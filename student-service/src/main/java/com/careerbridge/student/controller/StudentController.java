package com.careerbridge.student.controller;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ProjectDto;
import com.careerbridge.student.dto.SkillDto;
import com.careerbridge.student.dto.StudentProfileRequest;
import com.careerbridge.student.dto.StudentProfileResponse;
import com.careerbridge.student.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * userId arrives in X-User-Id, forwarded by the API Gateway after it validates the JWT -- this
 * service never parses tokens itself.
 *
 * That makes the header a trusted input, so port 8082 must not be publicly reachable: anyone who
 * can hit it directly can set the header to any value and act as that student. This is a network
 * control (security group / bind address), not something the code can enforce.
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileResponse> getProfile(
            @RequestHeader(USER_ID_HEADER) Long userId) {
        return ResponseEntity.ok(studentService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<StudentProfileResponse> updateProfile(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody StudentProfileRequest request) {
        return ResponseEntity.ok(studentService.updateProfile(userId, request));
    }

    @PostMapping("/profile/education")
    public ResponseEntity<EducationDto> addEducation(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody EducationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addEducation(userId, dto));
    }

    @PostMapping("/profile/skills")
    public ResponseEntity<SkillDto> addSkill(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody SkillDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addSkill(userId, dto));
    }

    /** Catalogue for the skill autocomplete. No header: the list is identical for every student. */
    @GetMapping("/profile/skills/suggestions")
    public ResponseEntity<List<String>> getSkillSuggestions() {
        return ResponseEntity.ok(studentService.getSkillSuggestions());
    }

    @PostMapping("/profile/projects")
    public ResponseEntity<ProjectDto> addProject(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody ProjectDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addProject(userId, dto));
    }

    @PostMapping("/profile/certificates")
    public ResponseEntity<CertificateDto> addCertificate(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody CertificateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addCertificate(userId, dto));
    }
}
