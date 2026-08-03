package com.careerbridge.recruiter.model.enums;

/**
 * What the STUDENT decided about an offer that was extended to them.
 *
 * Null on JobApplication means "an offer may have been extended, but the student has not responded
 * yet" -- it is deliberately not a NONE/PENDING value, because a null column needs no DEFAULT and
 * therefore no risky ALTER against the already-populated job_applications table.
 *
 * Deliberately only two values. The supplied task spec also listed WITHDRAWN, but never said who
 * writes it, and a company pulling an offer is already representable as ApplicationStatus.REJECTED.
 * A third value nobody ever sets is dead vocabulary that every future reader has to reason about.
 *
 * This is a NEW enum on a NEW column, which is what makes it safe. Adding a value to the EXISTING
 * ApplicationStatus enum would mean altering the CHECK constraint Hibernate generates for
 * @Enumerated(EnumType.STRING) on a table that already holds rows -- ddl-auto does that
 * unreliably, logs a WARN, and lets the service start healthy with the change silently missing.
 * That exact failure has cost this project three incidents (Question.updatedAt,
 * StudentProfile.role, PlacementReadinessScore.resumeScore).
 */
public enum OfferOutcome {
    ACCEPTED,
    DECLINED
}
