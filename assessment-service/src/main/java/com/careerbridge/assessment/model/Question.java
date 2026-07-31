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
    // nullable is NOT set here: columnDefinition already carries "not null", and adding
    // nullable = false made Hibernate emit "boolean not null default true not null" -- accepted by
    // PostgreSQL, but duplicated.
    @Column(name = "is_active", columnDefinition = "boolean not null default true")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * The DEFAULT in columnDefinition is REQUIRED and must not be simplified away.
     *
     * Hibernate marks an @UpdateTimestamp column NOT NULL in generated DDL and there is no way to
     * talk it out of that: @Column(nullable = true) does not override it, and neither does a bare
     * columnDefinition -- Hibernate appends its own "not null" after whatever is written here.
     * Both were tried against a live database; the generator wins each time.
     *
     * So `alter table questions add column updated_at timestamp(6) not null` is what gets emitted,
     * and that cannot apply to a table already holding rows -- PostgreSQL rejects it with
     * "contains null values". A DEFAULT is the one thing that makes the statement legal, because
     * PostgreSQL then backfills existing rows as part of the same ALTER. Same technique as
     * is_active directly above.
     *
     * What made this dangerous rather than merely broken: ddl-auto logs the failed ALTER as a WARN
     * and carries on, so the service starts HEALTHY with the column simply missing, and every query
     * mapping Question then fails at runtime with "column q1_0.updated_at does not exist" --
     * including the student-facing endpoints, not just the admin surface. Nothing in the test suite
     * catches it: unit tests mock the repository, and contextLoads never queries questions. It
     * surfaced only on a live request.
     *
     * Consequence to know: the 22 seeded questions carry the migration timestamp rather than null,
     * so updated_at reads as "last known change" rather than "an admin edited this". That matches
     * the common convention of seeding updated_at alongside created_at.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
