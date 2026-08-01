package com.careerbridge.roadmap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One step of a RoadmapTemplate. Copied verbatim into a StudentMilestone when a roadmap is
 * generated, so editing a template never rewrites a roadmap a student has already started.
 */
@Entity
@Table(name = "milestone_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1-based position within the template. Not a reserved word in PostgreSQL, so no quoting. */
    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false)
    private Integer estimatedDays;

    @Column(columnDefinition = "TEXT")
    private String resourceUrl;

    /** Excluded from toString/equals to break the @Data recursion; see RoadmapTemplate. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_template_id", nullable = false)
    private RoadmapTemplate roadmapTemplate;
}
