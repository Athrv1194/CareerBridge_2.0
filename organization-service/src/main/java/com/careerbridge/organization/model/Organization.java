package com.careerbridge.organization.model;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A tenant. Every college is one Organization; students, assessments and recommendations will
 * eventually carry its id for isolation.
 */
@Entity
@Table(name = "organizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to the oid large-object type,
    // which rejects a plain string from both the ORM insert path and any seed SQL. Logged SEV-2.
    @Column(columnDefinition = "TEXT", nullable = false)
    private String name;

    /** ENGINEERING_COLLEGE, POLYTECHNIC, UNIVERSITY. A String, not an enum, so adding a type is a
     *  data change rather than a deploy of this service plus every consumer of its events. */
    @Column(nullable = false)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String city;

    @Column(columnDefinition = "TEXT")
    private String state;

    /**
     * Soft delete. @Builder.Default is load-bearing: without it Lombok's builder writes null here
     * regardless of the field initialiser, and every findByIsActiveTrue query would then miss the
     * row entirely.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Excluded from toString and equals: @Data would otherwise recurse across the bidirectional
     * link (Organization -> departments -> organization -> ...) and blow the stack the first time
     * anything logged an entity. Excluding on this side alone is not enough; Department excludes
     * its back-reference too.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Department> departments = new ArrayList<>();
}
