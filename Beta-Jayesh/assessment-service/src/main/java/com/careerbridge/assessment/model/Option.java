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

@Entity
@Table(
        name = "options",
        // Same reason as Question: this is what makes data.sql's INSERT IGNORE idempotent across
        // restarts instead of re-inserting every option row.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_options_question_order",
                columnNames = {"question_id", "order_index"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private String optionText;

    /**
     * Scoring weight, 0..AssessmentConstants.MAX_OPTION_WEIGHT.
     *
     * NEVER serialised to a client -- OptionDto has no weight field, and submitAttempt reads the
     * weight from this row rather than from the request payload. Both are deliberate: a client that
     * can see or send weights can pick the highest-scoring answer, or fabricate its own score.
     *
     * The 0..3 range is not enforced here because no API writes options; they are seeded by SQL.
     * A row above the ceiling silently yields a score over 100%.
     */
    @Column(nullable = false)
    private Integer weight;

    private Integer orderIndex;
}
