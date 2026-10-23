package com.linkedin.postservice.repository;

import com.linkedin.postservice.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LikeRepository extends JpaRepository<Like, String> {

    boolean existsByPostIdAndUserId(String postId, String userId);

    /** @return number of rows removed (0 = the user had not liked the post) */
    @Modifying(flushAutomatically = true)
    @Query("delete from Like l where l.postId = :postId and l.userId = :userId")
    int removeLike(@Param("postId") String postId, @Param("userId") String userId);

    @Query("select l.postId from Like l where l.userId = :userId and l.postId in :postIds")
    List<String> findLikedPostIds(@Param("userId") String userId, @Param("postIds") Collection<String> postIds);

    @Modifying
    @Query("delete from Like l where l.postId = :postId")
    void deleteByPost(@Param("postId") String postId);
}
