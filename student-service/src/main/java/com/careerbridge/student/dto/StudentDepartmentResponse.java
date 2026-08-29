package com.careerbridge.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * studentId -> department, and nothing else. The narrowest payload that answers "which department
 * is this student in", for recruiter-service's department-level placement stats.
 *
 * Deliberately NOT PublicStudentProfileResponse, for two independent reasons:
 *
 *  - That DTO filters isPublic=true. Placement statistics must count every student in the college,
 *    not only those who opted into being visible to recruiters, or a student toggling their privacy
 *    switch would silently vanish from their department's placement numbers with nothing logged.
 *  - It carries name and email. Aggregating headcounts needs neither, so this carries neither.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDepartmentResponse {

    private Long studentId;

    /** Null when the student has not been assigned to a department. */
    private String department;
}
