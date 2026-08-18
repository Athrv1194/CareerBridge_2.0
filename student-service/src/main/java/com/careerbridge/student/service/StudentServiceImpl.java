package com.careerbridge.student.service;

import com.careerbridge.student.constants.SkillConstants;
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
import com.careerbridge.student.model.Certificate;
import com.careerbridge.student.model.Education;
import com.careerbridge.student.model.Experience;
import com.careerbridge.student.model.Project;
import com.careerbridge.student.model.Skill;
import com.careerbridge.student.model.StudentProfile;
import com.careerbridge.student.repository.CertificateRepository;
import com.careerbridge.student.repository.EducationRepository;
import com.careerbridge.student.repository.ExperienceRepository;
import com.careerbridge.student.repository.ProjectRepository;
import com.careerbridge.student.repository.SkillRepository;
import com.careerbridge.student.repository.StudentProfileRepository;
import com.careerbridge.student.util.ProfileCompletionCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentServiceImpl implements StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentServiceImpl.class);

    /**
     * Matches auth-service's Role enum. Strings, because that is what arrives in
     * X-User-Role.
     */
    private static final Set<String> ALLOWED_PUBLIC_PROFILE_ROLES = Set.of("RECRUITER", "PLACEMENT_OFFICER",
            "ORG_ADMIN", "SUPER_ADMIN");

    /** The only role that belongs in a candidate pool. See getPublicProfiles. */
    private static final String ROLE_STUDENT = "STUDENT";

    private final StudentProfileRepository studentProfileRepository;
    private final EducationRepository educationRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;
    private final CertificateRepository certificateRepository;
    private final ExperienceRepository experienceRepository;

    public StudentServiceImpl(StudentProfileRepository studentProfileRepository,
            EducationRepository educationRepository,
            SkillRepository skillRepository,
            ProjectRepository projectRepository,
            CertificateRepository certificateRepository,
            ExperienceRepository experienceRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.educationRepository = educationRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
        this.certificateRepository = certificateRepository;
        this.experienceRepository = experienceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(Long userId) {
        return toProfileResponse(requireProfile(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProfileResponse getProfileForRecruiter(Long studentId, String callerRole) {
        if (!ALLOWED_PUBLIC_PROFILE_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN may view a candidate's profile",
                    HttpStatus.FORBIDDEN);
        }
        StudentProfile profile = requireProfile(studentId);
        // Same visibility rule as the candidate search list (findByIsPublicTrueAndRole) -- a
        // recruiter should not be able to open a private profile just by guessing its studentId.
        // 404, not 403: this mirrors resume-service's cross-student lookup shape elsewhere in the
        // project -- the caller has no legitimate reason to know the difference.
        if (!Boolean.TRUE.equals(profile.getIsPublic()) || !ROLE_STUDENT.equals(profile.getRole())) {
            throw new CustomException("Student profile not found", HttpStatus.NOT_FOUND);
        }
        return toProfileResponse(profile);
    }

    private StudentProfileResponse toProfileResponse(StudentProfile profile) {
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
                .hasAvatar(profile.getAvatarImage() != null)
                .profileCompletionPercentage(profile.getProfileCompletionPercentage())
                .isPublic(profile.getIsPublic())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .educations(educationRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .skills(skillRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .projects(projectRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .certificates(certificateRepository.findByStudentProfileId(id).stream().map(this::toDto).toList())
                .experiences(experienceRepository.findByStudentProfileIdOrderByStartDateDesc(id).stream()
                        .map(this::toDto).toList())
                .build();
    }

    /**
     * Full replace: a null field clears the stored value, so the client sends the
     * whole profile.
     * email, resumeUrl, userId and the completion percentage are absent from the
     * request DTO by
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
        // The one field full-replace cannot blank: the column is NOT NULL, so an
        // omitted
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
    public EducationDto updateEducation(Long userId, Long educationId, EducationDto dto) {
        StudentProfile profile = requireProfile(userId);
        Education existing = requireOwnedEducation(profile.getId(), educationId);

        existing.setInstitution(dto.getInstitution());
        existing.setDegree(dto.getDegree());
        existing.setFieldOfStudy(dto.getFieldOfStudy());
        existing.setStartYear(dto.getStartYear());
        existing.setEndYear(dto.getEndYear());
        existing.setGrade(dto.getGrade());
        existing.setDescription(dto.getDescription());

        Education saved = educationRepository.save(existing);
        recalculate(profile);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteEducation(Long userId, Long educationId) {
        StudentProfile profile = requireProfile(userId);
        long deleted = educationRepository.deleteByIdAndStudentProfileId(educationId, profile.getId());
        if (deleted == 0) {
            throw new CustomException("Education entry not found", HttpStatus.NOT_FOUND);
        }
        recalculate(profile);
    }

    @Override
    @Transactional
    public SkillDto addSkill(Long userId, SkillDto dto) {
        StudentProfile profile = requireProfile(userId);

        // isCustom is derived, not trusted: the client cannot mark "Java" as a custom
        // skill, and
        // must positively opt in (isCustom=true) to introduce anything off the
        // catalogue.
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
    public void deleteSkill(Long userId, Long skillId) {
        StudentProfile profile = requireProfile(userId);
        long deleted = skillRepository.deleteByIdAndStudentProfileId(skillId, profile.getId());
        if (deleted == 0) {
            throw new CustomException("Skill not found", HttpStatus.NOT_FOUND);
        }
        // Skill COUNT feeds 15% of completion (>=2 skills), so dropping below that
        // threshold here
        // must be reflected immediately, the same way addSkill's recalculate is not
        // optional.
        recalculate(profile);
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
    public ProjectDto updateProject(Long userId, Long projectId, ProjectDto dto) {
        StudentProfile profile = requireProfile(userId);
        Project existing = requireOwnedProject(profile.getId(), projectId);

        existing.setTitle(dto.getTitle());
        existing.setDescription(dto.getDescription());
        existing.setTechStack(dto.getTechStack());
        existing.setProjectUrl(dto.getProjectUrl());
        existing.setGithubUrl(dto.getGithubUrl());
        existing.setStartDate(dto.getStartDate());
        existing.setEndDate(dto.getEndDate());
        existing.setIsOngoing(dto.getIsOngoing() != null && dto.getIsOngoing());

        Project saved = projectRepository.save(existing);
        recalculate(profile);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        StudentProfile profile = requireProfile(userId);
        long deleted = projectRepository.deleteByIdAndStudentProfileId(projectId, profile.getId());
        if (deleted == 0) {
            throw new CustomException("Project not found", HttpStatus.NOT_FOUND);
        }
        recalculate(profile);
    }

    @Override
    @Transactional
    public void uploadProjectCover(Long userId, Long projectId, byte[] bytes, String contentType) {
        StudentProfile profile = requireProfile(userId);
        Project project = requireOwnedProject(profile.getId(), projectId);
        project.setCoverImage(bytes);
        project.setCoverImageContentType(contentType);
        projectRepository.save(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageBlob getProjectCover(Long userId, Long projectId) {
        StudentProfile profile = requireProfile(userId);
        Project project = requireOwnedProject(profile.getId(), projectId);
        if (project.getCoverImage() == null) {
            throw new CustomException("This project has no cover image", HttpStatus.NOT_FOUND);
        }
        return ImageBlob.builder().bytes(project.getCoverImage()).contentType(project.getCoverImageContentType())
                .build();
    }

    @Override
    @Transactional
    public void deleteProjectCover(Long userId, Long projectId) {
        StudentProfile profile = requireProfile(userId);
        Project project = requireOwnedProject(profile.getId(), projectId);
        project.setCoverImage(null);
        project.setCoverImageContentType(null);
        projectRepository.save(project);
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
        // ProfileCompletionCalculator, so the call is a provable no-op costing 3
        // selects and an
        // update. StudentServiceTest pins this; give certificates a weight and that
        // test fails.
        return toDto(saved);
    }

    @Override
    @Transactional
    public CertificateDto updateCertificate(Long userId, Long certificateId, CertificateDto dto) {
        StudentProfile profile = requireProfile(userId);
        Certificate existing = certificateRepository.findByIdAndStudentProfileId(certificateId, profile.getId())
                .orElseThrow(() -> new CustomException("Certificate not found", HttpStatus.NOT_FOUND));

        existing.setName(dto.getName());
        existing.setIssuingOrganization(dto.getIssuingOrganization());
        existing.setIssueDate(dto.getIssueDate());
        existing.setExpiryDate(dto.getExpiryDate());
        existing.setCredentialUrl(dto.getCredentialUrl());

        // No recalculate(): certificates carry zero weight, same as addCertificate
        // above.
        return toDto(certificateRepository.save(existing));
    }

    @Override
    @Transactional
    public void deleteCertificate(Long userId, Long certificateId) {
        StudentProfile profile = requireProfile(userId);
        long deleted = certificateRepository.deleteByIdAndStudentProfileId(certificateId, profile.getId());
        if (deleted == 0) {
            throw new CustomException("Certificate not found", HttpStatus.NOT_FOUND);
        }
        // No recalculate(): certificates carry zero weight, same as addCertificate
        // above.
    }

    @Override
    @Transactional
    public void uploadCertificateFile(Long userId, Long certificateId, byte[] bytes, String contentType, String fileName) {
        StudentProfile profile = requireProfile(userId);
        Certificate certificate = requireOwnedCertificate(profile.getId(), certificateId);
        certificate.setCredentialFile(bytes);
        certificate.setCredentialFileContentType(contentType);
        certificate.setCredentialFileName(fileName);
        certificateRepository.save(certificate);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageBlob getCertificateFile(Long userId, Long certificateId) {
        StudentProfile profile = requireProfile(userId);
        Certificate certificate = requireOwnedCertificate(profile.getId(), certificateId);
        if (certificate.getCredentialFile() == null) {
            throw new CustomException("This certificate has no file attached", HttpStatus.NOT_FOUND);
        }
        return ImageBlob.builder()
                .bytes(certificate.getCredentialFile())
                .contentType(certificate.getCredentialFileContentType())
                .fileName(certificate.getCredentialFileName())
                .build();
    }

    @Override
    @Transactional
    public void deleteCertificateFile(Long userId, Long certificateId) {
        StudentProfile profile = requireProfile(userId);
        Certificate certificate = requireOwnedCertificate(profile.getId(), certificateId);
        certificate.setCredentialFile(null);
        certificate.setCredentialFileContentType(null);
        certificate.setCredentialFileName(null);
        certificateRepository.save(certificate);
    }

    @Override
    @Transactional
    public ExperienceDto addExperience(Long userId, ExperienceDto dto) {
        StudentProfile profile = requireProfile(userId);

        Experience saved = experienceRepository.save(Experience.builder()
                .studentProfileId(profile.getId())
                .title(dto.getTitle())
                .company(dto.getCompany())
                .startDate(dto.getStartDate())
                .endDate(Boolean.TRUE.equals(dto.getIsCurrent()) ? null : dto.getEndDate())
                .isCurrent(dto.getIsCurrent() != null && dto.getIsCurrent())
                .description(dto.getDescription())
                .build());

        // No recalculate(): work experience carries no weight in ProfileCompletionCalculator --
        // adding it would mean re-deriving every existing student's completion percentage, a bigger
        // decision than this page needs. Same deliberate omission as certificates.
        return toDto(saved);
    }

    @Override
    @Transactional
    public ExperienceDto updateExperience(Long userId, Long experienceId, ExperienceDto dto) {
        StudentProfile profile = requireProfile(userId);
        Experience existing = experienceRepository.findByIdAndStudentProfileId(experienceId, profile.getId())
                .orElseThrow(() -> new CustomException("Experience entry not found", HttpStatus.NOT_FOUND));

        existing.setTitle(dto.getTitle());
        existing.setCompany(dto.getCompany());
        existing.setStartDate(dto.getStartDate());
        boolean current = dto.getIsCurrent() != null && dto.getIsCurrent();
        existing.setIsCurrent(current);
        existing.setEndDate(current ? null : dto.getEndDate());
        existing.setDescription(dto.getDescription());

        return toDto(experienceRepository.save(existing));
    }

    @Override
    @Transactional
    public void deleteExperience(Long userId, Long experienceId) {
        StudentProfile profile = requireProfile(userId);
        long deleted = experienceRepository.deleteByIdAndStudentProfileId(experienceId, profile.getId());
        if (deleted == 0) {
            throw new CustomException("Experience entry not found", HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void uploadAvatar(Long userId, byte[] bytes, String contentType) {
        StudentProfile profile = requireProfile(userId);
        profile.setAvatarImage(bytes);
        profile.setAvatarContentType(contentType);
        studentProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public ImageBlob getAvatar(Long userId) {
        StudentProfile profile = requireProfile(userId);
        if (profile.getAvatarImage() == null) {
            throw new CustomException("No avatar uploaded", HttpStatus.NOT_FOUND);
        }
        return ImageBlob.builder().bytes(profile.getAvatarImage()).contentType(profile.getAvatarContentType()).build();
    }

    @Override
    @Transactional
    public void deleteAvatar(Long userId) {
        StudentProfile profile = requireProfile(userId);
        profile.setAvatarImage(null);
        profile.setAvatarContentType(null);
        studentProfileRepository.save(profile);
    }

    @Override
    public List<String> getSkillSuggestions() {
        return SkillConstants.PREDEFINED_SKILLS;
    }

    /**
     * RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN and SUPER_ADMIN only -- a STUDENT
     * calling this would
     * be able to enumerate every other public profile on the platform, which is not
     * what "public"
     * on a single profile is meant to permit. RBAC lives here, in the service
     * layer, never in the
     * controller or the gateway.
     *
     * Filtered to STUDENT profiles only. auth-service publishes student.registered
     * for every
     * registration regardless of role, so this table holds a profile row for
     * recruiters and admins
     * too; without the role predicate they appear in recruiter-service's candidate
     * pool.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PublicStudentProfileResponse> getPublicProfiles(String callerRole) {
        if (!ALLOWED_PUBLIC_PROFILE_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN may list public profiles",
                    HttpStatus.FORBIDDEN);
        }

        List<StudentProfile> profiles = studentProfileRepository.findByIsPublicTrueAndRole(ROLE_STUDENT);
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<Long> profileIds = profiles.stream().map(StudentProfile::getId).toList();
        // One query for every profile's skills, grouped in memory -- not N queries.
        Map<Long, List<String>> skillsByProfileId = skillRepository.findByStudentProfileIdIn(profileIds)
                .stream()
                .collect(Collectors.groupingBy(Skill::getStudentProfileId,
                        Collectors.mapping(Skill::getSkillName, Collectors.toList())));

        return profiles.stream()
                .map(p -> PublicStudentProfileResponse.builder()
                        .studentId(p.getUserId())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .email(p.getEmail())
                        .skills(skillsByProfileId.getOrDefault(p.getId(), List.of()))
                        .profileCompletionPercentage(p.getProfileCompletionPercentage())
                        .hasAvatar(p.getAvatarImage() != null)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ImageBlob getAvatarForRecruiter(Long studentId, String callerRole) {
        if (!ALLOWED_PUBLIC_PROFILE_ROLES.contains(callerRole)) {
            throw new CustomException(
                    "Only RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN may view a candidate's avatar",
                    HttpStatus.FORBIDDEN);
        }
        StudentProfile profile = requireProfile(studentId);
        if (profile.getAvatarImage() == null) {
            throw new CustomException("No avatar uploaded", HttpStatus.NOT_FOUND);
        }
        return ImageBlob.builder().bytes(profile.getAvatarImage()).contentType(profile.getAvatarContentType()).build();
    }

    @Override
    @Transactional
    public void updateResumeUrl(Long userId, String resumeUrl) {
        studentProfileRepository.findByUserId(userId).ifPresentOrElse(profile -> {
            profile.setResumeUrl(resumeUrl);
            // recalculate() saves internally, same path
            // updateProfile/addEducation/addSkill/
            // addProject all use -- this is the only place RESUME's 15% can ever be earned.
            recalculate(profile);
        }, () -> log.warn("No student profile for userId={}; ignoring resume.generated", userId));
    }

    private StudentProfile requireProfile(Long userId) {
        return studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("Student profile not found", HttpStatus.NOT_FOUND));
    }

    private Education requireOwnedEducation(Long profileId, Long educationId) {
        return educationRepository.findByIdAndStudentProfileId(educationId, profileId)
                .orElseThrow(() -> new CustomException("Education entry not found", HttpStatus.NOT_FOUND));
    }

    private Project requireOwnedProject(Long profileId, Long projectId) {
        return projectRepository.findByIdAndStudentProfileId(projectId, profileId)
                .orElseThrow(() -> new CustomException("Project not found", HttpStatus.NOT_FOUND));
    }

    private Certificate requireOwnedCertificate(Long profileId, Long certificateId) {
        return certificateRepository.findByIdAndStudentProfileId(certificateId, profileId)
                .orElseThrow(() -> new CustomException("Certificate not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Single place the completion percentage is derived, so no caller can forget to
     * update it.
     */
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
                .hasCoverImage(p.getCoverImage() != null)
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
                .hasCredentialFile(c.getCredentialFile() != null)
                .credentialFileName(c.getCredentialFileName())
                .build();
    }

    private ExperienceDto toDto(Experience e) {
        return ExperienceDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .company(e.getCompany())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .isCurrent(e.getIsCurrent())
                .description(e.getDescription())
                .build();
    }
}
