package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.CandidateResponse;

import java.util.List;

public interface CandidateSearchService {

    /**
     * RECRUITER, PLACEMENT_OFFICER or SUPER_ADMIN. Every parameter is optional; all supplied
     * filters are combined with AND.
     *
     * @param skills   comma-separated. A candidate matches if they hold AT LEAST ONE of them
     *                 (case-insensitive), since requiring all of them makes a two-skill search
     *                 return nothing on a realistic profile.
     * @param minScore excludes candidates below it, and also excludes anyone whose PRS score is
     *                 unavailable -- an unknown score cannot be asserted to clear a floor.
     * @param maxScore excludes candidates above it. Unavailable scores are kept: they have not
     *                 been shown to exceed the ceiling.
     * @param department exact department name, matched case-insensitively -- NOT a substring match,
     *                 so "CS" does not also match "CSE". Excludes anyone whose department is
     *                 unknown, for the same reason minScore does: an unknown value cannot be
     *                 asserted to match. An auth-service outage therefore empties a
     *                 department-filtered search rather than returning wrong hits.
     */
    List<CandidateResponse> searchCandidates(String callerRole, String skills,
                                             Double minScore, Double maxScore, String department);
}
