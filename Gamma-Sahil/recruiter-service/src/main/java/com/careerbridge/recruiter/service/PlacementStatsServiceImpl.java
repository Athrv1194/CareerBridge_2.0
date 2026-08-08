package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.PlacementStatsResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.model.Job;
import com.careerbridge.recruiter.model.JobApplication;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.OfferOutcome;
import com.careerbridge.recruiter.repository.CompanyRepository;
import com.careerbridge.recruiter.repository.JobApplicationRepository;
import com.careerbridge.recruiter.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregate placement outcomes. A separate service from ApplicationServiceImpl deliberately:
 * reporting is a different concern from application CRUD, and that class is already large.
 *
 * Both paths aggregate IN MEMORY rather than in JPQL, and that is forced rather than chosen.
 * recruiter-service has zero JPA relationships -- every association is a raw Long FK joined in the
 * service layer -- so there is nothing to traverse in a query. More importantly there is no
 * organization column on ANY entity here, so an org-scoped aggregate cannot be expressed as SQL at
 * all; the roster has to come from prs-service first.
 *
 * Neither path is N+1. Both batch-load through findAllById exactly the way
 * ApplicationServiceImpl.withJobTitles already does:
 *
 *   org-scoped:       1 HTTP (prs roster) + 3 queries
 *   recruiter-scoped: 3 queries, zero HTTP
 */
@Service
public class PlacementStatsServiceImpl implements PlacementStatsService {

    private static final Logger log = LoggerFactory.getLogger(PlacementStatsServiceImpl.class);

    private static final Set<String> ORG_STATS_ROLES =
            Set.of(RecruiterRoles.ORG_ADMIN, RecruiterRoles.SUPER_ADMIN);

    private static final int TOP_COMPANIES_LIMIT = 5;

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final PrsServiceClient prsServiceClient;

