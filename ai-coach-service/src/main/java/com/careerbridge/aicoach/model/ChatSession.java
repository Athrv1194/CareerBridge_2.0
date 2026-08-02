package com.careerbridge.aicoach.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    private String id;

    private Long studentId;
    private String careerPath;

    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
