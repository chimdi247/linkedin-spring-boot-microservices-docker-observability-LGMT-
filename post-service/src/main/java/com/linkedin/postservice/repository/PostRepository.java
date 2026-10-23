package com.linkedin.postservice.repository;

import com.linkedin.postservice.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, String> {

    List<Post> findByAuthorIdOrderByCreatedAtDesc(String userId);

    /** Atomic counter updates - a read-modify-write on the entity loses updates under concurrency. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    int addLikes(@Param("id") String id, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + 1 where p.id = :id")
    int addComment(@Param("id") String id);
}
