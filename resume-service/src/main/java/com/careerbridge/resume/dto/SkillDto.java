package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors student-service's SkillDto. proficiencyLevel is a String here, not student-service's
 * ProficiencyLevel enum -- same reasoning as StudentRegisteredEvent.role: wire-identical, but a
 * duplicated enum would make Jackson hard-fail this deserialization the day the enum gains a value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDto {

    private String skillName;
    private String proficiencyLevel;
    private Boolean isCustom;
}
