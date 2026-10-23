package com.linkedin.userservice.repository;

import com.linkedin.userservice.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    /** Callers must pass a non-empty collection (an empty IN () is invalid SQL). */
    List<User> findByIdNotIn(Collection<String> ids, Pageable pageable);
}
