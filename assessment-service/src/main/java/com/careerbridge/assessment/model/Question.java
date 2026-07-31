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
        // Load-bearing for seeding, not just hygiene: data.sql re-runs on every startup and relies
        // on INSERT IGNORE, which only suppresses UNIQUE violations. Without this constraint each
        // restart re-inserts the whole question bank, and the options subqueries that look a
        // question up by text then fail with "Subquery returns more than 1 row".
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

    // Plain FK column, not a @ManyToOne: keeps the question bank loadable as flat selects with no
    // lazy-init traps and no serialization cycles when questions are mapped to DTOs.
    @Column(nullable = false)
    private Long categoryId;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to oid (large object), which
    // rejects a plain string literal -- exactly what broke data.sql's INSERT INTO questions.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** Display order within the category; the repository sorts on it. */
    private Integer orderIndex;

    /**
     * ADMIN MODULE: soft-delete for the question bank. Admins retire a question rather than deleting
     * it, so historical AttemptAnswer rows keep pointing at something real.
     *
     * Every student-facing query filters on this -- including countByCategoryId, which backs the
     * "category has at least MIN_QUESTIONS_PER_CATEGORY questions" guard. Filtering the question
     * pool without also filtering that count would let a category pass the guard on 6 questions,
     * serve a pool of 3 active ones, and score the student against the fixed maxPossibleScore of 15
     * -- capping them at 60% with no error anywhere.
     *
     * columnDefinition carries the default deliberately, and it replaces a data-migration runner:
     *   - ddl-auto emits ALTER TABLE questions ADD COLUMN is_active boolean not null default true,
     *     and PostgreSQL backfills every pre-existing row as part of that one statement;
     *   - data.sql re-runs on every startup and its INSERT names only
     *     (category_id, question_text, order_index, created_at), so without a column default every
     *     seeded question would be NULL on a fresh volume and invisible to the student queries.
     * @Builder.Default covers the third path -- Lombok's builder, used by addQuestion -- which would
     * otherwise write null straight past the field initialiser.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false, columnDefinition = "boolean not null default true")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Null for every seeded question until an admin first edits it: @UpdateTimestamp fires only on
     * Hibernate's own update path, and data.sql bypasses Hibernate entirely. That is the honest
     * value -- the row genuinely has never been updated.
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
