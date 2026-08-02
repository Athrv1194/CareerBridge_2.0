package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.ChatSessionResponse;
import com.careerbridge.aicoach.dto.ChatSessionSummary;
import com.careerbridge.aicoach.dto.MessageReply;

import java.util.List;

public interface AiCoachChatService {

    ChatSessionResponse createSession(String role, Long studentId);

    List<ChatSessionSummary> listSessions(String role, Long studentId);

    ChatSessionResponse getSession(String role, Long studentId, String sessionId);

    void deleteSession(String role, Long studentId, String sessionId);

    MessageReply sendMessage(String role, Long studentId, String sessionId, String content);
}
