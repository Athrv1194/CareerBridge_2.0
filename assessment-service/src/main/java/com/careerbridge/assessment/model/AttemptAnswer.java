package com.careerbridge.assessment.model;

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

@Entity
@Table(name = "attempt_answers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long attemptId;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Long selectedOptionId;

    /**
     * Copied from the selected Option row at submit time, never taken from the request payload.
     * Snapshotting it here keeps a past result reproducible if an option's weight is later re-tuned.
     */
    @Column(nullable = false)
    private Integer weightEarned;
}
