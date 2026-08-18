package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.PlacementStatsResponse;
import com.careerbridge.recruiter.dto.DepartmentPlacementStatsDto;
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
import com.careerbridge.recruiter.service.PlacementStatsServiceImpl;
import com.careerbridge.recruiter.service.PrsServiceClient;
import com.careerbridge.recruiter.service.StudentServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacementStatsServiceTest {

    private static final Long ORG_ID = 3L;
    private static final Long RECRUITER_ID = 7L;

    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobRepository jobRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PrsServiceClient prsServiceClient;
    @Mock private StudentServiceClient studentServiceClient;

    @InjectMocks private PlacementStatsServiceImpl statsService;

    private static PrsLeaderboardEntryDto rosterEntry(Long studentId) {
        PrsLeaderboardEntryDto entry = new PrsLeaderboardEntryDto();
        entry.setStudentId(studentId);
        return entry;
    }

    private static StudentDepartmentDto dept(Long studentId, String department) {
        return StudentDepartmentDto.builder().studentId(studentId).department(department).build();
    }

    private static JobApplication application(Long id, Long studentId, Long jobId,
                                              ApplicationStatus status, OfferOutcome outcome,
                                              String ctc) {
        return JobApplication.builder()
                .id(id).studentId(studentId).jobId(jobId).status(status)
                .offerOutcome(outcome)
                .offeredCtc(ctc == null ? null : new BigDecimal(ctc))
                .build();
    }

    // ------------------------------------------------------------------------------------------
    // Org-scoped
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("org stats: counts, rate, average and highest CTC all computed from accepted offers")
    void orgStats_WithOffers_ComputesAllFields() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L), rosterEntry(3L), rosterEntry(4L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "8.00"),
                application(2L, 2L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "12.00"),
                application(3L, 3L, 101L, ApplicationStatus.OFFERED, OfferOutcome.DECLINED, "6.00"),
                application(4L, 4L, 101L, ApplicationStatus.APPLIED, null, null)));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme Corp").build()));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);

        assertEquals(4, stats.getTotalStudentsInScope());
        assertEquals(4, stats.getTotalApplications());
        assertEquals(3, stats.getOffersExtended());
        assertEquals(2, stats.getOffersAccepted());
        assertEquals(1, stats.getOffersDeclined());
        // 2 placed of 4 distinct applicants
        assertEquals(50.00, stats.getPlacementRate());
        assertEquals(new BigDecimal("10.00"), stats.getAverageCtc());
        assertEquals(new BigDecimal("12.00"), stats.getHighestCtc());
        assertEquals(List.of("Acme Corp"), stats.getTopCompanies());
    }

    @Test
    @DisplayName("org stats: no offers yet gives zeros and NULL ctc, never 0.00")
    void orgStats_NoOffers_ReturnsZerosAndNullCtc() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID)).thenReturn(List.of(rosterEntry(1L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.APPLIED, null, null)));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);

        assertEquals(0, stats.getOffersAccepted());
        assertEquals(0.0, stats.getPlacementRate());
        assertNull(stats.getAverageCtc(), "an average of no offers is not zero rupees");
        assertNull(stats.getHighestCtc());
        assertTrue(stats.getTopCompanies().isEmpty());
    }

    @Test
    @DisplayName("org stats: prs-service down yields zeros, not a 500")
    void orgStats_PrsServiceDown_ReturnsZerosNotError() {
        // fetchOrgLeaderboard never throws -- it returns an empty list on any failure.
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID)).thenReturn(List.of());

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);

        assertEquals(0, stats.getTotalStudentsInScope());
        assertEquals(0, stats.getTotalApplications());
        assertEquals(0.0, stats.getPlacementRate());
        // Fail closed: never fall back to counting every application in the database.
        verify(jobApplicationRepository, never()).findByStudentIdIn(any());
    }

    @Test
    @DisplayName("org stats: an ORG_ADMIN with no org header sees zeros, never another org's data")
    void orgStats_OrgAdminWithNoOrgId_ReturnsZeros() {
        when(prsServiceClient.fetchOrgLeaderboard(null)).thenReturn(List.of());

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", null);

        assertEquals(0, stats.getTotalStudentsInScope());
        verify(jobApplicationRepository, never()).findByStudentIdIn(any());
    }

    @Test
    @DisplayName("org stats: SUPER_ADMIN reads the global roster, never an org-scoped one")
    void orgStats_SuperAdmin_UsesGlobalLeaderboard() {
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(rosterEntry(1L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of());

        statsService.getOrgPlacementStats("SUPER_ADMIN", null);

        verify(prsServiceClient).fetchGlobalLeaderboard();
        verify(prsServiceClient, never()).fetchOrgLeaderboard(any());
    }

    @Test
    @DisplayName("org stats: the rate counts distinct students, not applications")
    void placementRate_CountsDistinctStudentsNotApplications() {
        // One student, five applications, one accepted. Application-level maths would say 20%;
        // the student is fully placed, so the answer is 100%.
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID)).thenReturn(List.of(rosterEntry(1L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(2L, 1L, 101L, ApplicationStatus.REJECTED, null, null),
                application(3L, 1L, 102L, ApplicationStatus.REJECTED, null, null),
                application(4L, 1L, 103L, ApplicationStatus.APPLIED, null, null),
                application(5L, 1L, 104L, ApplicationStatus.APPLIED, null, null)));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme Corp").build()));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);

        assertEquals(100.00, stats.getPlacementRate());
        assertEquals(5, stats.getTotalApplications());
    }

    @Test
    @DisplayName("org stats: the rate is rounded to 2 decimals, never NaN")
    void placementRate_RoundsToTwoDecimals() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L), rosterEntry(3L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(2L, 2L, 100L, ApplicationStatus.APPLIED, null, null),
                application(3L, 3L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme Corp").build()));

        // 1 of 3 = 33.333... -> 33.33
        assertEquals(33.33, statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID).getPlacementRate());
    }

    @Test
    @DisplayName("org stats: topCompanies is capped at five distinct names")
    void topCompanies_CappedAtFive() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID)).thenReturn(List.of(rosterEntry(1L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(2L, 2L, 101L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(3L, 3L, 102L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(4L, 4L, 103L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(5L, 5L, 104L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00"),
                application(6L, 6L, 105L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "9.00")));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build(),
                Job.builder().id(101L).companyId(501L).build(),
                Job.builder().id(102L).companyId(502L).build(),
                Job.builder().id(103L).companyId(503L).build(),
                Job.builder().id(104L).companyId(504L).build(),
                Job.builder().id(105L).companyId(505L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("A").build(),
                Company.builder().id(501L).name("B").build(),
                Company.builder().id(502L).name("C").build(),
                Company.builder().id(503L).name("D").build(),
                Company.builder().id(504L).name("E").build(),
                Company.builder().id(505L).name("F").build()));

        assertEquals(5, statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID).getTopCompanies().size());
    }

    @Test
    @DisplayName("org stats: a student cannot read their college's placement numbers")
    void orgStats_StudentRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> statsService.getOrgPlacementStats("STUDENT", ORG_ID)).getStatus());
        verifyNoInteractions(prsServiceClient, jobApplicationRepository);
    }

    @Test
    @DisplayName("org stats: a recruiter is refused -- they get the recruiter-scoped endpoint instead")
    void orgStats_RecruiterRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> statsService.getOrgPlacementStats("RECRUITER", ORG_ID)).getStatus());
        verifyNoInteractions(prsServiceClient);
    }

    // ------------------------------------------------------------------------------------------
    // Recruiter-scoped
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("my stats: scoped to the recruiter's own jobs and computed the same way")
    void myStats_ScopedToOwnJobsOnly() {
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).recruiterId(RECRUITER_ID).build()));
        when(jobApplicationRepository.findByJobIdIn(List.of(100L))).thenReturn(List.of(
                application(1L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "11.00"),
                application(2L, 2L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme Corp").build()));

        PlacementStatsResponse stats = statsService.getMyPlacementStats("RECRUITER", RECRUITER_ID);

        assertEquals(2, stats.getTotalApplications());
        assertEquals(1, stats.getOffersAccepted());
        assertEquals(50.00, stats.getPlacementRate());
        assertEquals(new BigDecimal("11.00"), stats.getAverageCtc());
    }

    @Test
    @DisplayName("my stats: never touches prs-service, so it survives a prs outage")
    void myStats_NeverCallsPrsService() {
        // The entire reason this second endpoint exists: Job.recruiterId is a local column, so
        // nothing here depends on the only cross-service call in the stats path.
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).recruiterId(RECRUITER_ID).build()));
        when(jobApplicationRepository.findByJobIdIn(any())).thenReturn(List.of());

        statsService.getMyPlacementStats("RECRUITER", RECRUITER_ID);

        verifyNoInteractions(prsServiceClient);
        // Extends the same guarantee to the department source: populating a breakdown here would
        // have quietly reintroduced a cross-service dependency on this deliberately-local path.
        verifyNoInteractions(studentServiceClient);
    }

    @Test
    @DisplayName("my stats: a recruiter with no jobs gets zeros without querying applications")
    void myStats_NoJobs_ReturnsZeros() {
        when(jobRepository.findByRecruiterIdOrderByCreatedAtDesc(RECRUITER_ID)).thenReturn(List.of());

        PlacementStatsResponse stats = statsService.getMyPlacementStats("RECRUITER", RECRUITER_ID);

        assertEquals(0, stats.getTotalApplications());
        verify(jobApplicationRepository, never()).findByJobIdIn(any());
    }

    @Test
    @DisplayName("my stats: an ORG_ADMIN is refused -- they get the org-scoped endpoint instead")
    void myStats_OrgAdminRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> statsService.getMyPlacementStats("ORG_ADMIN", RECRUITER_ID)).getStatus());
        verifyNoInteractions(jobRepository, jobApplicationRepository);
    }

    // ------------------------------------------------------------------------------------------
    // Department breakdown
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("breakdown splits the same figures per department, ordered by offersAccepted desc")
    void orgStats_DepartmentBreakdown_SplitsPerDepartment() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L), rosterEntry(3L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(10L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "12.00"),
                application(11L, 2L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "18.00"),
                application(12L, 3L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(studentServiceClient.fetchStudentDepartments("ORG_ADMIN")).thenReturn(List.of(
                dept(1L, "Computer Science"), dept(2L, "Computer Science"), dept(3L, "Mechanical")));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme").build()));

        List<DepartmentPlacementStatsDto> breakdown =
                statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID).getDepartmentBreakdown();

        assertEquals(2, breakdown.size());
        // Ordered by offersAccepted descending, so CS (2) precedes Mechanical (0).
        DepartmentPlacementStatsDto cs = breakdown.get(0);
        assertEquals("Computer Science", cs.getDepartment());
        assertEquals(2, cs.getStudentsInScope());
        assertEquals(2, cs.getOffersAccepted());
        assertEquals(100.0, cs.getPlacementRate());
        assertEquals(new BigDecimal("15.00"), cs.getAverageCtc());
        assertEquals(new BigDecimal("18.00"), cs.getHighestCtc());

        DepartmentPlacementStatsDto mech = breakdown.get(1);
        assertEquals("Mechanical", mech.getDepartment());
        assertEquals(1, mech.getStudentsInScope());
        assertEquals(0, mech.getOffersAccepted());
        assertEquals(0.0, mech.getPlacementRate());
        assertNull(mech.getAverageCtc());
    }

    /**
     * The rows must account for every student in the roster. Dropping the unassigned cohort would
     * make the breakdown stop summing to the total -- arithmetic a reader trusts without checking.
     */
    @Test
    @DisplayName("students with no department become a single null-keyed row, not dropped")
    void orgStats_DepartmentBreakdown_KeepsUnassignedAsNullRow() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(10L, 1L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(studentServiceClient.fetchStudentDepartments("ORG_ADMIN")).thenReturn(List.of(
                dept(1L, "Computer Science"), dept(2L, null)));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);
        List<DepartmentPlacementStatsDto> breakdown = stats.getDepartmentBreakdown();

        assertEquals(2, breakdown.size());
        long summed = breakdown.stream().mapToLong(DepartmentPlacementStatsDto::getStudentsInScope).sum();
        assertEquals(stats.getTotalStudentsInScope(), summed);
        assertTrue(breakdown.stream().anyMatch(d -> d.getDepartment() == null));
    }

    /**
     * The numbers for one college must not absorb another one: the departments endpoint returns
     * every student on the platform, so the roster is what scopes it.
     */
    @Test
    @DisplayName("students outside the roster are excluded from the breakdown")
    void orgStats_DepartmentBreakdown_IgnoresStudentsOutsideRoster() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID)).thenReturn(List.of(rosterEntry(1L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(10L, 1L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(studentServiceClient.fetchStudentDepartments("ORG_ADMIN")).thenReturn(List.of(
                dept(1L, "Computer Science"),
                dept(99L, "Some Other College Department")));

        List<DepartmentPlacementStatsDto> breakdown =
                statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID).getDepartmentBreakdown();

        assertEquals(1, breakdown.size());
        assertEquals("Computer Science", breakdown.get(0).getDepartment());
        assertEquals(1, breakdown.get(0).getStudentsInScope());
    }

    /**
     * The breakdown degrades on its own. The top-level totals need no department data, so a
     * student-service outage must not cost them.
     */
    @Test
    @DisplayName("student-service down: breakdown is empty but the totals are still correct")
    void orgStats_StudentServiceDown_TotalsSurviveWithoutBreakdown() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(10L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "12.00")));
        when(studentServiceClient.fetchStudentDepartments("ORG_ADMIN")).thenReturn(List.of());
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme").build()));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);

        assertTrue(stats.getDepartmentBreakdown().isEmpty());
        assertEquals(2, stats.getTotalStudentsInScope());
        assertEquals(1, stats.getOffersAccepted());
        assertEquals(new BigDecimal("12.00"), stats.getAverageCtc());
    }

    /**
     * Department rows must never disagree with the total they belong to -- both come from the same
     * counting helpers, and this pins that they actually add up.
     */
    @Test
    @DisplayName("department rows sum to the top-level accepted and application counts")
    void orgStats_DepartmentBreakdown_SumsToTotals() {
        when(prsServiceClient.fetchOrgLeaderboard(ORG_ID))
                .thenReturn(List.of(rosterEntry(1L), rosterEntry(2L), rosterEntry(3L)));
        when(jobApplicationRepository.findByStudentIdIn(any())).thenReturn(List.of(
                application(10L, 1L, 100L, ApplicationStatus.OFFERED, OfferOutcome.ACCEPTED, "10.00"),
                application(11L, 2L, 100L, ApplicationStatus.OFFERED, OfferOutcome.DECLINED, "20.00"),
                application(12L, 3L, 100L, ApplicationStatus.APPLIED, null, null)));
        when(studentServiceClient.fetchStudentDepartments("ORG_ADMIN")).thenReturn(List.of(
                dept(1L, "CS"), dept(2L, "Mech"), dept(3L, "Civil")));
        when(jobRepository.findAllById(any())).thenReturn(List.of(
                Job.builder().id(100L).companyId(500L).build()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(
                Company.builder().id(500L).name("Acme").build()));

        PlacementStatsResponse stats = statsService.getOrgPlacementStats("ORG_ADMIN", ORG_ID);
        List<DepartmentPlacementStatsDto> breakdown = stats.getDepartmentBreakdown();

        assertEquals(stats.getOffersAccepted(),
                breakdown.stream().mapToLong(DepartmentPlacementStatsDto::getOffersAccepted).sum());
        assertEquals(stats.getOffersDeclined(),
                breakdown.stream().mapToLong(DepartmentPlacementStatsDto::getOffersDeclined).sum());
        assertEquals(stats.getTotalApplications(),
                breakdown.stream().mapToLong(DepartmentPlacementStatsDto::getTotalApplications).sum());
        assertEquals(stats.getTotalStudentsInScope(),
                breakdown.stream().mapToLong(DepartmentPlacementStatsDto::getStudentsInScope).sum());
    }
}
