package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors student-service's StudentDepartmentResponse. studentId -> department for every student,
 * ignoring isPublic -- placement statistics count the whole cohort, not only the students who opted
 * into being visible to recruiters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDepartmentDto {

    private Long studentId;

    /** Null when the student has not been assigned to a department. */
    private String department;
}
