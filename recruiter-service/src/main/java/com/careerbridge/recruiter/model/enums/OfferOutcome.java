package com.careerbridge.recruiter.model.enums;

// Null on JobApplication = offer extended but student hasn't responded yet.
// Only two values -- WITHDRAWN was spec'd but never defined an owner; REJECTED covers company pullbacks.
public enum OfferOutcome {
    ACCEPTED,
    DECLINED
}
