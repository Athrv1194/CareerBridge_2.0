package com.careerbridge.organization.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published to careerbridge.exchange with routing key organization.request.approved.
 *
 * Consumed by auth-service, which provisions the ORG_ADMIN user this event describes. Carries the
 * admin's contact details directly rather than requiring a follow-up lookup -- there is no
 * organization-service endpoint that returns them keyed by organizationId, and this is the only
 * place they exist outside the request row itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRequestApprovedEvent {

    private Long requestId;

    private Long organizationId;

    private String organizationName;

    private String organizationCode;

    private String adminName;

    private String adminEmail;

    private String adminPhone;

    private LocalDateTime approvedAt;
}
