package com.careerbridge.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "questions",
        // Load-bearing for data.sql seeding: INSERT ... ON CONFLICT DO NOTHING requires this constraint.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_questions_category_order",
                columnNames = {"category_id", "order_index"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Flat FK column, not @ManyToOne -- avoids lazy-init traps and serialization cycles in DTO mapping.
    @Column(nullable = false)
    private Long categoryId;

    // TEXT not @Lob: PostgreSQLDialect maps @Lob String to oid, which rejects plain string inserts.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** Display order within the category. */
    private Integer orderIndex;

    // Soft-delete: admins retire rather than delete, so historical AttemptAnswer rows stay valid.
    // columnDefinition DEFAULT needed: data.sql doesn't name this column, so without a default every
    // seeded question would be NULL and invisible to student-facing queries.
    // nullable omitted intentionally -- columnDefinition carries "not null"; adding nullable=false
    // made Hibernate emit "boolean not null default true not null" (duplicated constraint).
    @Builder.Default
    @Column(name = "is_active", columnDefinition = "boolean not null default true")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // DEFAULT now() is required: @UpdateTimestamp forces NOT NULL in DDL; without DEFAULT the ALTER
    // fails against pre-existing rows. ddl-auto logs the failure as WARN and starts healthy --
    // making this the kind of thing that only breaks on a live request, not in tests.
    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
