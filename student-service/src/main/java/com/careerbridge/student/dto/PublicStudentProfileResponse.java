package com.careerbridge.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slim profile for cross-service candidate search (recruiter-service). Deliberately not
 * StudentProfileResponse: that carries 22 fields including bio, resumeUrl and four child
 * collections, which is both more than a recruiter needs and would cost 4 extra queries per
 * student here. studentId is StudentProfile.userId, renamed because the caller never sees the
 * internal profile row id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicStudentProfileResponse {

    private Long studentId;

    private String firstName;

    private String lastName;

    private String email;

    private List<String> skills;

    /**
     * Local copy of auth-service's department, kept current by the user.department.updated
     * consumer. Present so recruiter-service can display and filter on it without a third
     * synchronous call -- and it could not make that call anyway, since auth-service is the only
     * backend service with Spring Security and answers a header-only request 401.
     */
    private String department;

    private Integer profileCompletionPercentage;
}
