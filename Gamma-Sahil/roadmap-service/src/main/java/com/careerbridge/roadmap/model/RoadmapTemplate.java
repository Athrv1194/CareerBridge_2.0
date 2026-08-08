package com.careerbridge.roadmap.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The blueprint for one career's learning path. Seeded once by RoadmapDataSeeder, then copied into a
 * StudentRoadmap every time a student's recommendation names this career.
 *
 * careerName must match a name in recommendation-service's constants/CareerCatalog.java EXACTLY.
 * The lookup in RoadmapServiceImpl is case-insensitive but not fuzzy, so a renamed career there
 * silently means every affected student gets no roadmap at all -- the consumer logs a warning and
 * returns. Both files must be edited together.
 */
@Entity
@Table(name = "career_roadmap_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to the oid large-object type,
    // which rejects a plain string from both the ORM insert path and any seed SQL. Logged SEV-2.
    @Column(columnDefinition = "TEXT", nullable = false)
    private String careerName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String templateTitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer totalMilestones;

    @Column(nullable = false)
    private Integer estimatedWeeks;

    /**
     * Soft delete. @Builder.Default is load-bearing: without it Lombok's builder writes null here
     * regardless of the field initialiser, and findByCareerNameIgnoreCaseAndIsActiveTrue would then
     * miss every seeded template.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Excluded from toString and equals: @Data would otherwise recurse across the bidirectional link
     * (RoadmapTemplate -> milestoneTemplates -> roadmapTemplate -> ...) and blow the stack the first
     * time anything logged an entity. Excluding on this side alone is not enough; MilestoneTemplate
     * excludes its back-reference too.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "roadmapTemplate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MilestoneTemplate> milestoneTemplates = new ArrayList<>();
}
