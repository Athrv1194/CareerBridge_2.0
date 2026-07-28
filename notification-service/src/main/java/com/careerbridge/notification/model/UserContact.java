package com.careerbridge.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * How this service knows where to send an email.
 *
 * RecommendationGeneratedEvent carries only a userId -- no address, no name -- so the contact
 * details are harvested from student.registered (which auth-service publishes with email,
 * firstName and lastName) and looked up when a recommendation arrives. That keeps the email path
 * self-contained: no synchronous HTTP call to another service while handling a message, and the
 * details survive a restart.
 *
 * Consequence worth knowing: a user who registered BEFORE this service first ran has no row here,
 * so their email is skipped. That is handled explicitly -- the in-app notification is still
 * created and the NotificationRecord is written as FAILED with the reason -- rather than silently
 * dropped.
 *
 * PostgreSQL rather than Mongo specifically for the unique constraint below: in Mongo,
 * @Indexed(unique = true) is a no-op unless spring.data.mongodb.auto-index-creation is turned on,
 * which would make a live Mongo a hard startup requirement for every developer and for CI.
 */
@Entity
@Table(name = "user_contacts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique, and the real idempotency guarantee for a redelivered student.registered event. The
     * consumer upserts by reading this row first; the constraint is what closes the TOCTOU window,
     * surfacing as a DataIntegrityViolationException that the fail-soft catch swallows.
     */
    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String email;

    private String firstName;

    private String lastName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Bumped whenever auth-service re-publishes, so a corrected address actually lands. */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
