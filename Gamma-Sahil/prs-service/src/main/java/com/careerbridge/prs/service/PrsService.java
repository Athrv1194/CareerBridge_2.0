package com.careerbridge.prs.service;

import com.careerbridge.prs.dto.PrsLeaderboardEntry;
import com.careerbridge.prs.dto.PrsResponse;

import java.util.List;

public interface PrsService {

    /**
     * Creates the student's all-zero, grade-F record. Idempotent. Called only from the
     * student.registered consumer.
     *
     * Takes organizationId as well as studentId because this is the only event carrying it, and it
     * is what scopes the ORG_ADMIN leaderboard. Nullable -- a SUPER_ADMIN belongs to no
     * organization.
     */
    void initialisePrs(Long studentId, Long organizationId);

    /** 40% input. Recomputes the total and grade, then publishes prs.updated fail-soft. */
    void updateAssessmentScore(Long studentId, Double matchPercentage);

    /** 30% input. Recomputes the total and grade, then publishes prs.updated fail-soft. */
    void updateRoadmapScore(Long studentId, Double completionPercentage);

    /** 10% input. Recomputes the total and grade, then publishes prs.updated fail-soft. */
    void updateResumeScore(Long studentId, Double atsScore);

    PrsResponse getMyPrs(Long studentId);

    PrsResponse getPrsByStudentId(Long callerId, String callerRole, Long targetStudentId);

    /**
     * SUPER_ADMIN gets a global ranking; ORG_ADMIN gets one scoped to callerOrgId; anyone else is
     * refused. callerOrgId is nullable and is ignored for a SUPER_ADMIN.
     */
    List<PrsLeaderboardEntry> getLeaderboard(String callerRole, Long callerOrgId);
}
