package com.linkedin.postservice.dto;

import com.linkedin.postservice.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private String id;
    private String authorId;
    private String content;
    private String imageUrl;
    private int likeCount;
    private int commentCount;
    private boolean likedByMe;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostResponse from(Post p, boolean likedByMe) {
        return new PostResponse(p.getId(), p.getAuthorId(), p.getContent(), p.getImageUrl(),
                Math.max(0, p.getLikeCount()), Math.max(0, p.getCommentCount()), likedByMe,
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
