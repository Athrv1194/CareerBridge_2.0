package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.DepartmentPlacementStatsDto;
import com.careerbridge.recruiter.dto.PlacementStatsResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.StudentDepartmentDto;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   org-scoped:       2 HTTP (prs roster, student departments) + 3 queries
 *   recruiter-scoped: 3 queries, zero HTTP
 *
 * The department breakdown adds ONE HTTP call to the org-scoped path and no queries at all -- it
 * re-slices the application list already in memory. The recruiter-scoped path deliberately gets no
 * breakdown, which is what preserves its zero-cross-service-call guarantee; see
 * PlacementStatsResponse.departmentBreakdown.
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
    private final StudentServiceClient studentServiceClient;

    public PlacementStatsServiceImpl(JobApplicationRepository jobApplicationRepository,
                                     JobRepository jobRepository,
                                     CompanyRepository companyRepository,
                                     PrsServiceClient prsServiceClient,
                                     StudentServiceClient studentServiceClient) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.prsServiceClient = prsServiceClient;
        this.studentServiceClient = studentServiceClient;
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

        List<JobApplication> applications = jobApplicationRepository.findByStudentIdIn(studentIds);

        PlacementStatsResponse stats = aggregate(applications, studentIds.size());
        stats.setDepartmentBreakdown(departmentBreakdown(callerRole, studentIds, applications));
        return stats;
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

        PlacementStatsResponse stats = aggregate(applications, applicants);
        // Explicitly empty, never null -- see PlacementStatsResponse.departmentBreakdown for why
        // this path deliberately makes no cross-service call to populate it.
        stats.setDepartmentBreakdown(List.of());
        return stats;
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
                    .departmentBreakdown(List.of())
                    .build();
        }

        // Every figure below comes from the same helpers departmentBreakdown() uses, so a department
        // row can never compute one of these differently from the total it belongs to.
        List<JobApplication> accepted = acceptedOf(applications);
        List<BigDecimal> ctcs = acceptedCtcs(accepted);

        return PlacementStatsResponse.builder()
                .totalStudentsInScope(studentsInScope)
                .totalApplications(applications.size())
                .offersExtended(countOffered(applications))
                .offersAccepted(accepted.size())
                .offersDeclined(countDeclined(applications))
                .placementRate(placementRate(applications, accepted))
                .averageCtc(averageCtc(ctcs))
                .highestCtc(highestCtc(ctcs))
                .topCompanies(topCompanies(accepted))
                .build();
    }

    /**
     * The same figures as the top level, computed per department over disjoint slices of the same
     * roster and the same application list -- so the department rows always sum to the totals.
     *
     * ZERO extra queries. It reuses the already-loaded application list and calls the same pure
     * counting helpers aggregate() uses; the only new I/O is one HTTP call for the department names.
     * Calling aggregate() itself per department would have been the obvious shape and is the trap:
     * it invokes topCompanies(), which is two queries, so a college with eight departments would
     * quietly become sixteen extra queries per dashboard load.
     *
     * Students with no department are kept as a single null-keyed row rather than dropped -- an
     * unassigned cohort that silently vanished would make the rows stop summing to the total, which
     * is exactly the kind of arithmetic a reader trusts without checking.
     *
     * Fail-soft: an unreachable student-service yields an empty breakdown while the top-level totals
     * stay correct, since those need no department data at all.
     */
    private List<DepartmentPlacementStatsDto> departmentBreakdown(String callerRole,
                                                                  List<Long> studentIds,
                                                                  List<JobApplication> applications) {
        List<StudentDepartmentDto> departments = studentServiceClient.fetchStudentDepartments(callerRole);
        if (departments.isEmpty()) {
            log.warn("No student departments available; returning stats with no department breakdown");
            return List.of();
        }

        Set<Long> rosterIds = Set.copyOf(studentIds);

        // Only students actually in the roster: the endpoint returns every student on the platform,
        // and an ORG_ADMIN's numbers must not pick up another college's cohort.
        Map<Long, String> departmentByStudentId = departments.stream()
                .filter(d -> d.getStudentId() != null && rosterIds.contains(d.getStudentId()))
                .collect(HashMap::new,
                        (map, d) -> map.put(d.getStudentId(), d.getDepartment()),
                        HashMap::putAll);

        if (departmentByStudentId.isEmpty()) {
            log.warn("No roster student matched a known department; returning no breakdown");
            return List.of();
        }

        // Roster headcount per department, including the null-keyed unassigned cohort. A LinkedHashMap
        // because HashMap permits a null key but Collectors.groupingBy does not.
        Map<String, Long> studentsPerDepartment = new LinkedHashMap<>();
        for (Long studentId : studentIds) {
            String department = departmentByStudentId.get(studentId);
            studentsPerDepartment.merge(department, 1L, Long::sum);
        }

        Map<String, List<JobApplication>> applicationsPerDepartment = new LinkedHashMap<>();
        for (JobApplication application : applications) {
            if (application.getStudentId() == null) {
                continue;
            }
            String department = departmentByStudentId.get(application.getStudentId());
            applicationsPerDepartment.computeIfAbsent(department, key -> new ArrayList<>())
                    .add(application);
        }

        return studentsPerDepartment.entrySet().stream()
                .map(entry -> {
                    String department = entry.getKey();
                    List<JobApplication> departmentApplications =
                            applicationsPerDepartment.getOrDefault(department, List.of());
                    List<JobApplication> accepted = acceptedOf(departmentApplications);
                    List<BigDecimal> ctcs = acceptedCtcs(accepted);

                    return DepartmentPlacementStatsDto.builder()
                            .department(department)
                            .studentsInScope(entry.getValue())
                            .totalApplications(departmentApplications.size())
                            .offersExtended(countOffered(departmentApplications))
                            .offersAccepted(accepted.size())
                            .offersDeclined(countDeclined(departmentApplications))
                            .placementRate(placementRate(departmentApplications, accepted))
                            .averageCtc(averageCtc(ctcs))
                            .highestCtc(highestCtc(ctcs))
                            .build();
                })
                .sorted(Comparator.comparingLong(DepartmentPlacementStatsDto::getOffersAccepted).reversed())
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Pure counting helpers -- no database access, shared by aggregate() and departmentBreakdown()
    // so a department row can never compute a figure differently from the total it belongs to.
    // ---------------------------------------------------------------------------------------------

    private List<JobApplication> acceptedOf(List<JobApplication> applications) {
        return applications.stream()
                .filter(a -> a.getOfferOutcome() == OfferOutcome.ACCEPTED)
                .toList();
    }

    private long countOffered(List<JobApplication> applications) {
        return applications.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFERED).count();
    }

    private long countDeclined(List<JobApplication> applications) {
        return applications.stream()
                .filter(a -> a.getOfferOutcome() == OfferOutcome.DECLINED)
                .count();
    }

    private long distinctStudents(List<JobApplication> applications) {
        return applications.stream()
                .map(JobApplication::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /** Distinct STUDENTS on both sides -- see PlacementStatsResponse.placementRate. */
    private double placementRate(List<JobApplication> applications, List<JobApplication> accepted) {
        long applicants = distinctStudents(applications);
        if (applicants == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(distinctStudents(accepted) * 100.0 / applicants)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<BigDecimal> acceptedCtcs(List<JobApplication> accepted) {
        return accepted.stream()
                .map(JobApplication::getOfferedCtc)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Null rather than ZERO when nobody has accepted -- see PlacementStatsResponse.averageCtc. */
    private BigDecimal averageCtc(List<BigDecimal> acceptedCtcs) {
        if (acceptedCtcs.isEmpty()) {
            return null;
        }
        return acceptedCtcs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(acceptedCtcs.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal highestCtc(List<BigDecimal> acceptedCtcs) {
        return acceptedCtcs.stream().max(BigDecimal::compareTo).orElse(null);
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
                .departmentBreakdown(List.of())
                .build();
    }
}
