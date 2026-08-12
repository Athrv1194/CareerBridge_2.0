package com.careerbridge.student.controller;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ExperienceDto;
import com.careerbridge.student.dto.ImageBlob;
import com.careerbridge.student.dto.ProjectDto;
import com.careerbridge.student.dto.PublicStudentProfileResponse;
import com.careerbridge.student.dto.SkillDto;
import com.careerbridge.student.dto.StudentProfileRequest;
import com.careerbridge.student.dto.StudentProfileResponse;
import com.careerbridge.student.exception.CustomException;
import com.careerbridge.student.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private static final String USER_ROLE_HEADER = "X-User-Role";

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

    @PutMapping("/profile/education/{educationId}")
    public ResponseEntity<EducationDto> updateEducation(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long educationId,
            @Valid @RequestBody EducationDto dto) {
        return ResponseEntity.ok(studentService.updateEducation(userId, educationId, dto));
    }

    @DeleteMapping("/profile/education/{educationId}")
    public ResponseEntity<Void> deleteEducation(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long educationId) {
        studentService.deleteEducation(userId, educationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/skills")
    public ResponseEntity<SkillDto> addSkill(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody SkillDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addSkill(userId, dto));
    }

    @DeleteMapping("/profile/skills/{skillId}")
    public ResponseEntity<Void> deleteSkill(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long skillId) {
        studentService.deleteSkill(userId, skillId);
        return ResponseEntity.noContent().build();
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

    @PutMapping("/profile/projects/{projectId}")
    public ResponseEntity<ProjectDto> updateProject(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDto dto) {
        return ResponseEntity.ok(studentService.updateProject(userId, projectId, dto));
    }

    @DeleteMapping("/profile/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long projectId) {
        studentService.deleteProject(userId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/profile/projects/{projectId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadProjectCover(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        studentService.uploadProjectCover(userId, projectId, readImageBytes(file), resolveImageContentType(file));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile/projects/{projectId}/cover")
    public ResponseEntity<byte[]> getProjectCover(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long projectId) {
        ImageBlob blob = studentService.getProjectCover(userId, projectId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(blob.getBytes());
    }

    @DeleteMapping("/profile/projects/{projectId}/cover")
    public ResponseEntity<Void> deleteProjectCover(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long projectId) {
        studentService.deleteProjectCover(userId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/certificates")
    public ResponseEntity<CertificateDto> addCertificate(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody CertificateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addCertificate(userId, dto));
    }

    @PutMapping("/profile/certificates/{certificateId}")
    public ResponseEntity<CertificateDto> updateCertificate(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long certificateId,
            @Valid @RequestBody CertificateDto dto) {
        return ResponseEntity.ok(studentService.updateCertificate(userId, certificateId, dto));
    }

    @DeleteMapping("/profile/certificates/{certificateId}")
    public ResponseEntity<Void> deleteCertificate(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long certificateId) {
        studentService.deleteCertificate(userId, certificateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/profile/certificates/{certificateId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadCertificateFile(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long certificateId,
            @RequestParam("file") MultipartFile file) {
        studentService.uploadCertificateFile(userId, certificateId,
                readCertificateFileBytes(file), resolveCertificateFileContentType(file), file.getOriginalFilename());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile/certificates/{certificateId}/file")
    public ResponseEntity<byte[]> getCertificateFile(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long certificateId) {
        ImageBlob blob = studentService.getCertificateFile(userId, certificateId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + blob.getFileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(blob.getBytes());
    }

    @DeleteMapping("/profile/certificates/{certificateId}/file")
    public ResponseEntity<Void> deleteCertificateFile(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long certificateId) {
        studentService.deleteCertificateFile(userId, certificateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/profile/experience")
    public ResponseEntity<ExperienceDto> addExperience(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody ExperienceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.addExperience(userId, dto));
    }

    @PutMapping("/profile/experience/{experienceId}")
    public ResponseEntity<ExperienceDto> updateExperience(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long experienceId,
            @Valid @RequestBody ExperienceDto dto) {
        return ResponseEntity.ok(studentService.updateExperience(userId, experienceId, dto));
    }

    @DeleteMapping("/profile/experience/{experienceId}")
    public ResponseEntity<Void> deleteExperience(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long experienceId) {
        studentService.deleteExperience(userId, experienceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadAvatar(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @RequestParam("file") MultipartFile file) {
        studentService.uploadAvatar(userId, readImageBytes(file), resolveImageContentType(file));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile/avatar")
    public ResponseEntity<byte[]> getAvatar(@RequestHeader(USER_ID_HEADER) Long userId) {
        ImageBlob blob = studentService.getAvatar(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(blob.getBytes());
    }

    @DeleteMapping("/profile/avatar")
    public ResponseEntity<Void> deleteAvatar(@RequestHeader(USER_ID_HEADER) Long userId) {
        studentService.deleteAvatar(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Candidate search source for recruiter-service, called directly on the compose network (not
     * through the gateway with the caller's own token). RBAC lives in the service, not here --
     * this only forwards the caller's role.
     */
    @GetMapping("/profiles/public")
    public ResponseEntity<List<PublicStudentProfileResponse>> getPublicProfiles(
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(studentService.getPublicProfiles(callerRole));
    }

    /**
     * Called through the gateway by a recruiter/admin's own browser. Same RBAC as
     * getPublicProfiles, one candidate at a time -- deliberately not folded into that list
     * endpoint, which stays slim on purpose (see PublicStudentProfileResponse's own comment).
     */
    @GetMapping("/profile/{studentId}")
    public ResponseEntity<StudentProfileResponse> getProfileForRecruiter(
            @PathVariable Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(studentService.getProfileForRecruiter(studentId, callerRole));
    }

    /**
     * Called through the gateway by a recruiter/admin's own browser -- the caller's real
     * X-User-Role, not forwarded from another service. Same RBAC as getPublicProfiles.
     */
    @GetMapping("/profile/{studentId}/avatar")
    public ResponseEntity<byte[]> getAvatarForRecruiter(
            @PathVariable Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        ImageBlob blob = studentService.getAvatarForRecruiter(studentId, callerRole);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(blob.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(blob.getBytes());
    }

    /** Shared by the avatar and project-cover upload endpoints. */
    private byte[] readImageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("No file was uploaded", HttpStatus.BAD_REQUEST);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException("Could not read the uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveImageContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException("Only image uploads are accepted", HttpStatus.BAD_REQUEST);
        }
        return contentType;
    }

    /** Certificates/offer letters are legitimately PDFs, not just images -- a separate check from avatar/cover. */
    private byte[] readCertificateFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException("No file was uploaded", HttpStatus.BAD_REQUEST);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException("Could not read the uploaded file", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveCertificateFileContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals(MediaType.APPLICATION_PDF_VALUE) || contentType.startsWith("image/"))) {
            throw new CustomException("Only PDF or image uploads are accepted", HttpStatus.BAD_REQUEST);
        }
        return contentType;
    }
}
