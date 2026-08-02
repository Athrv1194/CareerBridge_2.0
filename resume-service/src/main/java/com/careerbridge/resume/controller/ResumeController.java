package com.careerbridge.resume.controller;

import com.careerbridge.resume.dto.ResumeDownload;
import com.careerbridge.resume.dto.ResumeResponse;
import com.careerbridge.resume.service.ResumeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity arrives entirely in headers injected by api-gateway after it validates the JWT; this
 * service never parses a token. The gateway strips any client-supplied copy of those headers,
 * which is what makes them trustworthy here.
 *
 * Consequence: port 8091 must not be publicly reachable. Anyone who can reach it directly can send
 * X-User-Role: SUPER_ADMIN and download every student's resume. That is a network control, not
 * something this code can enforce -- docker-compose publishes no host port for this service.
 *
 * No RBAC here: every authorization decision lives in ResumeService.
 *
 * Singular /api/resume, matching the other services and the gateway's route predicate. Gateway
 * predicates match whole segments, so a plural mapping would 404 every request arriving through
 * the gateway.
 */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/generate")
    public ResponseEntity<ResumeResponse> generateResume(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.generateResume(callerRole, studentId));
    }

    /**
     * /my is a literal segment competing with the /{id} capture below. Declaration order is
     * irrelevant -- Spring's PathPattern comparator prefers literals over captures -- but the
     * routing is pinned by a test rather than assumed, since getting it wrong would bind "my" to a
     * Long path variable and 400.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ResumeResponse>> getMyResumes(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(resumeService.getMyResumes(callerRole, studentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> getResumeById(
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.getResumeById(callerRole, callerId, id));
    }

    /**
     * Streams the stored bytes. Content-Disposition carries the stored fileName so the browser
     * saves resume_42_v2.pdf rather than the endpoint's last path segment.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadResume(
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        ResumeDownload download = resumeService.downloadResume(callerRole, callerId, id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.getFileName() + "\"")
                .body(download.getContent());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResume(
            @RequestHeader(USER_ID_HEADER) Long studentId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        resumeService.deleteResume(callerRole, studentId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<ResumeResponse>> getResumesByStudent(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long studentId) {
        return ResponseEntity.ok(resumeService.getResumesByStudentId(callerRole, studentId));
    }
}
