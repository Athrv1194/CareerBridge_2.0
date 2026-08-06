package com.careerbridge.organization.model;

/** PENDING is the only status data.sql-free rows start in -- @Builder.Default on OrganizationRequest.status. */
public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
