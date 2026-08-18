package com.careerbridge.recruiter;

import com.careerbridge.recruiter.dto.CandidateResponse;
import com.careerbridge.recruiter.dto.PrsLeaderboardEntryDto;
import com.careerbridge.recruiter.dto.PublicStudentProfileDto;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.service.CandidateSearchServiceImpl;
import com.careerbridge.recruiter.service.PrsServiceClient;
import com.careerbridge.recruiter.service.StudentServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateSearchServiceTest {

    @Mock private StudentServiceClient studentServiceClient;
    @Mock private PrsServiceClient prsServiceClient;

    @InjectMocks private CandidateSearchServiceImpl candidateSearchService;

    private static PublicStudentProfileDto profile(Long id, String first, List<String> skills) {
        return profile(id, first, skills, null);
    }

    private static PublicStudentProfileDto profile(Long id, String first, List<String> skills,
                                                   String department) {
        return PublicStudentProfileDto.builder()
                .studentId(id).firstName(first).lastName("Test")
                .email(first.toLowerCase() + "@careerbridge.com")
                .skills(skills).department(department).profileCompletionPercentage(60).build();
    }

    private static PrsLeaderboardEntryDto score(Long studentId, Double total) {
        return PrsLeaderboardEntryDto.builder().studentId(studentId).totalScore(total).grade("B").build();
    }

    @Test
    @DisplayName("searchCandidates: a STUDENT is refused with 403 before any client is called")
    void searchCandidates_StudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> candidateSearchService.searchCandidates("STUDENT", null, null, null, null));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(studentServiceClient, never()).fetchPublicProfiles(anyString());
        verify(prsServiceClient, never()).fetchGlobalLeaderboard();
    }

    /**
     * The whole point of the student-service enabler. If this ever returns empty against a live
     * stack with public profiles, the enabler is not wired -- an empty 200 here is a failure, not
     * a pass.
     */
    @Test
    @DisplayName("searchCandidates: student-service down yields an empty list, not a 500")
    void searchCandidates_StudentServiceDown_ReturnsEmpty() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of());

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null);

        assertTrue(result.isEmpty());
        // No point asking prs-service for scores when there are no candidates to score.
        verify(prsServiceClient, never()).fetchGlobalLeaderboard();
    }

    @Test
    @DisplayName("searchCandidates: prs-service down still returns candidates, scored -1.0")
    void searchCandidates_PrsDown_ShowsUnavailableScore() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"))));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of());

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null);

        assertEquals(1, result.size());
        assertEquals(-1.0, result.get(0).getPrsScore());
    }

    /**
     * An unknown score fails a floor but passes a ceiling: "at least 60" is a claim the data
     * cannot support, while "at most 60" has not been contradicted. Treating unknown as 0 would
     * get both wrong.
     */
    @Test
    @DisplayName("searchCandidates: an unavailable score is excluded by minScore but kept by maxScore")
    void searchCandidates_UnavailableScore_AsymmetricFiltering() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"))));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of());

        assertTrue(candidateSearchService.searchCandidates("RECRUITER", null, 50.0, null, null).isEmpty(),
                "an unknown score cannot be asserted to clear a floor");

        assertEquals(1, candidateSearchService.searchCandidates("RECRUITER", null, null, 50.0, null).size(),
                "an unknown score has not been shown to exceed a ceiling");
    }

    @Test
    @DisplayName("searchCandidates: skill filter matches case-insensitively on at least one skill")
    void searchCandidates_FiltersSkills() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java", "Spring Boot")),
                profile(2L, "Grace", List.of("Python")),
                profile(3L, "Alan", List.of("java"))));
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 70.0), score(2L, 80.0), score(3L, 60.0)));

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", "JAVA", null, null, null);

        assertEquals(2, result.size());
        assertEquals(List.of(1L, 3L), result.stream().map(CandidateResponse::getStudentId).sorted().toList());
    }

    @Test
    @DisplayName("searchCandidates: multiple requested skills match a candidate holding any one of them")
    void searchCandidates_MultipleSkills_MatchesAny() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java")),
                profile(2L, "Grace", List.of("Python")),
                profile(3L, "Alan", List.of("Rust"))));
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 70.0), score(2L, 80.0), score(3L, 60.0)));

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", "Java, Python", null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("searchCandidates: a candidate with no skills is excluded once a skill filter is set")
    void searchCandidates_NoSkills_ExcludedByFilter() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of()),
                profile(2L, "Grace", null)));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertTrue(candidateSearchService.searchCandidates("RECRUITER", "Java", null, null, null).isEmpty());
        // With no skill filter both are still candidates.
        assertEquals(2, candidateSearchService.searchCandidates("RECRUITER", null, null, null, null).size());
    }

    @Test
    @DisplayName("searchCandidates: score range excludes below min and above max, bounds inclusive")
    void searchCandidates_FiltersByScoreRange() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java")),
                profile(2L, "Grace", List.of("Java")),
                profile(3L, "Alan", List.of("Java"))));
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 40.0), score(2L, 60.0), score(3L, 80.0)));

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", null, 40.0, 60.0, null);

        assertEquals(List.of(2L, 1L), result.stream().map(CandidateResponse::getStudentId).toList());
    }

    @Test
    @DisplayName("searchCandidates: results are sorted by score descending, unavailable last")
    void searchCandidates_SortsByScoreDescending() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java")),
                profile(2L, "Grace", List.of("Java")),
                profile(3L, "Alan", List.of("Java"))));
        // Alan (3L) has no leaderboard row, so he scores -1.0 and must sort last.
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 55.0), score(2L, 90.0)));

        List<CandidateResponse> result =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null);

        assertEquals(List.of(2L, 1L, 3L), result.stream().map(CandidateResponse::getStudentId).toList());
        assertEquals(-1.0, result.get(2).getPrsScore());
    }

    @Test
    @DisplayName("searchCandidates: PLACEMENT_OFFICER and SUPER_ADMIN are permitted too")
    void searchCandidates_OtherPermittedRoles() {
        when(studentServiceClient.fetchPublicProfiles("PLACEMENT_OFFICER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"))));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertEquals(1,
                candidateSearchService.searchCandidates("PLACEMENT_OFFICER", null, null, null, null).size());
    }

    /**
     * The caller's real role is forwarded so student-service's own RBAC still governs the read --
     * this is not the privilege elevation PrsServiceClient deliberately performs.
     */
    @Test
    @DisplayName("searchCandidates: forwards the caller's role to student-service unchanged")
    void searchCandidates_ForwardsCallerRole() {
        when(studentServiceClient.fetchPublicProfiles("PLACEMENT_OFFICER")).thenReturn(List.of());

        candidateSearchService.searchCandidates("PLACEMENT_OFFICER", null, null, null, null);

        verify(studentServiceClient).fetchPublicProfiles("PLACEMENT_OFFICER");
    }

    @Test
    @DisplayName("searchCandidates: builds a profileUrl and carries skills through")
    void searchCandidates_MapsAllFields() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(42L, "Ada", List.of("Java", "SQL"))));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(42L, 70.0)));

        CandidateResponse candidate =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null).get(0);

        assertEquals(42L, candidate.getStudentId());
        assertEquals("ada@careerbridge.com", candidate.getEmail());
        assertEquals(List.of("Java", "SQL"), candidate.getSkills());
        assertEquals(70.0, candidate.getPrsScore());
        assertEquals(60, candidate.getProfileCompletionPercentage());
        assertEquals("/api/student/profile/42", candidate.getProfileUrl());
    }

    // -------------------------------------------------------------------------------------------
    // department filter
    //
    // department arrives on the profile itself -- auth-service owns it, publishes
    // user.department.updated, and student-service keeps the local copy that lands here. There is
    // no third client to stub.
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("department is carried through from the student profile onto the candidate")
    void searchCandidates_CarriesDepartment() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(42L, "Ada", List.of("Java"), "Computer Science")));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(42L, 70.0)));

        CandidateResponse candidate =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null).get(0);

        assertEquals("Computer Science", candidate.getDepartment());
    }

    @Test
    @DisplayName("department filter keeps only the matching department")
    void searchCandidates_DepartmentFilter_KeepsOnlyMatches() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java"), "Computer Science"),
                profile(2L, "Grace", List.of("Java"), "Mechanical")));
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 70.0), score(2L, 60.0)));

        List<CandidateResponse> results = candidateSearchService
                .searchCandidates("RECRUITER", null, null, null, "Computer Science");

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getStudentId());
    }

    @Test
    @DisplayName("department filter is case-insensitive")
    void searchCandidates_DepartmentFilter_CaseInsensitive() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"), "Computer Science")));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertEquals(1, candidateSearchService
                .searchCandidates("RECRUITER", null, null, null, "computer science").size());
    }

    /**
     * Exact match, not substring -- department names come from a fixed per-organization list, so
     * "CS" must not silently pull in every "CSE" student. The opposite choice from mentor-service's
     * deliberately-LIKE expertise filter.
     */
    @Test
    @DisplayName("department filter does NOT substring-match: CS excludes CSE")
    void searchCandidates_DepartmentFilter_IsNotSubstringMatch() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"), "CSE")));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertTrue(candidateSearchService
                .searchCandidates("RECRUITER", null, null, null, "CS").isEmpty());
    }

    /**
     * Same asymmetry minScore has against SCORE_UNAVAILABLE: an unknown department cannot be
     * asserted to match, so it is excluded rather than assumed.
     */
    @Test
    @DisplayName("a candidate with no department is excluded by a department filter")
    void searchCandidates_DepartmentFilter_ExcludesUnknownDepartment() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"), null)));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertTrue(candidateSearchService
                .searchCandidates("RECRUITER", null, null, null, "Computer Science").isEmpty());
    }

    @Test
    @DisplayName("a blank department filter is treated as no filter at all")
    void searchCandidates_BlankDepartmentFilter_IsIgnored() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"), null)));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        assertEquals(1, candidateSearchService
                .searchCandidates("RECRUITER", null, null, null, "   ").size());
    }

    @Test
    @DisplayName("an unassigned department is null on the response, not an empty string")
    void searchCandidates_UnassignedDepartment_IsNull() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER"))
                .thenReturn(List.of(profile(1L, "Ada", List.of("Java"), null)));
        when(prsServiceClient.fetchGlobalLeaderboard()).thenReturn(List.of(score(1L, 70.0)));

        List<CandidateResponse> results =
                candidateSearchService.searchCandidates("RECRUITER", null, null, null, null);

        assertEquals(1, results.size());
        assertNull(results.get(0).getDepartment());
    }

    /**
     * The department filter composes with the others rather than replacing them -- every supplied
     * filter is ANDed, as the interface contract states.
     */
    @Test
    @DisplayName("department filter combines with skills and score filters")
    void searchCandidates_DepartmentFilter_CombinesWithOtherFilters() {
        when(studentServiceClient.fetchPublicProfiles("RECRUITER")).thenReturn(List.of(
                profile(1L, "Ada", List.of("Java"), "Computer Science"),
                profile(2L, "Grace", List.of("Python"), "Computer Science"),
                profile(3L, "Alan", List.of("Java"), "Mechanical")));
        when(prsServiceClient.fetchGlobalLeaderboard())
                .thenReturn(List.of(score(1L, 70.0), score(2L, 80.0), score(3L, 90.0)));

        List<CandidateResponse> results = candidateSearchService
                .searchCandidates("RECRUITER", "Java", 50.0, null, "Computer Science");

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getStudentId());
    }
}
