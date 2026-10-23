package com.linkedin.userservice.repository;

import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConnectionRepository extends JpaRepository<Connection, String> {

    boolean existsByRequesterIdAndReceiverId(String requesterId, String receiverId);
    List<Connection> findByRequesterIdAndStatus(String requesterId, ConnectionStatus status);
    List<Connection> findByReceiverIdAndStatus(String receiverId, ConnectionStatus status);

    /** Every connection row between two users, in either direction. */
    @Query("select c from Connection c where (c.requesterId = :a and c.receiverId = :b) "
            + "or (c.requesterId = :b and c.receiverId = :a)")
    List<Connection> findBetween(@Param("a") String a, @Param("b") String b);

    /** Accepted connections of a user, regardless of who sent the original request. */
    @Query("select c from Connection c where c.status = com.linkedin.userservice.entity.ConnectionStatus.CONNECTED "
            + "and (c.requesterId = :userId or c.receiverId = :userId)")
    List<Connection> findAcceptedFor(@Param("userId") String userId);

    /** All rows a user takes part in (used to exclude people from suggestions). */
    @Query("select c from Connection c where c.requesterId = :userId or c.receiverId = :userId")
    List<Connection> findAllInvolving(@Param("userId") String userId);
}