    public PlacementStatsServiceImpl(JobApplicationRepository jobApplicationRepository,
                                     JobRepository jobRepository,
                                     CompanyRepository companyRepository,
                                     PrsServiceClient prsServiceClient) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.prsServiceClient = prsServiceClient;
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementStatsResponse getOrgPlacementStats(String callerRole, Long callerOrgId) {
        if (!ORG_STATS_ROLES.contains(callerRole)) {
            throw new CustomException("Only ORG_ADMIN or SUPER_ADMIN may view organization placement stats",
                    HttpStatus.FORBIDDEN);
        }

        List<PrsLeaderboardEntryDto> roster = RecruiterRoles.SUPER_ADMIN.equals(callerRole)
                ? prsServiceClient.fetchGlobalLeaderboard()
                : prsServiceClient.fetchOrgLeaderboard(callerOrgId);

        List<Long> studentIds = roster.stream()
                .map(PrsLeaderboardEntryDto::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (studentIds.isEmpty()) {
            // Reachable three ways and they are indistinguishable from here: prs-service down, a
            // genuinely empty organization, or an ORG_ADMIN whose token carries no X-User-Org-Id
            // (fetchOrgLeaderboard returns an empty list for a null orgId and never throws).
            // Fail closed -- treating a missing roster as "count everyone" would be a cross-tenant
            // leak. totalStudentsInScope=0 is what stops zeros reading as "nobody got placed".
            log.warn("Empty student roster for callerRole={} callerOrgId={}; returning empty stats",
                    callerRole, callerOrgId);
            return emptyStats();
        }

        return aggregate(jobApplicationRepository.findByStudentIdIn(studentIds), studentIds.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PlacementStatsResponse getMyPlacementStats(String callerRole, Long recruiterId) {
        if (!RecruiterRoles.RECRUITER.equals(callerRole)) {
            throw new CustomException("Only a RECRUITER may view their own placement stats",
                    HttpStatus.FORBIDDEN);
        }

        List<Long> jobIds = jobRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
                .map(Job::getId)
                .toList();

        if (jobIds.isEmpty()) {
            return emptyStats();
        }

        List<JobApplication> applications = jobApplicationRepository.findByJobIdIn(jobIds);

        // totalStudentsInScope is the applicant count here, not a roster size -- a recruiter has no
        // college roster to scope against.
        long applicants = applications.stream()
                .map(JobApplication::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return aggregate(applications, applicants);
    }

    /**
     * The one place the numbers are computed, shared by both scopes so they can never drift.
     *
     * studentsInScope is the denominator context, not the placement-rate denominator: the rate uses
     * distinct students who actually applied, since a student who never applied was never in the
     * placement funnel and would silently deflate the rate.
     */
    private PlacementStatsResponse aggregate(List<JobApplication> applications, long studentsInScope) {
        if (applications.isEmpty()) {
            return PlacementStatsResponse.builder()
                    .totalStudentsInScope(studentsInScope)
                    .totalApplications(0)
                    .offersExtended(0)
                    .offersAccepted(0)
                    .offersDeclined(0)
                    .placementRate(0.0)
                    .averageCtc(null)
                    .highestCtc(null)
                    .topCompanies(List.of())
                    .build();
        }

        long offersExtended = applications.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.OFFERED)
                .count();

        List<JobApplication> accepted = applications.stream()
                .filter(a -> a.getOfferOutcome() == OfferOutcome.ACCEPTED)
                .toList();

        long offersDeclined = applications.stream()
                .filter(a -> a.getOfferOutcome() == OfferOutcome.DECLINED)
                .count();

        // Distinct STUDENTS on both sides. A student who applies to twenty jobs and accepts one is
        // placed, and an application-level ratio would score that as 5%.
        long distinctApplicants = applications.stream()
                .map(JobApplication::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long distinctPlaced = accepted.stream()
                .map(JobApplication::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double placementRate = distinctApplicants == 0
                ? 0.0
                : BigDecimal.valueOf(distinctPlaced * 100.0 / distinctApplicants)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

        List<BigDecimal> acceptedCtcs = accepted.stream()
                .map(JobApplication::getOfferedCtc)
                .filter(Objects::nonNull)
                .toList();

        // Null rather than ZERO when nobody has accepted: an average of no offers is not zero
        // rupees, it is "not applicable", and a 0.00 on a dashboard reads as a real salary.
        BigDecimal averageCtc = acceptedCtcs.isEmpty() ? null
                : acceptedCtcs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(acceptedCtcs.size()), 2, RoundingMode.HALF_UP);

        BigDecimal highestCtc = acceptedCtcs.stream().max(BigDecimal::compareTo).orElse(null);

        return PlacementStatsResponse.builder()
                .totalStudentsInScope(studentsInScope)
                .totalApplications(applications.size())
                .offersExtended(offersExtended)
                .offersAccepted(accepted.size())
                .offersDeclined(offersDeclined)
                .placementRate(placementRate)
                .averageCtc(averageCtc)
                .highestCtc(highestCtc)
                .topCompanies(topCompanies(accepted))
                .build();
    }

    /**
     * Two batch queries regardless of how many accepted offers there are -- applications carry a
     * jobId, jobs carry a companyId, and only Company holds the name. Same findAllById shape as
     * ApplicationServiceImpl.withJobTitles.
     */
    private List<String> topCompanies(List<JobApplication> accepted) {
        if (accepted.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = accepted.stream()
                .map(JobApplication::getJobId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Long> companyIdByJobId = jobRepository.findAllById(jobIds).stream()
                .filter(job -> job.getCompanyId() != null)
                .collect(Collectors.toMap(Job::getId, Job::getCompanyId));

        List<Long> companyIds = companyIdByJobId.values().stream().distinct().toList();
        if (companyIds.isEmpty()) {
            return List.of();
        }

        Map<Long, String> nameByCompanyId = companyRepository.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        return accepted.stream()
                .map(JobApplication::getJobId)
                .map(companyIdByJobId::get)
                .filter(Objects::nonNull)
                .map(nameByCompanyId::get)
                .filter(Objects::nonNull)
                .distinct()
                .limit(TOP_COMPANIES_LIMIT)
                .toList();
    }

    private PlacementStatsResponse emptyStats() {
        return PlacementStatsResponse.builder()
                .totalStudentsInScope(0)
                .totalApplications(0)
                .offersExtended(0)
                .offersAccepted(0)
                .offersDeclined(0)
                .placementRate(0.0)
                .averageCtc(null)
                .highestCtc(null)
                .topCompanies(List.of())
                .build();
    }
}
