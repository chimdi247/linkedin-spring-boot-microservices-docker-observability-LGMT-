package com.linkedin.postservice.service;

import com.linkedin.postservice.dto.PostResponse;
import com.linkedin.postservice.entity.Comment;
import com.linkedin.postservice.entity.Like;
import com.linkedin.postservice.entity.Post;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.repository.CommentRepository;
import com.linkedin.postservice.repository.LikeRepository;
import com.linkedin.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock LikeRepository likeRepository;
    @Mock CommentRepository commentRepository;
    @Mock S3Service s3Service;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(postRepository, likeRepository, commentRepository, s3Service, kafkaTemplate);
        when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setId("p1");
            p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            return p;
        });
    }

    private static Post post(String id, String author) {
        Post p = new Post();
        p.setId(id);
        p.setAuthorId(author);
        p.setContent("c-" + id);
        return p;
    }

    private static void status(Runnable r, HttpStatus expected) {
        assertEquals(expected, assertThrows(ApiException.class, r::run).getStatus());
    }

    // ------------------------------------------------------------ create
    @Test
    void blankContentIs400() {
        status(() -> service.createPost("a", "   ", null), HttpStatus.BAD_REQUEST);
        status(() -> service.createPost("a", null, null), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(postRepository);
    }

    @Test
    void overlongContentIs400() {
        status(() -> service.createPost("a", "x".repeat(5001), null), HttpStatus.BAD_REQUEST);
        assertDoesNotThrow(() -> service.createPost("a", "x".repeat(5000), null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTrimsContentTakesAuthorFromArgumentAndPublishesEvent() {
        PostResponse r = service.createPost("author-1", "  hello  ", null);
        assertEquals("hello", r.getContent());
        assertEquals("author-1", r.getAuthorId());
        assertFalse(r.isLikedByMe());
        assertNull(r.getImageUrl());
        verifyNoInteractions(s3Service);

        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("post.created"), eq("p1"), ev.capture());
        Map<String, Object> m = (Map<String, Object>) ev.getValue();
        assertEquals("author-1", m.get("authorId"));
        assertEquals("hello", m.get("content"));
    }

    @Test
    void createWithImageUploadsToTheAuthorsPrefix() {
        MockMultipartFile img = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3});
        when(s3Service.uploadImage(img, "posts/author-1")).thenReturn("http://media/posts/author-1/a.png");
        PostResponse r = service.createPost("author-1", "pic", img);
        assertEquals("http://media/posts/author-1/a.png", r.getImageUrl());
    }

    // -------------------------------------------------------------- like
    @Test
    void firstLikeInsertsRowIncrementsAtomicallyAndPublishes() {
        Post p = post("p1", "author");
        when(postRepository.findById("p1")).thenReturn(Optional.of(p));
        when(likeRepository.removeLike("p1", "u1")).thenReturn(0);

        assertEquals("Post liked", service.likePost("p1", "u1"));

        verify(likeRepository).save(any(Like.class));
        verify(postRepository).addLikes("p1", 1);
        verify(postRepository, never()).save(any());                       // no read-modify-write of the entity
        verify(kafkaTemplate).send(eq("post.liked"), eq("p1"), any());
    }

    @Test
    void secondLikeTogglesOffWithoutEvent() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(post("p1", "author")));
        when(likeRepository.removeLike("p1", "u1")).thenReturn(1);

        assertEquals("Post unliked", service.likePost("p1", "u1"));

        verify(postRepository).addLikes("p1", -1);
        verify(likeRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void likingUnknownPostIs404() {
        when(postRepository.findById("nope")).thenReturn(Optional.empty());
        status(() -> service.likePost("nope", "u1"), HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------- comment
    @Test
    void blankOrOverlongCommentIs400() {
        status(() -> service.addComment("p1", "u1", " "), HttpStatus.BAD_REQUEST);
        status(() -> service.addComment("p1", "u1", "x".repeat(1001)), HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void commentIsSavedCounterIncrementedAtomicallyAndEventCarriesPostAuthor() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(post("p1", "post-author")));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(i -> {
            Comment c = i.getArgument(0);
            c.setId("c1");
            return c;
        });

        Comment saved = service.addComment("p1", "commenter", "  nice  ");

        assertEquals("nice", saved.getContent());
        assertEquals("commenter", saved.getAuthorId());
        verify(postRepository).addComment("p1");
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("post.commented"), eq("p1"), ev.capture());
        assertEquals("post-author", ((Map<String, Object>) ev.getValue()).get("postAuthorId"));
    }

    // ------------------------------------------------------------ delete
    @Test
    void nonAuthorCannotDelete403() {
        when(postRepository.findById("p1")).thenReturn(Optional.of(post("p1", "author")));
        status(() -> service.deletePost("p1", "intruder"), HttpStatus.FORBIDDEN);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void authorDeletesPostWithItsLikesAndComments() {
        Post p = post("p1", "author");
        when(postRepository.findById("p1")).thenReturn(Optional.of(p));
        service.deletePost("p1", "author");
        verify(likeRepository).deleteByPost("p1");
        verify(commentRepository).deleteByPost("p1");
        verify(postRepository).delete(p);
    }

    // ------------------------------------------------------------- batch
    @Test
    void batchKeepsRequestedOrderSkipsUnknownAndMarksLikedByMe() {
        when(postRepository.findAllById(any())).thenReturn(List.of(post("a", "x"), post("b", "x"), post("c", "x")));
        when(likeRepository.findLikedPostIds(eq("viewer"), any())).thenReturn(List.of("c"));

        List<PostResponse> out = service.getPosts(List.of("c", "missing", "a", "b"), "viewer");

        assertEquals(List.of("c", "a", "b"), out.stream().map(PostResponse::getId).collect(Collectors.toList()));
        assertTrue(out.get(0).isLikedByMe());
        assertFalse(out.get(1).isLikedByMe());
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchIsCappedAtFiftyDistinctNonBlankIds() {
        when(postRepository.findAllById(any())).thenReturn(List.of());
        List<String> ids = new ArrayList<>(IntStream.range(0, 80).mapToObj(i -> "id" + i).collect(Collectors.toList()));
        ids.add(0, " ");
        ids.add("id1");
        service.getPosts(ids, "v");
        ArgumentCaptor<Iterable<String>> cap = ArgumentCaptor.forClass(Iterable.class);
        verify(postRepository).findAllById(cap.capture());
        List<String> sent = new ArrayList<>();
        cap.getValue().forEach(sent::add);
        assertEquals(PostService.MAX_BATCH, sent.size());
        assertEquals("id0", sent.get(0));
        assertEquals(sent.size(), new HashSet<>(sent).size());
    }

    @Test
    void emptyBatchDoesNotTouchTheDatabase() {
        assertTrue(service.getPosts(List.of(" ", ""), "v").isEmpty());
        verifyNoInteractions(postRepository, likeRepository);
    }

    @Test
    void responseNeverExposesNegativeCounters() {
        Post p = post("p", "a");
        p.setLikeCount(-3);
        p.setCommentCount(-1);
        PostResponse r = PostResponse.from(p, false);
        assertEquals(0, r.getLikeCount());
        assertEquals(0, r.getCommentCount());
    }
}
