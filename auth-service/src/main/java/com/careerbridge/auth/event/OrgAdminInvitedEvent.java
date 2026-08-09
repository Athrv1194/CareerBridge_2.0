package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published after an ORG_ADMIN user is provisioned from an approved institution application.
 * Consumed by notification-service, which is the only thing that turns resetToken into an emailed
 * link -- auth-service never sends mail directly, same convention as PasswordResetRequestedEvent.
 *
 * resetToken is the SAME opaque credential stored in PasswordResetOtp.resetToken: activation is
 * deliberately implemented as a pre-filled password reset, so the link this event drives the TPO
 * toward posts to the already-public POST /api/auth/forgot-password/reset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAdminInvitedEvent {

    private String email;
    private String firstName;
    private String organizationName;
    private String resetToken;
    private int expiresInHours;
}
