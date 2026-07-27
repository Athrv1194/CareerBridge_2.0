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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique because career matching does a case-insensitive substring match of this name against
     * CareerPath.requiredSkills -- two categories sharing a name would score identically and be
     * indistinguishable in the result.
     */
    @Column(nullable = false, unique = true)
    private String name;

    // TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to oid (large object), which
    // rejects a plain string literal -- exactly what broke data.sql's INSERT INTO categories.
    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
