package com.linkedin.postservice.repository;

import com.linkedin.postservice.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    List<Comment> findByPostIdOrderByCreatedAtDesc(String postId);

    @Modifying
    @Query("delete from Comment c where c.postId = :postId")
    void deleteByPost(@Param("postId") String postId);
}
