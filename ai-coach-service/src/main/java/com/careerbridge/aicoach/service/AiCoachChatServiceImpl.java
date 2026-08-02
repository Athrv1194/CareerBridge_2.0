package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import com.careerbridge.aicoach.dto.ChatMessageResponse;
import com.careerbridge.aicoach.dto.ChatSessionResponse;
import com.careerbridge.aicoach.dto.ChatSessionSummary;
import com.careerbridge.aicoach.dto.MessageReply;
import com.careerbridge.aicoach.dto.client.PrsResponseDto;
import com.careerbridge.aicoach.dto.client.RoadmapResponseDto;
import com.careerbridge.aicoach.dto.client.StudentProfileDto;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.model.ChatMessage;
import com.careerbridge.aicoach.model.ChatSession;
import com.careerbridge.aicoach.repository.ChatSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiCoachChatServiceImpl implements AiCoachChatService {

    private static final String DEFAULT_CAREER_PATH = "Software Developer";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_SYSTEM = "system";

    private final ChatSessionRepository chatSessionRepository;
    private final RoadmapServiceClient roadmapServiceClient;
    private final StudentServiceClient studentServiceClient;
    private final PrsServiceClient prsServiceClient;
    private final GroqClient groqClient;

    public AiCoachChatServiceImpl(ChatSessionRepository chatSessionRepository,
                                   RoadmapServiceClient roadmapServiceClient,
                                   StudentServiceClient studentServiceClient,
                                   PrsServiceClient prsServiceClient,
                                   GroqClient groqClient) {
        this.chatSessionRepository = chatSessionRepository;
        this.roadmapServiceClient = roadmapServiceClient;
        this.studentServiceClient = studentServiceClient;
        this.prsServiceClient = prsServiceClient;
        this.groqClient = groqClient;
    }

    @Override
    public ChatSessionResponse createSession(String role, Long studentId) {
        requireStudent(role);

        String careerPath = DEFAULT_CAREER_PATH;
        try {
            RoadmapResponseDto roadmap = roadmapServiceClient.fetchMyRoadmap(studentId);
            if (roadmap != null && roadmap.getCareerName() != null) {
                careerPath = roadmap.getCareerName();
            }
        } catch (CustomException e) {
            // roadmap-service being down must not block starting a chat session -- fall back to
            // the default career path rather than propagating a 503 from an unrelated dependency.
        }

        LocalDateTime now = LocalDateTime.now();
        ChatSession saved = chatSessionRepository.save(ChatSession.builder()
                .studentId(studentId)
                .careerPath(careerPath)
                .messages(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .build());

        return toResponse(saved);
    }

    @Override
    public List<ChatSessionSummary> listSessions(String role, Long studentId) {
        requireStudent(role);

        return chatSessionRepository.findByStudentIdOrderByUpdatedAtDesc(studentId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public ChatSessionResponse getSession(String role, Long studentId, String sessionId) {
        requireStudent(role);
        ChatSession session = loadOwnedSession(studentId, sessionId);
        return toResponse(session);
    }

    @Override
    public void deleteSession(String role, Long studentId, String sessionId) {
        requireStudent(role);

        // Wrong owner and not-found collapse to the same 404 in one round trip (R6) -- a student
        // has no legitimate reason to address a session id that is not theirs, so 403 would confirm
        // existence. Same shape as resume-service's resume lookups.
        long deleted = chatSessionRepository.deleteByIdAndStudentId(sessionId, studentId);
        if (deleted == 0) {
            throw new CustomException("Session not found", HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Write order is the whole correctness rule here (R3): build the prompt, call Groq, and ONLY
     * on success append and persist both turns in one save. The state that must never occur is a
     * persisted user turn with no assistant reply -- it is unanswerable, it re-enters the 20-message
     * history on every subsequent request, and it silently doubles token cost forever. On any Groq
     * failure nothing is written; the client retries with the same text. Same reasoning as
     * notification-service's documented Mongo-then-email-then-Postgres write ordering.
     */
    @Override
    public MessageReply sendMessage(String role, Long studentId, String sessionId, String content) {
        requireStudent(role);
        ChatSession session = loadOwnedSession(studentId, sessionId);

        List<Map<String, String>> groqMessages = buildGroqMessages(session, studentId, content);
        String replyContent = groqClient.chat(groqMessages);

        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> messages = new ArrayList<>(session.getMessages() == null ? List.of() : session.getMessages());
        messages.add(ChatMessage.builder().role(ROLE_USER).content(content).timestamp(now).build());
        messages.add(ChatMessage.builder().role(ROLE_ASSISTANT).content(replyContent).timestamp(now).build());
        session.setMessages(messages);
        session.setUpdatedAt(now);
        chatSessionRepository.save(session);

        return MessageReply.builder().role(ROLE_ASSISTANT).content(replyContent).timestamp(now).build();
    }

    private List<Map<String, String>> buildGroqMessages(ChatSession session, Long studentId, String newUserContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(chatEntry(ROLE_SYSTEM, buildSystemPrompt(session, studentId)));

        List<ChatMessage> history = session.getMessages() == null ? List.of() : session.getMessages();
        int start = Math.max(0, history.size() - AiCoachConstants.MAX_HISTORY);
        for (ChatMessage m : history.subList(start, history.size())) {
            messages.add(chatEntry(m.getRole(), m.getContent()));
        }

        messages.add(chatEntry(ROLE_USER, newUserContent));
        return messages;
    }

    private Map<String, String> chatEntry(String role, String content) {
        Map<String, String> entry = new HashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        return entry;
    }

    /**
     * Never throws -- both upstream clients are fail-soft (null on failure), so a downed
     * student-service or prs-service degrades the prompt rather than failing the chat. Skills are
     * capped and newline-stripped before interpolation: a student can name a skill
     * "Ignore previous instructions", and this is the minimum honest guard against that, not a
     * claim of full prompt-injection immunity -- see the plan's accepted-gaps section.
     */
    String buildSystemPrompt(ChatSession session, Long studentId) {
        StudentProfileDto profile = studentServiceClient.fetchMyProfile(studentId);
        PrsResponseDto prs = prsServiceClient.fetchMyPrs(studentId);

        String firstName = flatten(profile != null ? profile.getFirstName() : null, "there");
        String lastName = flatten(profile != null ? profile.getLastName() : null, "");
        String skills = profile != null && profile.getSkills() != null
                ? profile.getSkills().stream()
                        .filter(s -> s != null && s.getSkillName() != null)
                        .map(s -> flatten(s.getSkillName(), null))
                        .filter(s -> s != null && !s.isBlank())
                        .limit(AiCoachConstants.MAX_SKILLS_IN_PROMPT)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("not listed yet")
                : "not listed yet";
        String profileCompletion = profile != null && profile.getProfileCompletionPercentage() != null
                ? profile.getProfileCompletionPercentage() + "%"
                : "unknown";
        String assessmentScore = prs != null && prs.getAssessmentScore() != null
                ? prs.getAssessmentScore() + "%"
                : "not available";
        String prsScore = prs != null && prs.getTotalScore() != null
                ? prs.getTotalScore() + "%"
                : "not available";

        return """
                You are an AI Career Coach for CareerBridge, helping engineering college students \
                with their career journey. Be specific, practical, and concise (2-4 paragraphs max).

                Student profile:
                - Name: %s %s
                - Recommended career path: %s
                - Current skills: %s
                - Assessment score: %s
                - Profile completion: %s
                - Placement readiness score: %s

                Give advice tailored to their specific career path and scores. Suggest concrete next \
                steps. Be encouraging but realistic.
                """.formatted(firstName, lastName, session.getCareerPath(), skills,
                assessmentScore, profileCompletion, prsScore);
    }

    /** Strips \r\n from a student-controlled field before it enters the prompt, or returns fallback. */
    private String flatten(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    /** Package-private so AiCoachChatServiceImpl's sendMessage can reuse the same 404 shape. */
    ChatSession loadOwnedSession(Long studentId, String sessionId) {
        return chatSessionRepository.findByIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new CustomException("Session not found", HttpStatus.NOT_FOUND));
    }

    private void requireStudent(String role) {
        if (!AiCoachConstants.ROLE_STUDENT.equals(role)) {
            throw new CustomException("Only STUDENT may manage their own coach sessions", HttpStatus.FORBIDDEN);
        }
    }

    private ChatSessionSummary toSummary(ChatSession session) {
        return ChatSessionSummary.builder()
                .id(session.getId())
                .careerPath(session.getCareerPath())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    ChatSessionResponse toResponse(ChatSession session) {
        List<ChatMessageResponse> messages = session.getMessages() == null
                ? List.of()
                : session.getMessages().stream()
                        .map(m -> ChatMessageResponse.builder()
                                .role(m.getRole())
                                .content(m.getContent())
                                .timestamp(m.getTimestamp())
                                .build())
                        .toList();

        return ChatSessionResponse.builder()
                .id(session.getId())
                .careerPath(session.getCareerPath())
                .messages(messages)
                .createdAt(session.getCreatedAt())
                .build();
    }
}
