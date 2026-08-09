package com.careerbridge.student.model;

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

import java.time.LocalDate;

/**
 * Work experience -- did not exist anywhere in this project until the Resume page design called
 * for it. Plain FK column, not a @ManyToOne, matching Education/Skill/Project/Certificate's
 * existing convention: sub-entities stay independently queryable and nothing cascades.
 *
 * Carries no weight in ProfileCompletionCalculator (that would need every existing student's
 * completion percentage re-derived and is a bigger decision than this page needs) -- same
 * deliberate omission as Certificate.
 */
@Entity
@Table(name = "experiences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentProfileId;

    @Column(nullable = false)
    private String title;

    private String company;

    private LocalDate startDate;

    /** Null when isCurrent is true. */
    private LocalDate endDate;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isCurrent = false;

    @Column(columnDefinition = "TEXT")
    private String description;
}
