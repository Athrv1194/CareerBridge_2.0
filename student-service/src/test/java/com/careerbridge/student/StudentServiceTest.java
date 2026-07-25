package com.careerbridge.student;

import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
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
}
