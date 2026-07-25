package com.careerbridge.student.service;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ProjectDto;
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
}
