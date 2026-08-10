package com.careerbridge.mentor.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookSessionRequest {

    @NotNull(message = "Mentor profile id is required")
    private Long mentorProfileId;

    @NotBlank(message = "Topic is required")
    private String topic;

    /**
     * @Future rather than @FutureOrPresent: a session proposed for the current instant cannot be
     * attended, and the mentor still has to accept it first.
     */
    @NotNull(message = "Scheduled date and time is required")
    @Future(message = "Scheduled time must be in the future")
    private LocalDateTime scheduledAt;

    /** Optional; defaults to 30 minutes in the service when null. */
    private Integer durationMinutes;
}
