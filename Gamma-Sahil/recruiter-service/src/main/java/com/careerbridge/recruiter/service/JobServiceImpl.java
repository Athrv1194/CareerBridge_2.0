package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.CreateJobRequest;
import com.careerbridge.recruiter.dto.JobResponse;
import com.careerbridge.recruiter.dto.JobSummaryResponse;
import com.careerbridge.recruiter.dto.UpdateJobRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.repository.CompanyRepository;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import com.careerbridge.recruiter.util.SkillsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public JobServiceImpl(JobRepository jobRepository,
                          CompanyRepository companyRepository,
                          JobApplicationRepository jobApplicationRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @Override
    @Transactional
    public JobResponse createJob(String callerRole, Long recruiterId, CreateJobRequest request) {
        requireRecruiter(callerRole);

        // Loaded by (companyId, recruiterId): posting a job under another recruiter's company is a
        // 404, so the company id space cannot be probed from here.
        Company company = companyRepository.findByIdAndRecruiterId(request.getCompanyId(), recruiterId)
                .orElseThrow(() -> new CustomException("Company not found", HttpStatus.NOT_FOUND));

        Job saved = jobRepository.save(Job.builder()
                .companyId(company.getId())
                .recruiterId(recruiterId)
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(request.getRequiredSkills())
                .location(request.getLocation())
                .workMode(request.getWorkMode())
                .jobType(request.getJobType())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .applicationDeadline(request.getApplicationDeadline())
                .build());

        log.info("Job {} created by recruiterId={} under companyId={}",
                saved.getId(), recruiterId, company.getId());
        return toResponse(saved, company.getName());
    }

    @Override
    @Transactional
    public JobResponse updateJob(String callerRole, Long recruiterId, Long jobId, UpdateJobRequest request) {
        requireRecruiter(callerRole);

        Job job = requireOwnedJob(recruiterId, jobId);

        // Null means "leave unchanged", not "clear".
        if (request.getTitle() != null) {
            job.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            job.setDescription(request.getDescription());
        }
        if (request.getRequiredSkills() != null) {
            job.setRequiredSkills(request.getRequiredSkills());
        }
        if (request.getLocation() != null) {
            job.setLocation(request.getLocation());
        }
        if (request.getWorkMode() != null) {
            job.setWorkMode(request.getWorkMode());
        }
        if (request.getJobType() != null) {
            job.setJobType(request.getJobType());
        }
        if (request.getSalaryMin() != null) {
            job.setSalaryMin(request.getSalaryMin());
        }
        if (request.getSalaryMax() != null) {
            job.setSalaryMax(request.getSalaryMax());
        }
        if (request.getApplicationDeadline() != null) {
            job.setApplicationDeadline(request.getApplicationDeadline());
        }

        Job saved = jobRepository.save(job);
        return toResponse(saved, companyNameOf(saved.getCompanyId()));
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));

        return toResponse(job, companyNameOf(job.getCompanyId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobSummaryResponse> listActiveJobs() {
        return toSummaries(jobRepository.findByIsActiveTrueOrderByCreatedAtDesc());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobSummaryResponse> listMyJobs(String callerRole, Long recruiterId) {
        requireRecruiter(callerRole);

        return toSummaries(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId));
    }

    @Override
    @Transactional
    public void deactivateJob(String callerRole, Long recruiterId, Long jobId) {
        requireRecruiter(callerRole);

        Job job = requireOwnedJob(recruiterId, jobId);

        if (Boolean.FALSE.equals(job.getIsActive())) {
            throw new CustomException("Job is already inactive", HttpStatus.BAD_REQUEST);
        }

        job.setIsActive(false);
        jobRepository.save(job);
        log.info("Job {} deactivated by recruiterId={}", jobId, recruiterId);
    }

    /**
     * Hard delete, and deliberately narrow: once anyone has applied, the applications (and any
     * interviews hanging off them) reference this job id, so removing the row would strand a
     * student's entire application history with no way to render it.
     */
    @Override
    @Transactional
    public void deleteJob(String callerRole, Long recruiterId, Long jobId) {
        requireRecruiter(callerRole);

        Job job = requireOwnedJob(recruiterId, jobId);

        if (jobApplicationRepository.existsByJobId(jobId)) {
            throw new CustomException(
                    "Cannot delete a job with existing applications - deactivate it instead",
                    HttpStatus.BAD_REQUEST);
        }

        jobRepository.delete(job);
        log.info("Job {} deleted by recruiterId={}", jobId, recruiterId);
    }

    private Job requireOwnedJob(Long recruiterId, Long jobId) {
        return jobRepository.findByIdAndRecruiterId(jobId, recruiterId)
                .orElseThrow(() -> new CustomException("Job not found", HttpStatus.NOT_FOUND));
    }

    private void requireRecruiter(String callerRole) {
        if (!RecruiterRoles.RECRUITER.equals(callerRole)) {
            throw new CustomException("Only a RECRUITER may manage job postings", HttpStatus.FORBIDDEN);
        }
    }

    /** Null when the company row is gone, rather than throwing: a read must not 500 on it. */
    private String companyNameOf(Long companyId) {
        return companyRepository.findById(companyId).map(Company::getName).orElse(null);
    }

    /** One extra query for every company named in the list, not one per job. */
    private List<JobSummaryResponse> toSummaries(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }

        List<Long> companyIds = jobs.stream().map(Job::getCompanyId).distinct().toList();
        Map<Long, String> namesById = companyRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        return jobs.stream()
                .map(job -> JobSummaryResponse.builder()
                        .id(job.getId())
                        .companyId(job.getCompanyId())
                        .companyName(namesById.get(job.getCompanyId()))
                        .title(job.getTitle())
                        .location(job.getLocation())
                        .workMode(job.getWorkMode())
                        .jobType(job.getJobType())
                        .salaryMin(job.getSalaryMin())
                        .salaryMax(job.getSalaryMax())
                        .applicationDeadline(job.getApplicationDeadline())
                        .isActive(job.getIsActive())
                        .createdAt(job.getCreatedAt())
                        .build())
                .toList();
    }

    private JobResponse toResponse(Job job, String companyName) {
        return JobResponse.builder()
                .id(job.getId())
                .companyId(job.getCompanyId())
                .companyName(companyName)
                .recruiterId(job.getRecruiterId())
                .title(job.getTitle())
                .description(job.getDescription())
                .requiredSkills(SkillsParser.parse(job.getRequiredSkills()))
                .location(job.getLocation())
                .workMode(job.getWorkMode())
                .jobType(job.getJobType())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .applicationDeadline(job.getApplicationDeadline())
                .isActive(job.getIsActive())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
