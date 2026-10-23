package com.linkedin.postservice.service;

import com.linkedin.postservice.dto.PostResponse;
import com.linkedin.postservice.entity.Comment;
import com.linkedin.postservice.entity.Like;
import com.linkedin.postservice.entity.Post;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.repository.CommentRepository;
import com.linkedin.postservice.repository.LikeRepository;
import com.linkedin.postservice.repository.PostRepository;
import com.linkedin.postservice.telemetry.Telemetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    public static final int MAX_BATCH = 50;
    private static final int MAX_POST_LENGTH = 5000;
    private static final int MAX_COMMENT_LENGTH = 1000;

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final S3Service s3Service;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String POST_CREATED_TOPIC = "post.created";
    private static final String POST_LIKED_TOPIC = "post.liked";
    private static final String POST_COMMENTED_TOPIC = "post.commented";

    /** Create a post (optional image to S3) and publish post.created for feed + search. */
    @Transactional
    public PostResponse createPost(String authorId, String content, MultipartFile image) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Post content must not be empty");
        }
        if (text.length() > MAX_POST_LENGTH) {
            throw ApiException.badRequest("Post content is too long (max " + MAX_POST_LENGTH + " characters)");
        }

        Post post = new Post();
        post.setAuthorId(authorId);
        post.setContent(text);

        boolean hasImage = image != null && !image.isEmpty();
        if (hasImage) {
            post.setImageUrl(s3Service.uploadImage(image, "posts/" + authorId));
        }

        Post saved = postRepository.saveAndFlush(post);
        log.info("Post created: {}", saved.getId());

        Map<String, Object> event = new HashMap<>();
        event.put("postId", saved.getId());
        event.put("authorId", saved.getAuthorId());
        event.put("content", saved.getContent());
        event.put("imageUrl", saved.getImageUrl());
        event.put("createdAt", saved.getCreatedAt().toString());
        kafkaTemplate.send(POST_CREATED_TOPIC, saved.getId(), event);

        Telemetry.postCreated(hasImage);
        return PostResponse.from(saved, false);
    }

    @Transactional
    public PostResponse getPost(String postId, String viewerId) {
        Post post = find(postId);
        return PostResponse.from(post, likeRepository.existsByPostIdAndUserId(postId, viewerId));
    }

    /** Batch lookup used to hydrate a feed page: keeps the order of {@code ids}, skips unknown ids. */
    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(List<String> ids, String viewerId) {
        List<String> clean = ids.stream().filter(s -> s != null && !s.isBlank()).map(String::trim)
                .distinct().limit(MAX_BATCH).collect(Collectors.toList());
        if (clean.isEmpty()) {
            return List.of();
        }
        Map<String, Post> byId = postRepository.findAllById(clean).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Set<String> liked = new HashSet<>(likeRepository.findLikedPostIds(viewerId, clean));
        return clean.stream().map(byId::get).filter(Objects::nonNull)
                .map(p -> PostResponse.from(p, liked.contains(p.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostResponse> getUserPosts(String userId, String viewerId) {
        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<String> liked = new HashSet<>(likeRepository.findLikedPostIds(viewerId,
                posts.stream().map(Post::getId).collect(Collectors.toList())));
        return posts.stream().map(p -> PostResponse.from(p, liked.contains(p.getId())))
                .collect(Collectors.toList());
    }

    /** Toggle like. Counter updates are atomic SQL increments; (post,user) is unique in the schema. */
    @Transactional
    public String likePost(String postId, String userId) {
        Post post = find(postId);

        if (likeRepository.removeLike(postId, userId) > 0) {
            postRepository.addLikes(postId, -1);
            Telemetry.postLike(false);
            return "Post unliked";
        }

        Like like = new Like();
        like.setPostId(postId);
        like.setUserId(userId);
        likeRepository.save(like);
        postRepository.addLikes(postId, 1);

        Map<String, Object> event = new HashMap<>();
        event.put("postId", postId);
        event.put("userId", userId);
        event.put("authorId", post.getAuthorId());
        kafkaTemplate.send(POST_LIKED_TOPIC, postId, event);

        Telemetry.postLike(true);
        return "Post liked";
    }

    @Transactional
    public Comment addComment(String postId, String authorId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Comment must not be empty");
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw ApiException.badRequest("Comment is too long (max " + MAX_COMMENT_LENGTH + " characters)");
        }
        Post post = find(postId);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setContent(text);
        Comment saved = commentRepository.saveAndFlush(comment);
        postRepository.addComment(postId);

        Map<String, Object> event = new HashMap<>();
        event.put("postId", postId);
        event.put("authorId", authorId);
        event.put("commentId", saved.getId());
        event.put("postAuthorId", post.getAuthorId());
        kafkaTemplate.send(POST_COMMENTED_TOPIC, postId, event);

        Telemetry.commentAdded();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(String postId) {
        return commentRepository.findByPostIdOrderByCreatedAtDesc(postId);
    }

    @Transactional
    public void deletePost(String postId, String userId) {
        Post post = find(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw ApiException.forbidden("Not authorized to delete this post");
        }
        likeRepository.deleteByPost(postId);
        commentRepository.deleteByPost(postId);
        postRepository.delete(post);
        Telemetry.postDeleted();
        log.info("Post deleted: {}", postId);
    }

    private Post find(String postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Post not found: " + postId));
    }
}
