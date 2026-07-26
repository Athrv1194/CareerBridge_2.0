package com.careerbridge.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique, and the real double-submit backstop: the status check in submitAttempt is a
     * best-effort guard with a TOCTOU window, this constraint is what the database enforces.
     */
    @Column(nullable = false, unique = true)
    private Long attemptId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    /** Sum of weightEarned across the submitted answers. */
    @Column(nullable = false)
    private Integer rawScore;

    /** Question count of the whole category x MAX_OPTION_WEIGHT -- a server-side fact, so a
     *  partial submission scores proportionally lower rather than inflating to 100%. */
    @Column(nullable = false)
    private Integer maxPossibleScore;

    @Column(nullable = false)
    private Double categoryScorePercentage;

    /** Null when no career paths are seeded; the result is still recorded. */
    private Long topCareerPathId;

    private Double careerMatchPercentage;

    /**
     * The TOP_CAREERS_TO_RECOMMEND highest-scoring careers as JSON, not literally every career --
     * the field name over-promises. Serialised with Jackson 3 (tools.jackson), which is the only
     * Jackson on this service's classpath.
     */
    @Lob
    private String allCareerScoresJson;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime calculatedAt;
}
