package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.CandidateResponse;

import java.util.List;

public interface CandidateSearchService {

    // skills: comma-separated, AT LEAST ONE match (AND would return nothing on real profiles).
    // minScore: excludes unavailable (-1.0) scores. maxScore: keeps unavailable scores.
    // department: exact match, case-insensitive, NOT substring ("CS" must not match "CSE").
    // Excludes unknown departments, same reasoning as minScore excluding unavailable scores.
    List<CandidateResponse> searchCandidates(String callerRole, String skills,
                                             Double minScore, Double maxScore, String department);
}
