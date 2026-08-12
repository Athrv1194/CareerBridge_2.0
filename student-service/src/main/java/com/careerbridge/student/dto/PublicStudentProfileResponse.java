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

    private Integer profileCompletionPercentage;

    private Boolean hasAvatar;
}
