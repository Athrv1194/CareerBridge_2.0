package com.careerbridge.mentor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * action is a free String validated in the service rather than an enum bound by Jackson, so an
 * unrecognised value produces a 400 naming what was sent instead of an opaque deserialization
 * failure. Same reasoning as auth-service's ChangeRoleRequest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RespondToSessionRequest {

    /** ACCEPT or DECLINE, case-insensitive. */
    @NotBlank(message = "Action is required")
    private String action;

    /** Required when action is ACCEPT; enforced in the service, not by an annotation. */
    private String meetingLink;

    private String mentorNotes;
}
