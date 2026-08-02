package com.careerbridge.aicoach.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A deliberately partial local copy of student-service's StudentProfileResponse (22 fields) --
 * only what the coaching prompt actually interpolates. Safe because Jackson 3 disables
 * FAIL_ON_UNKNOWN_PROPERTIES by default. No careerPath/recommendedCareerPath field: verified by
 * exhaustive grep that no such field exists anywhere on the real response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDto {

    private Long userId;
    private String firstName;
    private String lastName;

    // Integer on the real response, not int -- an absent value must bind to null.
    private Integer profileCompletionPercentage;

    private List<SkillDto> skills;
}
