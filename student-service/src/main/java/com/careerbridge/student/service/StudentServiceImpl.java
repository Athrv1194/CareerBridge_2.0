package com.careerbridge.student.service;

import com.careerbridge.student.constants.SkillConstants;
import com.careerbridge.student.dto.CertificateDto;
import com.careerbridge.student.dto.EducationDto;
import com.careerbridge.student.dto.ProjectDto;
import com.careerbridge.student.dto.SkillDto;
import com.careerbridge.student.dto.StudentProfileRequest;
import com.careerbridge.student.dto.StudentProfileResponse;
import com.careerbridge.student.exception.CustomException;
import com.careerbridge.student.model.Certificate;
import com.careerbridge.student.model.Education;
import com.careerbridge.student.model.Project;
import com.careerbridge.student.model.Skill;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.CertificateRepository;
import com.careerbridge.student.repository.EducationRepository;
import com.careerbridge.student.repository.ProjectRepository;
import com.careerbridge.student.repository.SkillRepository;
import com.careerbridge.student.repository.StudentProfileRepository;
import com.careerbridge.student.util.ProfileCompletionCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentServiceImpl implements StudentService {

    private final StudentProfileRepository studentProfileRepository;
    private final EducationRepository educationRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;
    private final CertificateRepository certificateRepository;

    public StudentServiceImpl(StudentProfileRepository studentProfileRepository,
                              EducationRepository educationRepository,
                              SkillRepository skillRepository,
                              ProjectRepository projectRepository,
                              CertificateRepository certificateRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.educationRepository = educationRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
        this.certificateRepository = certificateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long userId) {
        StudentProfile profile = requireProfile(userId);
        Long id = profile.getId();

        return StudentProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .email(profile.getEmail())
                .phone(profile.getPhone())
                .bio(profile.getBio())
                .city(profile.getCity())
                .state(profile.getState())
                .country(profile.getCountry())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .resumeUrl(profile.getResumeUrl())
                .profileCompletionPercentage(profile.getProfileCompletionPercentage())
                .isPublic(profile.getIsPublic())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .educations(educationRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .skills(skillRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .projects(projectRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .certificates(certificateRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .build();
    }

    /**
     * Full replace: a null field clears the stored value, so the client sends the whole profile.
     * email, resumeUrl, userId and the completion percentage are absent from the request DTO by
     * design and therefore cannot be overwritten from here.
     */
    @Override
    @Transactional
    public StudentProfileResponse updateProfile(Long userId, StudentProfileRequest request) {
        StudentProfile profile = requireProfile(userId);

        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setPhone(request.getPhone());
        profile.setBio(request.getBio());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setCountry(request.getCountry());
        profile.setLinkedinUrl(request.getLinkedinUrl());
        profile.setGithubUrl(request.getGithubUrl());
        profile.setPortfolioUrl(request.getPortfolioUrl());
        // The one field full-replace cannot blank: the column is NOT NULL, so an omitted
        // isPublic falls back to the entity default rather than failing the insert.
        profile.setIsPublic(request.getIsPublic() == null ? Boolean.TRUE : request.getIsPublic());

        recalculate(profile);

        return getProfile(userId);
    }

    @Override
    @Transactional
    public EducationDto addEducation(Long userId, EducationDto dto) {
        StudentProfile profile = requireProfile(userId);

        Education saved = educationRepository.save(Education.builder()
                .studentProfileId(profile.getId())
                .institution(dto.getInstitution())
                .degree(dto.getDegree())
                .fieldOfStudy(dto.getFieldOfStudy())
                .startYear(dto.getStartYear())
                .endYear(dto.getEndYear())
                .grade(dto.getGrade())
                .description(dto.getDescription())
                .build());

        recalculate(profile);
        return toDto(saved);
    }

    @Override
    @Transactional
    public SkillDto addSkill(Long userId, SkillDto dto) {
        StudentProfile profile = requireProfile(userId);

        // isCustom is derived, not trusted: the client cannot mark "Java" as a custom skill, and
        // must positively opt in (isCustom=true) to introduce anything off the catalogue.
        boolean predefined = isPredefined(dto.getSkillName());
        if (!predefined && !Boolean.TRUE.equals(dto.getIsCustom())) {
            throw new CustomException(
                    "Unknown skill '" + dto.getSkillName() + "'; set isCustom=true to add it",
                    HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(
                skillRepository.existsByStudentProfileIdAndSkillName(profile.getId(), dto.getSkillName()))) {
            throw new CustomException("Skill '" + dto.getSkillName() + "' is already on this profile",
                    HttpStatus.CONFLICT);
        }

        Skill saved = skillRepository.save(Skill.builder()
                .studentProfileId(profile.getId())
                .skillName(dto.getSkillName())
                .proficiencyLevel(dto.getProficiencyLevel())
                .isCustom(!predefined)
                .build());

        recalculate(profile);
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProjectDto addProject(Long userId, ProjectDto dto) {
        StudentProfile profile = requireProfile(userId);

        Project saved = projectRepository.save(Project.builder()
                .studentProfileId(profile.getId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .techStack(dto.getTechStack())
                .projectUrl(dto.getProjectUrl())
                .githubUrl(dto.getGithubUrl())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .isOngoing(dto.getIsOngoing() != null && dto.getIsOngoing())
                .build());

        recalculate(profile);
        return toDto(saved);
    }

    @Override
    @Transactional
    public CertificateDto addCertificate(Long userId, CertificateDto dto) {
        StudentProfile profile = requireProfile(userId);

        Certificate saved = certificateRepository.save(Certificate.builder()
                .studentProfileId(profile.getId())
                .name(dto.getName())
                .issuingOrganization(dto.getIssuingOrganization())
                .issueDate(dto.getIssueDate())
                .expiryDate(dto.getExpiryDate())
                .credentialUrl(dto.getCredentialUrl())
                .build());

        // ponytail: no recalculate() here -- certificates carry zero weight in
        // ProfileCompletionCalculator, so the call is a provable no-op costing 3 selects and an
        // update. StudentServiceTest pins this; give certificates a weight and that test fails.
        return toDto(saved);
    }

    @Override
    public List<String> getSkillSuggestions() {
        return SkillConstants.PREDEFINED_SKILLS;
    }

    private StudentProfile requireProfile(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("Student profile not found", HttpStatus.NOT_FOUND));
    }

    /** Single place the completion percentage is derived, so no caller can forget to update it. */
    private void recalculate(StudentProfile profile) {
        Long id = profile.getId();
        profile.setProfileCompletionPercentage(ProfileCompletionCalculator.calculateCompletion(
                profile,
                skillRepository.findByStudentProfileId(id),
                educationRepository.findByStudentProfileId(id),
                projectRepository.findByStudentProfileId(id)));
        studentProfileRepository.save(profile);
    }

    private boolean isPredefined(String skillName) {
        return SkillConstants.PREDEFINED_SKILLS.stream().anyMatch(s -> s.equalsIgnoreCase(skillName));
    }

    private EducationDto toDto(Education e) {
        return EducationDto.builder()
                .id(e.getId())
                .institution(e.getInstitution())
                .degree(e.getDegree())
                .fieldOfStudy(e.getFieldOfStudy())
                .startYear(e.getStartYear())
                .endYear(e.getEndYear())
                .grade(e.getGrade())
                .description(e.getDescription())
                .build();
    }

    private SkillDto toDto(Skill s) {
        return SkillDto.builder()
                .id(s.getId())
                .skillName(s.getSkillName())
                .proficiencyLevel(s.getProficiencyLevel())
                .isCustom(s.getIsCustom())
                .build();
    }

    private ProjectDto toDto(Project p) {
        return ProjectDto.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .techStack(p.getTechStack())
                .projectUrl(p.getProjectUrl())
                .githubUrl(p.getGithubUrl())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .isOngoing(p.getIsOngoing())
                .build();
    }

    private CertificateDto toDto(Certificate c) {
        return CertificateDto.builder()
                .id(c.getId())
                .name(c.getName())
                .issuingOrganization(c.getIssuingOrganization())
                .issueDate(c.getIssueDate())
                .expiryDate(c.getExpiryDate())
                .credentialUrl(c.getCredentialUrl())
                .build();
    }
}
