package com.careerbridge.aicoach.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Embedded on ChatSession, not a second collection -- persisting a user turn and its assistant
 * reply is then one atomic single-document update, which is what makes the Groq write-order rule
 * in AiCoachChatServiceImpl safe (nothing is ever half-written). 16MB document ceiling is tens of
 * thousands of turns; split to a separate collection only if that is ever approached.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String role;       // "user" or "assistant"
    private String content;
    private LocalDateTime timestamp;
}
