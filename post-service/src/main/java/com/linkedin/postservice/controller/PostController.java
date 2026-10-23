package com.linkedin.postservice.controller;

import com.linkedin.postservice.dto.PostResponse;
import com.linkedin.postservice.entity.Comment;
import com.linkedin.postservice.exception.ApiException;
import com.linkedin.postservice.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Identity always comes from X-User-Id (set by the gateway after JWT validation), never from params. */
@RestController
@RequestMapping("/api/v1/posts")
@Slf4j
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String content,
            @RequestParam(required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(userId, content, image));
    }

    /** Batch hydration for feed pages: GET /posts?ids=a,b,c (order preserved). */
    @GetMapping
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam List<String> ids) {
        if (ids.size() > PostService.MAX_BATCH) {
            throw ApiException.badRequest("At most " + PostService.MAX_BATCH + " ids per request");
        }
        return ResponseEntity.ok(postService.getPosts(ids, userId));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(postService.getPost(postId, userId));
    }

    @GetMapping("/user/{authorId}")
    public ResponseEntity<List<PostResponse>> getUserPosts(
            @PathVariable String authorId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(postService.getUserPosts(authorId, userId));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<String> likePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(postService.likePost(postId, userId));
    }

    @PostMapping(value = "/{postId}/comments", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Comment> addCommentForm(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String content) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.addComment(postId, userId, content));
    }

    @PostMapping(value = "/{postId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Comment> addCommentJson(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.addComment(postId, userId, body.get("content")));
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<List<Comment>> getComments(@PathVariable String postId) {
        return ResponseEntity.ok(postService.getComments(postId));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(
            @PathVariable String postId,
            @RequestHeader("X-User-Id") String userId) {
        postService.deletePost(postId, userId);
        return ResponseEntity.ok("Post deleted");
    }
}
