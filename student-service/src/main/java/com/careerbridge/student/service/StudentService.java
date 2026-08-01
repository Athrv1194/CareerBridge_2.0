package com.careerbridge.student.service;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ProjectDto;
import com.careerbridge.student.dto.PublicStudentProfileResponse;
import com.careerbridge.student.dto.SkillDto;
import com.careerbridge.student.dto.StudentProfileRequest;
import com.careerbridge.student.dto.StudentProfileResponse;

import java.util.List;

public interface StudentService {

    StudentProfileResponse getProfile(Long userId);

    StudentProfileResponse updateProfile(Long userId, StudentProfileRequest request);

    EducationDto addEducation(Long userId, EducationDto dto);

    SkillDto addSkill(Long userId, SkillDto dto);

    ProjectDto addProject(Long userId, ProjectDto dto);

    CertificateDto addCertificate(Long userId, CertificateDto dto);

    List<String> getSkillSuggestions();

    /**
     * Candidate search source for recruiter-service. Restricted to RECRUITER, PLACEMENT_OFFICER,
     * ORG_ADMIN and SUPER_ADMIN -- a STUDENT must not be able to enumerate their peers.
     */
    List<PublicStudentProfileResponse> getPublicProfiles(String callerRole);
}
