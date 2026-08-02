package com.careerbridge.aicoach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** The assistant's reply to POST /sessions/{id}/messages. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReply {

    private String role;
    private String content;
    private LocalDateTime timestamp;
}
