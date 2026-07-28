package com.careerbridge.notification.repository;

import com.careerbridge.notification.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {

    /**
     * Safe as an Optional: user_id carries a unique constraint, so at most one row.
     *
     * Serves both paths -- the upsert on student.registered reads it to decide insert-vs-update,
     * and the recommendation path reads it to find the recipient address. An empty result on the
     * second path is an expected state (a student who registered before this service existed),
     * not an error.
     */
    Optional<UserContact> findByUserId(Long userId);
}
