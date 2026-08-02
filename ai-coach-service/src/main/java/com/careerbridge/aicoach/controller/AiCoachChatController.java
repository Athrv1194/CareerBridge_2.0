package com.careerbridge.aicoach.controller;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.ChatSessionResponse;
import com.careerbridge.aicoach.dto.ChatSessionSummary;
import com.careerbridge.aicoach.dto.MessageReply;
import com.careerbridge.aicoach.dto.SendMessageRequest;
import com.careerbridge.aicoach.service.AiCoachChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-coach/sessions")
public class AiCoachChatController {

    private final AiCoachChatService chatService;

    public AiCoachChatController(AiCoachChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatSessionResponse> createSession(
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createSession(role, studentId));
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionSummary>> listSessions(
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(chatService.listSessions(role, studentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatSessionResponse> getSession(
            @PathVariable String id,
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(chatService.getSession(role, studentId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String id,
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role) {
        chatService.deleteSession(role, studentId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageReply> sendMessage(
            @PathVariable String id,
            @RequestHeader(AiCoachConstants.USER_ID_HEADER) Long studentId,
            @RequestHeader(AiCoachConstants.USER_ROLE_HEADER) String role,
            @RequestBody @Valid SendMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(role, studentId, id, request.getContent()));
    }
}
