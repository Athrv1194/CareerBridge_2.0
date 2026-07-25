package com.careerbridge.student.dto;

import com.careerbridge.student.model.ProficiencyLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serves as both request body (POST .../skills) and response element.
 *
 * The "skillName must be in PREDEFINED_SKILLS unless isCustom" rule is NOT expressed here as a
 * custom ConstraintValidator: it is a cross-field rule that also needs the predefined list, which
 * the service already holds for getSkillSuggestions. Enforced in StudentServiceImpl.addSkill,
 * which returns the same HTTP 400 shape a bean-validation failure would.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDto {

    private Long id;

    @NotBlank(message = "Skill name is required")
    private String skillName;

    private ProficiencyLevel proficiencyLevel;

    @Builder.Default
    private Boolean isCustom = false;
}
