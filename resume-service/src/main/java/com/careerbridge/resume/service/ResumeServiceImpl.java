package com.careerbridge.resume.service;

import com.careerbridge.resume.constants.ResumeRoles;
import com.careerbridge.resume.dto.AtsResult;
import com.careerbridge.resume.dto.ResumeBuildOptions;
import com.careerbridge.resume.dto.ResumeDownload;
import com.careerbridge.resume.dto.ResumeGenerateRequest;
import com.careerbridge.resume.dto.ResumeResponse;
import com.careerbridge.resume.dto.StudentProfileDto;
import com.careerbridge.resume.exception.CustomException;
import com.careerbridge.resume.messaging.ResumeEventPublisher;
import com.careerbridge.resume.model.ResumeSummary;
import com.careerbridge.resume.model.StudentResume;
import com.careerbridge.resume.pdf.ResumePdfBuilder;
import com.careerbridge.resume.repository.StudentResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * RBAC lives here, never in the controller or the gateway -- the gateway validates the JWT and
 * forwards identity, but knows nothing about which student owns which resume.
 */
@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);

    /** Who may read a resume that is not their own. A STUDENT is not in this set. */
    private static final Set<String> RESUME_VIEWER_ROLES = Set.of(
            ResumeRoles.RECRUITER, ResumeRoles.PLACEMENT_OFFICER,
            ResumeRoles.ORG_ADMIN, ResumeRoles.SUPER_ADMIN);

    private static final String DOWNLOAD_PATH_PREFIX = "/api/resume/download/";

    private final StudentResumeRepository resumeRepository;
    private final StudentServiceClient studentServiceClient;
    private final AtsScoreCalculator atsScoreCalculator;
    private final ResumePdfBuilder resumePdfBuilder;
    private final ResumeEventPublisher eventPublisher;

    public ResumeServiceImpl(StudentResumeRepository resumeRepository,
                             StudentServiceClient studentServiceClient,
                             AtsScoreCalculator atsScoreCalculator,
                             ResumePdfBuilder resumePdfBuilder,
                             ResumeEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.studentServiceClient = studentServiceClient;
        this.atsScoreCalculator = atsScoreCalculator;
        this.resumePdfBuilder = resumePdfBuilder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ResumeResponse generateResume(String callerRole, Long studentId, ResumeGenerateRequest rawRequest) {
        requireStudent(callerRole, "Only a STUDENT may generate a resume");

        // A missing body is treated identically to an all-omitted one: every include* toggle
        // defaults to true (see ResumeGenerateRequest.orDefaultTrue), which is this endpoint's
        // original, pre-toggle behaviour -- an old caller sending no body still gets everything.
        ResumeGenerateRequest request = rawRequest != null ? rawRequest : ResumeGenerateRequest.builder().build();

        StudentProfileDto profile = studentServiceClient.fetchMyProfile(studentId);
        if (profile == null) {
            // 503, not 500: the fault is a downstream dependency being unreachable, and the caller
            // should retry rather than treat this as a bug in resume generation.
            throw new CustomException(
                    "Unable to fetch your profile right now - please try again in a moment",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        boolean tailored = StringUtils.hasText(request.getJobDescription());
        AtsResult atsResult = tailored
                ? atsScoreCalculator.calculateTailored(profile, request.getJobDescription())
                : atsScoreCalculator.calculateDetailed(profile);

        int version = resumeRepository.findTopByStudentIdOrderByVersionDesc(studentId)
                .map(latest -> latest.getVersion() + 1)
                .orElse(1);

        ResumeBuildOptions options = ResumeBuildOptions.builder()
                .summary(request.getSummary())
                .includePhone(request.orDefaultTrue(request.getIncludePhone()))
                .includeEmail(request.orDefaultTrue(request.getIncludeEmail()))
                .includeLinks(request.orDefaultTrue(request.getIncludeLinks()))
                .includeLocation(request.orDefaultTrue(request.getIncludeLocation()))
                .includeExperience(request.orDefaultTrue(request.getIncludeExperience()))
                .includeSkills(request.orDefaultTrue(request.getIncludeSkills()))
                .includeProjects(request.orDefaultTrue(request.getIncludeProjects()))
                .includeEducation(request.orDefaultTrue(request.getIncludeEducation()))
                .includeCertificates(request.orDefaultTrue(request.getIncludeCertificates()))
                .build();

        byte[] pdfContent;
        try {
            pdfContent = resumePdfBuilder.build(profile, options);
        } catch (IOException ex) {
            log.error("PDF generation failed for studentId={}", studentId, ex);
            throw new CustomException("Resume generation failed - please try again",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Exactly one resume is the default at a time, and it is always the newest. Done before the
        // insert so there is never a window with two defaults.
        clearDefaults(studentId);

        StudentResume saved = resumeRepository.save(StudentResume.builder()
                .studentId(studentId)
                .fileName("resume_" + studentId + "_v" + version + ".pdf")
                .version(version)
                .atsScore(atsResult.getScore())
                .isDefault(true)
                .pdfContent(pdfContent)
                .summary(request.getSummary())
                .includePhone(options.isIncludePhone())
                .includeEmail(options.isIncludeEmail())
                .includeLinks(options.isIncludeLinks())
                .includeLocation(options.isIncludeLocation())
                .includeExperience(options.isIncludeExperience())
                .includeSkills(options.isIncludeSkills())
                .includeProjects(options.isIncludeProjects())
                .includeEducation(options.isIncludeEducation())
                .includeCertificates(options.isIncludeCertificates())
                .jobDescription(tailored ? request.getJobDescription() : null)
                .isTailored(tailored)
                .closestCareerName(atsResult.getClosestCareerName())
                .matchedKeywords(String.join(",", atsResult.getMatchedKeywords()))
                .missingKeywords(String.join(",", atsResult.getMissingKeywords()))
                .hasEducation(!isEmpty(profile.getEducations()))
                .hasProjects(!isEmpty(profile.getProjects()))
                .build());

        // Fail-soft, and last: the row is committed by the time this runs, so a broker outage costs
        // a lagging prs-service score, not a failed request for a resume that exists.
        eventPublisher.publishResumeGenerated(saved);

        log.info("Generated resume {} for studentId={} version={} atsScore={} tailored={} bytes={}",
                saved.getId(), studentId, version, atsResult.getScore(), tailored, pdfContent.length);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ResumeResponse setDefaultResume(String callerRole, Long studentId, Long resumeId) {
        requireStudent(callerRole, "Only a STUDENT may change their default resume");

        StudentResume target = resumeRepository.findByIdAndStudentId(resumeId, studentId)
                .orElseThrow(() -> new CustomException("Resume not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(target.getIsDefault())) {
            return toResponse(target);
        }

        clearDefaults(studentId);
        target.setIsDefault(true);
        StudentResume saved = resumeRepository.save(target);

        log.info("Set resume {} as default for studentId={}", resumeId, studentId);

        return toResponse(saved);
    }

    private void clearDefaults(Long studentId) {
        List<StudentResume> existing = resumeRepository.findAllByStudentId(studentId);
        if (!existing.isEmpty()) {
            existing.forEach(resume -> resume.setIsDefault(false));
            resumeRepository.saveAll(existing);
        }
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeResponse> getMyResumes(String callerRole, Long studentId) {
        requireStudent(callerRole, "Only a STUDENT may list their own resumes");

        return resumeRepository.findByStudentIdOrderByGeneratedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeResponse getResumeById(String callerRole, Long callerId, Long resumeId) {
        return toResponse(requireReadableResume(callerRole, callerId, resumeId));
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeDownload downloadResume(String callerRole, Long callerId, Long resumeId) {
        StudentResume resume = requireReadableResume(callerRole, callerId, resumeId);

        // Cannot be null: pdf_content is NOT NULL and every row is written with real bytes. Guarded
        // anyway so a hand-edited row surfaces as a clean 404 rather than a null body the client
        // would try to open as a PDF.
        if (resume.getPdfContent() == null || resume.getPdfContent().length == 0) {
            throw new CustomException("Resume file is unavailable - please regenerate your resume",
                    HttpStatus.NOT_FOUND);
        }

        return ResumeDownload.builder()
                .fileName(resume.getFileName())
                .content(resume.getPdfContent())
                .build();
    }

    @Override
    @Transactional
    public void deleteResume(String callerRole, Long studentId, Long resumeId) {
        requireStudent(callerRole, "Only a STUDENT may delete a resume");

        StudentResume resume = resumeRepository.findByIdAndStudentId(resumeId, studentId)
                .orElseThrow(() -> new CustomException("Resume not found", HttpStatus.NOT_FOUND));

        boolean wasDefault = Boolean.TRUE.equals(resume.getIsDefault());
        resumeRepository.delete(resume);

        // Deleting the default would otherwise leave the student with resumes but no default, so
        // the newest survivor is promoted. No survivors is fine -- there is nothing to default to.
        if (wasDefault) {
            resumeRepository.findAllByStudentId(studentId).stream()
                    .max(Comparator.comparing(StudentResume::getVersion))
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        resumeRepository.save(next);
                        log.info("Promoted resume {} to default for studentId={}", next.getId(), studentId);
                    });
        }

        log.info("Deleted resume {} for studentId={}", resumeId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeResponse> getResumesByStudentId(String callerRole, Long targetStudentId) {
        if (!RESUME_VIEWER_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN may list another student's resumes",
                    HttpStatus.FORBIDDEN);
        }

        return resumeRepository.findByStudentIdOrderByGeneratedAtDesc(targetStudentId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------------------------------

    private void requireStudent(String callerRole, String message) {
        if (!ResumeRoles.STUDENT.equals(callerRole)) {
            throw new CustomException(message, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * A STUDENT is scoped to their own rows by the compound finder, so another student's resume is
     * a 404, not a 403 -- a student has no legitimate reason to address a resume id that is not
     * theirs, and answering 403 would confirm the row exists. Same shape as recruiter-service's
     * company and job lookups, and the opposite of its application/interview lookups, where the
     * caller does have a legitimate relationship to the row and 403 is the honest answer.
     *
     * Everyone in RESUME_VIEWER_ROLES reads by id alone; the role check is the whole gate.
     */
    private StudentResume requireReadableResume(String callerRole, Long callerId, Long resumeId) {
        if (ResumeRoles.STUDENT.equals(callerRole)) {
            return resumeRepository.findByIdAndStudentId(resumeId, callerId)
                    .orElseThrow(() -> new CustomException("Resume not found", HttpStatus.NOT_FOUND));
        }

        if (!RESUME_VIEWER_ROLES.contains(callerRole)) {
            throw new CustomException("You are not permitted to read this resume", HttpStatus.FORBIDDEN);
        }

        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new CustomException("Resume not found", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------------------------

    /** From the full entity, after a write or a single-resume read. */
    private ResumeResponse toResponse(StudentResume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .studentId(resume.getStudentId())
                .fileName(resume.getFileName())
                .fileUrl(DOWNLOAD_PATH_PREFIX + resume.getId())
                .version(resume.getVersion())
                .atsScore(resume.getAtsScore())
                .isDefault(resume.getIsDefault())
                .generatedAt(resume.getGeneratedAt())
                .summary(resume.getSummary())
                .includePhone(resume.getIncludePhone())
                .includeEmail(resume.getIncludeEmail())
                .includeLinks(resume.getIncludeLinks())
                .includeLocation(resume.getIncludeLocation())
                .includeExperience(resume.getIncludeExperience())
                .includeSkills(resume.getIncludeSkills())
                .includeProjects(resume.getIncludeProjects())
                .includeEducation(resume.getIncludeEducation())
                .includeCertificates(resume.getIncludeCertificates())
                .isTailored(resume.getIsTailored())
                .jobDescription(resume.getJobDescription())
                .closestCareerName(resume.getClosestCareerName())
                .matchedKeywords(splitKeywords(resume.getMatchedKeywords()))
                .missingKeywords(splitKeywords(resume.getMissingKeywords()))
                .totalKeywords(splitKeywords(resume.getMatchedKeywords()).size()
                        + splitKeywords(resume.getMissingKeywords()).size())
                .hasEducation(resume.getHasEducation())
                .hasProjects(resume.getHasProjects())
                .build();
    }

    /**
     * From the projection, for list reads -- never loads pdfContent (or any of the other TEXT
     * columns; ResumeSummary only declares the fields a list row shows, see its own class comment).
     */
    private ResumeResponse toResponse(ResumeSummary resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .studentId(resume.getStudentId())
                .fileName(resume.getFileName())
                .fileUrl(DOWNLOAD_PATH_PREFIX + resume.getId())
                .version(resume.getVersion())
                .atsScore(resume.getAtsScore())
                .isDefault(resume.getIsDefault())
                .isTailored(resume.getIsTailored())
                .generatedAt(resume.getGeneratedAt())
                .build();
    }

    private List<String> splitKeywords(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return List.of(csv.split(","));
    }
}
