package com.careerbridge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * firstName/lastName/email/role are the requester's, copied in at read time from the User row --
 * this table stores only userId, so an org admin reviewing a list of bare ids would have no way to
 * tell who any of them are without this join.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequestResponse {

    private Long id;

    private Long userId;

    private String firstName;

    private String lastName;

    private String email;

    private String role;

    private Long organizationId;

    private String status;

    private LocalDateTime requestedAt;

    private LocalDateTime reviewedAt;

    private Long reviewedByUserId;
}
