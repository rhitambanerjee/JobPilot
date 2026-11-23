package com.rhitam.jobtracker.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u.sessionVersion from User u where u.id = :id")
    Optional<Integer> findSessionVersion(@Param("id") UUID id);
}
