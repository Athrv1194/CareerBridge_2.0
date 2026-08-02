package com.careerbridge.aicoach.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Keyed on (careerName, milestoneTitle), NOT (studentId, milestoneTitle). Milestone titles come
 * from 47 fixed seeded templates in roadmap-service, and both external search queries
 * (Tavily/YouTube) contain zero student-specific data -- so resources are provably identical for
 * every student on the same career. 47 documents total, forever, regardless of student count.
 *
 * A document with an empty `resources` list must never be persisted -- see CatalogRefresher. That
 * would make existsByCareerNameAndMilestoneTitle return true forever and permanently un-enrich
 * that milestone. There is deliberately no @Indexed(unique=true) here: MongoMappingContext's
 * autoIndexCreation defaults to false project-wide (see notification-service's own note), and
 * enabling it would issue createIndex during context refresh, forfeiting the no-Mongo-required
 * contextLoads property this service is built around. Create the index by hand in Atlas if this
 * ever runs with multiple replicas.
 */
@Document(collection = "milestone_resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneResourceDocument {

    @Id
    private String id;

    private String careerName;
    private String milestoneTitle;

    @Builder.Default
    private List<ResourceItem> resources = new ArrayList<>();

    private LocalDateTime fetchedAt;
}
