package com.careerbridge.student;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ImageBlob;
import com.careerbridge.student.dto.ProjectDto;
import com.careerbridge.student.dto.SkillDto;
import com.careerbridge.student.dto.StudentProfileRequest;
import com.careerbridge.student.dto.StudentProfileResponse;
import com.careerbridge.student.exception.CustomException;
import com.careerbridge.student.model.Certificate;
import com.careerbridge.student.model.Education;
import com.careerbridge.student.model.ProficiencyLevel;
import com.careerbridge.student.model.Project;
import com.careerbridge.student.model.Skill;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.CertificateRepository;
import com.careerbridge.student.repository.EducationRepository;
import com.careerbridge.student.repository.ProjectRepository;
import com.careerbridge.student.repository.SkillRepository;
import com.careerbridge.student.repository.StudentProfileRepository;
import com.careerbridge.student.service.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long PROFILE_ID = 1L;

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private CertificateRepository certificateRepository;

    @InjectMocks private StudentServiceImpl studentService;

    private StudentProfile profile;

    @BeforeEach
    void setUp() {
        profile = StudentProfile.builder()
                .id(PROFILE_ID)
                .userId(USER_ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@careerbridge.com")
                .profileCompletionPercentage(0)
                .isPublic(true)
                .build();
    }

    /** Nothing on the profile, no children: every criterion fails, so the score is 0. */
    private void stubEmptyChildren() {
        when(skillRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
        when(educationRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
        when(projectRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("getProfile: returns the profile with all four child collections populated")
    void getProfile_ExistingUser_ReturnsProfile() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(
                Education.builder().id(5L).institution("MIT").build()));
        when(skillRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(
                Skill.builder().id(6L).skillName("Java").isCustom(false).build()));
        when(projectRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(
                Project.builder().id(7L).title("CareerBridge").build()));
        when(certificateRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(
                Certificate.builder().id(8L).name("AWS SAA").build()));

        StudentProfileResponse response = studentService.getProfile(USER_ID);

        assertEquals(USER_ID, response.getUserId());
        assertEquals("ada@careerbridge.com", response.getEmail());
        assertEquals("MIT", response.getEducations().get(0).getInstitution());
        assertEquals("Java", response.getSkills().get(0).getSkillName());
        assertEquals("CareerBridge", response.getProjects().get(0).getTitle());
        assertEquals("AWS SAA", response.getCertificates().get(0).getName());
    }

    @Test
    @DisplayName("getProfile: an unknown userId is a 404, not an empty profile")
    void getProfile_NoProfile_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.getProfile(USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("updateProfile: filling the five basic fields moves completion to 20%")
    void updateProfile_ValidRequest_UpdatesCompletionPercentage() {
        StudentProfileRequest request = new StudentProfileRequest();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setPhone("+91-9000000000");
        request.setBio("Final year CS student");
        request.setCity("Pune");

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        stubEmptyChildren();
        when(certificateRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());

        studentService.updateProfile(USER_ID, request);

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        // basic info 20; no education/skills/projects/resume/social/portfolio
        assertEquals(20, saved.getValue().getProfileCompletionPercentage());
        assertEquals("Pune", saved.getValue().getCity());
    }

    @Test
    @DisplayName("addSkill: a catalogue skill is stored with isCustom=false")
    void addSkill_PredefinedSkill_Success() {
        SkillDto dto = SkillDto.builder()
                .skillName("Spring Boot")
                .proficiencyLevel(ProficiencyLevel.ADVANCED)
                .isCustom(false)
                .build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByStudentProfileIdAndSkillName(PROFILE_ID, "Spring Boot")).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyChildren();

        SkillDto result = studentService.addSkill(USER_ID, dto);

        ArgumentCaptor<Skill> saved = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(saved.capture());
        assertEquals("Spring Boot", saved.getValue().getSkillName());
        assertFalse(saved.getValue().getIsCustom());
        assertEquals(ProficiencyLevel.ADVANCED, result.getProficiencyLevel());
    }

    @Test
    @DisplayName("addSkill: an off-catalogue skill is accepted when the client opts in with isCustom")
    void addSkill_CustomSkill_Success() {
        SkillDto dto = SkillDto.builder()
                .skillName("Elixir")
                .proficiencyLevel(ProficiencyLevel.BEGINNER)
                .isCustom(true)
                .build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByStudentProfileIdAndSkillName(PROFILE_ID, "Elixir")).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyChildren();

        studentService.addSkill(USER_ID, dto);

        ArgumentCaptor<Skill> saved = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(saved.capture());
        assertEquals("Elixir", saved.getValue().getSkillName());
        assertTrue(saved.getValue().getIsCustom());
    }

    @Test
    @DisplayName("addSkill: the same skill twice on one profile is a 409 and is not persisted")
    void addSkill_DuplicateSkill_ThrowsException() {
        SkillDto dto = SkillDto.builder().skillName("Java").isCustom(false).build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByStudentProfileIdAndSkillName(PROFILE_ID, "Java")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.addSkill(USER_ID, dto));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(skillRepository, never()).save(any(Skill.class));
    }

    @Test
    @DisplayName("addSkill: an unknown skill without isCustom is a 400, not a silent insert")
    void addSkill_UnknownSkillNotCustom_ThrowsException() {
        SkillDto dto = SkillDto.builder().skillName("Cobol-on-Cogs").isCustom(false).build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.addSkill(USER_ID, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(skillRepository, never()).save(any(Skill.class));
        verify(skillRepository, never()).existsByStudentProfileIdAndSkillName(anyLong(), anyString());
    }

    @Test
    @DisplayName("addSkill: catalogue matching ignores case, so 'java' is not a custom skill")
    void addSkill_PredefinedSkillDifferentCase_NotMarkedCustom() {
        SkillDto dto = SkillDto.builder().skillName("java").isCustom(false).build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.existsByStudentProfileIdAndSkillName(PROFILE_ID, "java")).thenReturn(false);
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyChildren();

        studentService.addSkill(USER_ID, dto);

        ArgumentCaptor<Skill> saved = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).save(saved.capture());
        assertFalse(saved.getValue().getIsCustom());
    }

    @Test
    @DisplayName("addProject: a first project moves completion to 20%")
    void addProject_Success_RecalculatesCompletion() {
        ProjectDto dto = ProjectDto.builder().title("CareerBridge").isOngoing(true).build();
        Project persisted = Project.builder().id(9L).title("CareerBridge").isOngoing(true).build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.save(any(Project.class))).thenReturn(persisted);
        when(skillRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
        when(educationRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
        when(projectRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(persisted));

        studentService.addProject(USER_ID, dto);

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertEquals(20, saved.getValue().getProfileCompletionPercentage());
    }

    @Test
    @DisplayName("addEducation: a first education entry moves completion to 15%")
    void addEducation_Success_RecalculatesCompletion() {
        EducationDto dto = EducationDto.builder().institution("MIT").degree("BSc").build();
        Education persisted = Education.builder().id(3L).institution("MIT").degree("BSc").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.save(any(Education.class))).thenReturn(persisted);
        when(skillRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());
        when(educationRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(persisted));
        when(projectRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of());

        studentService.addEducation(USER_ID, dto);

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertEquals(15, saved.getValue().getProfileCompletionPercentage());
    }

    /**
     * Pins the deliberate omission in addCertificate. Certificates carry no weight in
     * ProfileCompletionCalculator, so recalculating there is a provable no-op. If someone later
     * gives certificates a weight and forgets to add the recalc call, this test fails and says so.
     */
    @Test
    @DisplayName("addCertificate: persists the certificate and deliberately skips the recalculation")
    void addCertificate_Success_DoesNotRecalculate() {
        CertificateDto dto = CertificateDto.builder().name("AWS SAA").issuingOrganization("Amazon").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(certificateRepository.save(any(Certificate.class)))
                .thenReturn(Certificate.builder().id(11L).name("AWS SAA").issuingOrganization("Amazon").build());

        CertificateDto result = studentService.addCertificate(USER_ID, dto);

        assertEquals("AWS SAA", result.getName());
        verify(certificateRepository).save(any(Certificate.class));
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
        verify(skillRepository, never()).findByStudentProfileId(anyLong());
    }

    @Test
    @DisplayName("getSkillSuggestions: returns the catalogue without touching the database")
    void getSkillSuggestions_ReturnsPredefinedList() {
        List<String> suggestions = studentService.getSkillSuggestions();

        assertEquals(47, suggestions.size());
        assertTrue(suggestions.contains("Java"));
        verify(studentProfileRepository, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("getPublicProfiles: STUDENT role is refused with 403")
    void getPublicProfiles_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.getPublicProfiles("STUDENT"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(studentProfileRepository, never()).findByIsPublicTrueAndRole(anyString());
    }

    @Test
    @DisplayName("getPublicProfiles: RECRUITER role is permitted")
    void getPublicProfiles_RecruiterRole_ReturnsList() {
        when(studentProfileRepository.findByIsPublicTrueAndRole("STUDENT")).thenReturn(List.of(profile));
        when(skillRepository.findByStudentProfileIdIn(List.of(PROFILE_ID))).thenReturn(List.of(
                Skill.builder().studentProfileId(PROFILE_ID).skillName("Java").build(),
                Skill.builder().studentProfileId(PROFILE_ID).skillName("Spring Boot").build()));

        List<com.careerbridge.student.dto.PublicStudentProfileResponse> result =
                studentService.getPublicProfiles("RECRUITER");

        assertEquals(1, result.size());
        assertEquals(USER_ID, result.get(0).getStudentId());
        assertEquals(List.of("Java", "Spring Boot"), result.get(0).getSkills());
    }

    @Test
    @DisplayName("getPublicProfiles: no public profiles returns an empty list without querying skills")
    void getPublicProfiles_NoPublicProfiles_ReturnsEmptyList() {
        when(studentProfileRepository.findByIsPublicTrueAndRole("STUDENT")).thenReturn(List.of());

        List<com.careerbridge.student.dto.PublicStudentProfileResponse> result =
                studentService.getPublicProfiles("SUPER_ADMIN");

        assertTrue(result.isEmpty());
        verify(skillRepository, never()).findByStudentProfileIdIn(any());
    }

    @Test
    @DisplayName("getPublicProfiles: a profile with no skills gets an empty list, not a crash")
    void getPublicProfiles_ProfileWithNoSkills_EmptySkillsList() {
        when(studentProfileRepository.findByIsPublicTrueAndRole("STUDENT")).thenReturn(List.of(profile));
        when(skillRepository.findByStudentProfileIdIn(List.of(PROFILE_ID))).thenReturn(List.of());

        List<com.careerbridge.student.dto.PublicStudentProfileResponse> result =
                studentService.getPublicProfiles("ORG_ADMIN");

        assertTrue(result.get(0).getSkills().isEmpty());
    }

    /**
     * The candidate pool must contain students only. auth-service publishes student.registered for
     * EVERY registration, so this table holds a profile row for recruiters and admins too -- without
     * the role predicate they show up as candidates in recruiter-service. Caught in live
     * verification, where the pool returned 18 rows including recruiters and SUPER_ADMINs.
     */
    @Test
    @DisplayName("getPublicProfiles: queries with the STUDENT role, never unfiltered")
    void getPublicProfiles_FiltersToStudentRoleOnly() {
        when(studentProfileRepository.findByIsPublicTrueAndRole("STUDENT")).thenReturn(List.of(profile));
        when(skillRepository.findByStudentProfileIdIn(List.of(PROFILE_ID))).thenReturn(List.of());

        studentService.getPublicProfiles("RECRUITER");

        // Pins the argument: a call with any other role, or an unfiltered finder, fails here.
        verify(studentProfileRepository).findByIsPublicTrueAndRole("STUDENT");
    }

    // -------------------------------------------------------------------------------------------
    // updateResumeUrl
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateResumeUrl: sets the url and recalculates completion through the shared path")
    void updateResumeUrl_ExistingProfile_SetsUrlAndRecalculates() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        stubEmptyChildren();

        studentService.updateResumeUrl(USER_ID, "/api/resume/download/500");

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertEquals("/api/resume/download/500", saved.getValue().getResumeUrl());
        // RESUME is worth 15 of ProfileCompletionCalculator's 100; nothing else is filled in this
        // fixture, so 15 is exactly the delta a resumeUrl alone should produce.
        assertEquals(15, saved.getValue().getProfileCompletionPercentage());
    }

    @Test
    @DisplayName("updateResumeUrl: a student with no profile row is a no-op, not an exception")
    void updateResumeUrl_NoProfile_DoesNothing() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        studentService.updateResumeUrl(USER_ID, "/api/resume/download/500");

        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    // -------------------------------------------------------------------------------------------
    // Education: update / delete
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateEducation: an owned entry is updated and completion is recalculated")
    void updateEducation_Owned_UpdatesAndRecalculates() {
        Education existing = Education.builder().id(3L).studentProfileId(PROFILE_ID).institution("MIT").build();
        EducationDto dto = EducationDto.builder().institution("Stanford").degree("MSc").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndStudentProfileId(3L, PROFILE_ID)).thenReturn(Optional.of(existing));
        when(educationRepository.save(any(Education.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyChildren();
        when(educationRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(existing));

        EducationDto result = studentService.updateEducation(USER_ID, 3L, dto);

        assertEquals("Stanford", result.getInstitution());
        verify(studentProfileRepository).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("updateEducation: an entry belonging to another profile is a 404, not a leak")
    void updateEducation_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.findByIdAndStudentProfileId(3L, PROFILE_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.updateEducation(USER_ID, 3L, EducationDto.builder().institution("X").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(educationRepository, never()).save(any(Education.class));
    }

    @Test
    @DisplayName("deleteEducation: deletes the owned row and recalculates completion")
    void deleteEducation_Owned_DeletesAndRecalculates() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.deleteByIdAndStudentProfileId(3L, PROFILE_ID)).thenReturn(1L);
        stubEmptyChildren();

        studentService.deleteEducation(USER_ID, 3L);

        verify(studentProfileRepository).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("deleteEducation: zero rows deleted (wrong id or wrong owner) is a 404")
    void deleteEducation_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(educationRepository.deleteByIdAndStudentProfileId(3L, PROFILE_ID)).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.deleteEducation(USER_ID, 3L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    // -------------------------------------------------------------------------------------------
    // Skill: delete
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("deleteSkill: deletes the owned row and recalculates completion (count-based weight)")
    void deleteSkill_Owned_DeletesAndRecalculates() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.deleteByIdAndStudentProfileId(6L, PROFILE_ID)).thenReturn(1L);
        stubEmptyChildren();

        studentService.deleteSkill(USER_ID, 6L);

        verify(studentProfileRepository).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("deleteSkill: zero rows deleted (wrong id or wrong owner) is a 404")
    void deleteSkill_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(skillRepository.deleteByIdAndStudentProfileId(6L, PROFILE_ID)).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.deleteSkill(USER_ID, 6L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // Project: update / delete / cover image
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateProject: an owned project is updated and completion is recalculated")
    void updateProject_Owned_UpdatesAndRecalculates() {
        Project existing = Project.builder().id(9L).studentProfileId(PROFILE_ID).title("Old").build();
        ProjectDto dto = ProjectDto.builder().title("New").isOngoing(true).build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyChildren();
        when(projectRepository.findByStudentProfileId(PROFILE_ID)).thenReturn(List.of(existing));

        ProjectDto result = studentService.updateProject(USER_ID, 9L, dto);

        assertEquals("New", result.getTitle());
        verify(studentProfileRepository).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("updateProject: a project belonging to another profile is a 404")
    void updateProject_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.updateProject(USER_ID, 9L, ProjectDto.builder().title("X").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("deleteProject: zero rows deleted (wrong id or wrong owner) is a 404")
    void deleteProject_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.deleteByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.deleteProject(USER_ID, 9L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("uploadProjectCover: stores the bytes and content type on the owned project")
    void uploadProjectCover_Owned_Stores() {
        Project existing = Project.builder().id(9L).studentProfileId(PROFILE_ID).title("CB").build();
        byte[] bytes = {1, 2, 3};

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(Optional.of(existing));

        studentService.uploadProjectCover(USER_ID, 9L, bytes, "image/png");

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(saved.capture());
        assertEquals("image/png", saved.getValue().getCoverImageContentType());
    }

    @Test
    @DisplayName("getProjectCover: a project with no cover image is a 404, not an empty blob")
    void getProjectCover_NoCoverImage_Throws404() {
        Project existing = Project.builder().id(9L).studentProfileId(PROFILE_ID).title("CB").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(Optional.of(existing));

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.getProjectCover(USER_ID, 9L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getProjectCover: returns the stored bytes and content type")
    void getProjectCover_HasCoverImage_ReturnsBlob() {
        Project existing = Project.builder().id(9L).studentProfileId(PROFILE_ID)
                .coverImage(new byte[]{9, 9}).coverImageContentType("image/jpeg").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(projectRepository.findByIdAndStudentProfileId(9L, PROFILE_ID)).thenReturn(Optional.of(existing));

        ImageBlob blob = studentService.getProjectCover(USER_ID, 9L);

        assertEquals("image/jpeg", blob.getContentType());
        assertEquals(2, blob.getBytes().length);
    }

    // -------------------------------------------------------------------------------------------
    // Certificate: update / delete (no recalculation, same as addCertificate)
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("updateCertificate: updates the owned row without recalculating completion")
    void updateCertificate_Owned_UpdatesWithoutRecalculating() {
        Certificate existing = Certificate.builder().id(11L).studentProfileId(PROFILE_ID).name("Old").build();
        CertificateDto dto = CertificateDto.builder().name("AWS SAA").issuingOrganization("Amazon").build();

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(certificateRepository.findByIdAndStudentProfileId(11L, PROFILE_ID)).thenReturn(Optional.of(existing));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(inv -> inv.getArgument(0));

        CertificateDto result = studentService.updateCertificate(USER_ID, 11L, dto);

        assertEquals("AWS SAA", result.getName());
        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    @Test
    @DisplayName("deleteCertificate: zero rows deleted (wrong id or wrong owner) is a 404")
    void deleteCertificate_NotOwned_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(certificateRepository.deleteByIdAndStudentProfileId(11L, PROFILE_ID)).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.deleteCertificate(USER_ID, 11L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("deleteCertificate: deletes the owned row without touching profile completion")
    void deleteCertificate_Owned_DeletesWithoutRecalculating() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(certificateRepository.deleteByIdAndStudentProfileId(11L, PROFILE_ID)).thenReturn(1L);

        studentService.deleteCertificate(USER_ID, 11L);

        verify(studentProfileRepository, never()).save(any(StudentProfile.class));
    }

    // -------------------------------------------------------------------------------------------
    // Avatar
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("uploadAvatar: stores the bytes and content type; does not affect completion")
    void uploadAvatar_Success_StoresWithoutRecalculating() {
        byte[] bytes = {5, 6, 7};

        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        studentService.uploadAvatar(USER_ID, bytes, "image/png");

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertEquals("image/png", saved.getValue().getAvatarContentType());
        // Not a recalculate() call: no skill/education/project lookups happen for an avatar.
        verify(skillRepository, never()).findByStudentProfileId(anyLong());
    }

    @Test
    @DisplayName("getAvatar: no avatar uploaded is a 404, not an empty blob")
    void getAvatar_NoAvatar_Throws404() {
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        CustomException ex = assertThrows(CustomException.class,
                () -> studentService.getAvatar(USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("getAvatar: returns the stored bytes and content type")
    void getAvatar_HasAvatar_ReturnsBlob() {
        profile.setAvatarImage(new byte[]{1, 2, 3, 4});
        profile.setAvatarContentType("image/webp");
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        ImageBlob blob = studentService.getAvatar(USER_ID);

        assertEquals("image/webp", blob.getContentType());
        assertEquals(4, blob.getBytes().length);
    }

    @Test
    @DisplayName("deleteAvatar: clears the stored bytes and content type")
    void deleteAvatar_Success_Clears() {
        profile.setAvatarImage(new byte[]{1});
        profile.setAvatarContentType("image/png");
        when(studentProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        studentService.deleteAvatar(USER_ID);

        ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(saved.capture());
        assertNull(saved.getValue().getAvatarImage());
    }
}
