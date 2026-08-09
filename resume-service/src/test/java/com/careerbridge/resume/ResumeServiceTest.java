package com.careerbridge.resume;

import com.careerbridge.resume.dto.AtsResult;
import com.careerbridge.resume.dto.ResumeDownload;
import com.careerbridge.resume.dto.ResumeGenerateRequest;
import com.careerbridge.resume.dto.ResumeResponse;
import com.careerbridge.resume.dto.StudentProfileDto;
import com.careerbridge.resume.exception.CustomException;
import com.careerbridge.resume.messaging.ResumeEventPublisher;
import org.springframework.http.HttpStatus;
import com.careerbridge.resume.model.ResumeSummary;
import com.careerbridge.resume.model.StudentResume;
import com.careerbridge.resume.pdf.ResumePdfBuilder;
import com.careerbridge.resume.repository.StudentResumeRepository;
import com.careerbridge.resume.service.AtsScoreCalculator;
import com.careerbridge.resume.service.ResumeServiceImpl;
import com.careerbridge.resume.service.StudentServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    private static final Long STUDENT_ID = 42L;
    private static final Long OTHER_STUDENT_ID = 99L;
    private static final Long RESUME_ID = 500L;
    private static final byte[] PDF_BYTES = "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII);

    @Mock private StudentResumeRepository resumeRepository;
    @Mock private StudentServiceClient studentServiceClient;
    @Mock private AtsScoreCalculator atsScoreCalculator;
    @Mock private ResumePdfBuilder resumePdfBuilder;
    @Mock private ResumeEventPublisher eventPublisher;

    @InjectMocks private ResumeServiceImpl resumeService;

    private StudentProfileDto profile;

    @BeforeEach
    void setUp() {
        profile = StudentProfileDto.builder()
                .userId(STUDENT_ID).firstName("Ada").lastName("Lovelace")
                .email("ada@careerbridge.com").build();
    }

    private static StudentResume resume(Long id, Long studentId, int version, boolean isDefault) {
        return StudentResume.builder()
                .id(id).studentId(studentId)
                .fileName("resume_" + studentId + "_v" + version + ".pdf")
                .version(version).atsScore(72.5).isDefault(isDefault)
                .pdfContent(PDF_BYTES).generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * A concrete ResumeSummary rather than a Mockito mock: toResponse reads all seven getters, so
     * mocking would need seven stubs per row and would trip strict-stub checks the moment the
     * mapper stopped reading one.
     */
    private static ResumeSummary summary(Long id, int version, boolean isDefault) {
        return new ResumeSummary() {
            @Override public Long getId() { return id; }
            @Override public Long getStudentId() { return STUDENT_ID; }
            @Override public String getFileName() { return "resume_42_v" + version + ".pdf"; }
            @Override public Integer getVersion() { return version; }
            @Override public Double getAtsScore() { return 72.5; }
            @Override public Boolean getIsDefault() { return isDefault; }
            @Override public Boolean getIsTailored() { return false; }
            @Override public LocalDateTime getGeneratedAt() { return LocalDateTime.now(); }
        };
    }

    private static AtsResult detailedResult() {
        return AtsResult.builder().score(72.5).closestCareerName("Backend Developer")
                .matchedKeywords(List.of("Java")).missingKeywords(List.of("Spring Boot")).totalKeywords(2).build();
    }

    // ---------------------------------------------------------------------------------------------
    // generateResume
    // ---------------------------------------------------------------------------------------------

    private void stubHappyGeneration() throws IOException {
        when(studentServiceClient.fetchMyProfile(STUDENT_ID)).thenReturn(profile);
        when(atsScoreCalculator.calculateDetailed(profile)).thenReturn(detailedResult());
        when(resumePdfBuilder.build(any(), any())).thenReturn(PDF_BYTES);
        when(resumeRepository.save(any(StudentResume.class))).thenAnswer(inv -> {
            StudentResume r = inv.getArgument(0);
            r.setId(RESUME_ID);
            return r;
        });
    }

    @Test
    @DisplayName("generateResume: a first resume is version 1, default, with the calculated ATS score")
    void generateResume_FirstResume_Version1() throws Exception {
        stubHappyGeneration();
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID)).thenReturn(Optional.empty());
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

        ResumeResponse result = resumeService.generateResume("STUDENT", STUDENT_ID, null);

        assertEquals(1, result.getVersion());
        assertEquals(72.5, result.getAtsScore());
        assertTrue(result.getIsDefault());
        assertEquals("resume_42_v1.pdf", result.getFileName());
        // fileUrl is derived from the generated id, never stored.
        assertEquals("/api/resume/download/500", result.getFileUrl());

        ArgumentCaptor<StudentResume> saved = ArgumentCaptor.forClass(StudentResume.class);
        verify(resumeRepository).save(saved.capture());
        assertArrayEquals(PDF_BYTES, saved.getValue().getPdfContent());
    }

    @Test
    @DisplayName("generateResume: version increments from the current highest, not the row count")
    void generateResume_VersionIncrement() throws Exception {
        stubHappyGeneration();
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID))
                .thenReturn(Optional.of(resume(1L, STUDENT_ID, 2, true)));
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

        ResumeResponse result = resumeService.generateResume("STUDENT", STUDENT_ID, null);

        assertEquals(3, result.getVersion());
        assertEquals("resume_42_v3.pdf", result.getFileName());
    }

    @Test
    @DisplayName("generateResume: every previous resume is flipped to non-default before the insert")
    void generateResume_MarksPreviousNonDefault() throws Exception {
        StudentResume previous = resume(1L, STUDENT_ID, 1, true);
        stubHappyGeneration();
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID))
                .thenReturn(Optional.of(previous));
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of(previous));

        resumeService.generateResume("STUDENT", STUDENT_ID, null);

        assertFalse(previous.getIsDefault(), "the previous resume should no longer be the default");
        verify(resumeRepository).saveAll(List.of(previous));
    }

    /**
     * Order is the property that matters: the row must be persisted before the event is published,
     * so neither prs-service nor student-service can be told about a resume that was never written.
     * The fail-soft half lives in ResumeEventPublisher and is pinned by ResumeEventPublisherTest --
     * mocking the publisher to throw here would only test the mock.
     */
    @Test
    @DisplayName("generateResume: the row is saved before resume.generated is published")
    void generateResume_SavesBeforePublishing() throws Exception {
        stubHappyGeneration();
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID)).thenReturn(Optional.empty());
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

        resumeService.generateResume("STUDENT", STUDENT_ID, null);

        InOrder order = inOrder(resumeRepository, eventPublisher);
        order.verify(resumeRepository).save(any(StudentResume.class));
        order.verify(eventPublisher).publishResumeGenerated(any(StudentResume.class));
    }

    /**
     * 503, not 500: student-service being unreachable is a downstream dependency failure the caller
     * should retry, not a bug in resume generation.
     */
    @Test
    @DisplayName("generateResume: student-service unreachable is a 503, and nothing is written")
    void generateResume_StudentServiceDown_Throws503() {
        when(studentServiceClient.fetchMyProfile(STUDENT_ID)).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.generateResume("STUDENT", STUDENT_ID, null));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(resumeRepository, never()).save(any());
        verify(eventPublisher, never()).publishResumeGenerated(any());
    }

    @Test
    @DisplayName("generateResume: a PDF failure is a 500, and no row is written")
    void generateResume_PdfBuildFails_Throws500() throws Exception {
        when(studentServiceClient.fetchMyProfile(STUDENT_ID)).thenReturn(profile);
        when(atsScoreCalculator.calculateDetailed(profile)).thenReturn(detailedResult());
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID)).thenReturn(Optional.empty());
        when(resumePdfBuilder.build(any(), any())).thenThrow(new IOException("render failed"));

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.generateResume("STUDENT", STUDENT_ID, null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        verify(resumeRepository, never()).save(any());
        verify(eventPublisher, never()).publishResumeGenerated(any());
    }

    @Test
    @DisplayName("generateResume: a RECRUITER is refused with 403 before student-service is called")
    void generateResume_WrongRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.generateResume("RECRUITER", STUDENT_ID, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(studentServiceClient, never()).fetchMyProfile(anyLong());
    }

    // ---------------------------------------------------------------------------------------------
    // getMyResumes
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getMyResumes: returns metadata from the projection, newest first")
    void getMyResumes_ReturnsProjectionMapped() {
        when(resumeRepository.findByStudentIdOrderByGeneratedAtDesc(STUDENT_ID))
                .thenReturn(List.of(summary(2L, 2, true), summary(1L, 1, false)));

        List<ResumeResponse> result = resumeService.getMyResumes("STUDENT", STUDENT_ID);

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersion());
        assertTrue(result.get(0).getIsDefault());
        assertEquals("/api/resume/download/2", result.get(0).getFileUrl());
    }

    @Test
    @DisplayName("getMyResumes: a RECRUITER is refused with 403")
    void getMyResumes_WrongRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.getMyResumes("RECRUITER", STUDENT_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ---------------------------------------------------------------------------------------------
    // getResumeById / downloadResume
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getResumeById: a STUDENT reads their own resume through the ownership-scoped finder")
    void getResumeById_StudentOwn_Returns() {
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID))
                .thenReturn(Optional.of(resume(RESUME_ID, STUDENT_ID, 1, true)));

        ResumeResponse result = resumeService.getResumeById("STUDENT", STUDENT_ID, RESUME_ID);

        assertEquals(RESUME_ID, result.getId());
        // Never the unscoped finder for a student -- that is what would leak another's resume.
        verify(resumeRepository, never()).findById(anyLong());
    }

    /**
     * 404, not 403, and deliberately so: a student has no legitimate reason to address a resume id
     * that is not theirs, so answering 403 would confirm the row exists. Same shape as
     * recruiter-service's company and job lookups; the opposite of its application and interview
     * lookups, where the caller does have a legitimate relationship to the row.
     */
    @Test
    @DisplayName("getResumeById: another student's resume is a 404, never a 403 that confirms it exists")
    void getResumeById_StudentNotOwner_Throws404() {
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, OTHER_STUDENT_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.getResumeById("STUDENT", OTHER_STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getResumeById: a RECRUITER reads any resume by id, unscoped")
    void getResumeById_Recruiter_ReadsAnyById() {
        when(resumeRepository.findById(RESUME_ID))
                .thenReturn(Optional.of(resume(RESUME_ID, STUDENT_ID, 1, true)));

        ResumeResponse result = resumeService.getResumeById("RECRUITER", OTHER_STUDENT_ID, RESUME_ID);

        assertEquals(STUDENT_ID, result.getStudentId());
    }

    @Test
    @DisplayName("getResumeById: an unrecognised role is refused with 403")
    void getResumeById_UnknownRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.getResumeById("MENTOR", STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(resumeRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("downloadResume: returns the stored bytes and the stored filename in one read")
    void downloadResume_ReturnsBytesAndFileName() {
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID))
                .thenReturn(Optional.of(resume(RESUME_ID, STUDENT_ID, 2, true)));

        ResumeDownload download = resumeService.downloadResume("STUDENT", STUDENT_ID, RESUME_ID);

        assertArrayEquals(PDF_BYTES, download.getContent());
        assertEquals("resume_42_v2.pdf", download.getFileName());
    }

    @Test
    @DisplayName("downloadResume: a row with empty bytes is a 404 telling the student to regenerate")
    void downloadResume_EmptyContent_Throws404() {
        StudentResume empty = resume(RESUME_ID, STUDENT_ID, 1, true);
        empty.setPdfContent(new byte[0]);
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(empty));

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.downloadResume("STUDENT", STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---------------------------------------------------------------------------------------------
    // deleteResume
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deleteResume: deleting the default promotes the newest survivor by version")
    void deleteResume_WasDefault_PromotesNewestSurvivor() {
        StudentResume toDelete = resume(RESUME_ID, STUDENT_ID, 3, true);
        StudentResume older = resume(1L, STUDENT_ID, 1, false);
        StudentResume newerSurvivor = resume(2L, STUDENT_ID, 2, false);

        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(toDelete));
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of(older, newerSurvivor));

        resumeService.deleteResume("STUDENT", STUDENT_ID, RESUME_ID);

        verify(resumeRepository).delete(toDelete);
        // Promoted by version, not by generatedAt -- versions are strictly increasing and immune to
        // same-second timestamp ties.
        assertTrue(newerSurvivor.getIsDefault());
        assertFalse(older.getIsDefault());
        verify(resumeRepository).save(newerSurvivor);
    }

    @Test
    @DisplayName("deleteResume: deleting a non-default resume promotes nothing")
    void deleteResume_NotDefault_PromotesNothing() {
        StudentResume toDelete = resume(RESUME_ID, STUDENT_ID, 1, false);
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(toDelete));

        resumeService.deleteResume("STUDENT", STUDENT_ID, RESUME_ID);

        verify(resumeRepository).delete(toDelete);
        verify(resumeRepository, never()).findAllByStudentId(anyLong());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteResume: deleting the only resume leaves nothing to promote")
    void deleteResume_LastResume_NothingToPromote() {
        StudentResume toDelete = resume(RESUME_ID, STUDENT_ID, 1, true);
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(toDelete));
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

        resumeService.deleteResume("STUDENT", STUDENT_ID, RESUME_ID);

        verify(resumeRepository).delete(toDelete);
        verify(resumeRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteResume: another student's resume is a 404, and nothing is deleted")
    void deleteResume_NotOwner_Throws404() {
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, OTHER_STUDENT_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.deleteResume("STUDENT", OTHER_STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(resumeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteResume: a RECRUITER is refused with 403")
    void deleteResume_WrongRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.deleteResume("RECRUITER", STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(resumeRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------------------------------------
    // getResumesByStudentId
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getResumesByStudentId: a RECRUITER may list another student's resumes")
    void getResumesByStudentId_Recruiter_Returns() {
        when(resumeRepository.findByStudentIdOrderByGeneratedAtDesc(STUDENT_ID))
                .thenReturn(List.of(summary(1L, 1, true)));

        List<ResumeResponse> result = resumeService.getResumesByStudentId("RECRUITER", STUDENT_ID);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getResumesByStudentId: a STUDENT is refused, and the query never runs")
    void getResumesByStudentId_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.getResumesByStudentId("STUDENT", OTHER_STUDENT_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(resumeRepository, never()).findByStudentIdOrderByGeneratedAtDesc(anyLong());
    }

    // ---------------------------------------------------------------------------------------------
    // generateResume: tailor mode
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("generateResume: a non-blank jobDescription switches to tailored scoring")
    void generateResume_WithJobDescription_UsesTailoredScoring() throws Exception {
        when(studentServiceClient.fetchMyProfile(STUDENT_ID)).thenReturn(profile);
        when(atsScoreCalculator.calculateTailored(profile, "Need Java and Docker"))
                .thenReturn(AtsResult.builder().score(50.0).closestCareerName(null)
                        .matchedKeywords(List.of("Java")).missingKeywords(List.of("Docker")).totalKeywords(2).build());
        when(resumePdfBuilder.build(any(), any())).thenReturn(PDF_BYTES);
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID)).thenReturn(Optional.empty());
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());
        when(resumeRepository.save(any(StudentResume.class))).thenAnswer(inv -> {
            StudentResume r = inv.getArgument(0);
            r.setId(RESUME_ID);
            return r;
        });

        ResumeGenerateRequest request = ResumeGenerateRequest.builder().jobDescription("Need Java and Docker").build();
        ResumeResponse result = resumeService.generateResume("STUDENT", STUDENT_ID, request);

        assertTrue(result.getIsTailored());
        assertEquals(50.0, result.getAtsScore());
        assertEquals(List.of("Java"), result.getMatchedKeywords());
        assertEquals(List.of("Docker"), result.getMissingKeywords());
        verify(atsScoreCalculator, never()).calculateDetailed(any());
    }

    @Test
    @DisplayName("generateResume: a blank jobDescription still uses best-match scoring, not tailored")
    void generateResume_BlankJobDescription_UsesDetailedScoring() throws Exception {
        stubHappyGeneration();
        when(resumeRepository.findTopByStudentIdOrderByVersionDesc(STUDENT_ID)).thenReturn(Optional.empty());
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of());

        ResumeGenerateRequest request = ResumeGenerateRequest.builder().jobDescription("   ").build();
        ResumeResponse result = resumeService.generateResume("STUDENT", STUDENT_ID, request);

        assertFalse(result.getIsTailored());
        verify(atsScoreCalculator, never()).calculateTailored(any(), any());
    }

    // ---------------------------------------------------------------------------------------------
    // setDefaultResume
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("setDefaultResume: flips the target to default and every other resume to non-default")
    void setDefaultResume_Owned_FlipsDefaults() {
        StudentResume target = resume(RESUME_ID, STUDENT_ID, 1, false);
        StudentResume currentDefault = resume(2L, STUDENT_ID, 2, true);

        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(target));
        when(resumeRepository.findAllByStudentId(STUDENT_ID)).thenReturn(List.of(target, currentDefault));
        when(resumeRepository.save(any(StudentResume.class))).thenAnswer(inv -> inv.getArgument(0));

        ResumeResponse result = resumeService.setDefaultResume("STUDENT", STUDENT_ID, RESUME_ID);

        assertTrue(result.getIsDefault());
        assertFalse(currentDefault.getIsDefault());
    }

    @Test
    @DisplayName("setDefaultResume: already-default is a no-op, not an error")
    void setDefaultResume_AlreadyDefault_NoOp() {
        StudentResume target = resume(RESUME_ID, STUDENT_ID, 1, true);
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, STUDENT_ID)).thenReturn(Optional.of(target));

        resumeService.setDefaultResume("STUDENT", STUDENT_ID, RESUME_ID);

        verify(resumeRepository, never()).findAllByStudentId(anyLong());
        verify(resumeRepository, never()).save(any());
    }

    @Test
    @DisplayName("setDefaultResume: another student's resume is a 404")
    void setDefaultResume_NotOwner_Throws404() {
        when(resumeRepository.findByIdAndStudentId(RESUME_ID, OTHER_STUDENT_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.setDefaultResume("STUDENT", OTHER_STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("setDefaultResume: a RECRUITER is refused with 403")
    void setDefaultResume_WrongRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> resumeService.setDefaultResume("RECRUITER", STUDENT_ID, RESUME_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(resumeRepository, never()).findByIdAndStudentId(anyLong(), anyLong());
    }
}
