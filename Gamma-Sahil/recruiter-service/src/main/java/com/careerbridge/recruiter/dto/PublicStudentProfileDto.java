package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mirrors student-service's PublicStudentProfileResponse field-for-field. Safe if student-service
 * adds a field later -- Jackson 3 disables FAIL_ON_UNKNOWN_PROPERTIES by default (Boot 4 ships
 * tools.jackson) -- but a rename binds the renamed field to null here silently. Re-verify against
 * student-service/src/main/java/com/careerbridge/student/dto/PublicStudentProfileResponse.java
 * after any change there.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicStudentProfileDto {

    private Long studentId;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> skills;
    private Integer profileCompletionPercentage;
}
