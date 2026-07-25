package com.careerbridge.student.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentProfileId;

    @Column(nullable = false)
    private String skillName;

    // STRING, not the JPA default ordinal: reordering the enum would otherwise silently
    // remap every existing row to a different proficiency.
    @Enumerated(EnumType.STRING)
    private ProficiencyLevel proficiencyLevel;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isCustom = false;
}
