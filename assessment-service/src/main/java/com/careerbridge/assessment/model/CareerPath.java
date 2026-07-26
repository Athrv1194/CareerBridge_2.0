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
@Table(name = "career_paths")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique, and load-bearing: career match scores are keyed by name, so topCareerPathId is
     * resolved by looking this name back up. Duplicates would make that lookup ambiguous.
     */
    @Column(nullable = false, unique = true)
    private String name;

    @Lob
    private String description;

    /** Comma-separated skills. Career matching substring-matches the category name against this. */
    @Column(length = 1000)
    private String requiredSkills;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
