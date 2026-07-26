package com.careerbridge.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @Lob
    @Column(nullable = false)
    private String questionText;

    /** Display order within the category; the repository sorts on it. */
    private Integer orderIndex;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
