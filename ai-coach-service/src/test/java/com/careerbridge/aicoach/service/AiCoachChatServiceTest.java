package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.ChatSessionResponse;
import com.careerbridge.aicoach.dto.ChatSessionSummary;
import com.careerbridge.aicoach.dto.MessageReply;
import com.careerbridge.aicoach.dto.client.PrsResponseDto;
import com.careerbridge.aicoach.dto.client.RoadmapResponseDto;
import com.careerbridge.aicoach.dto.client.SkillDto;
import com.careerbridge.aicoach.dto.client.StudentProfileDto;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.model.ChatMessage;
import com.careerbridge.aicoach.model.ChatSession;
import com.careerbridge.aicoach.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCoachChatServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private RoadmapServiceClient roadmapServiceClient;

    @Mock
    private StudentServiceClient studentServiceClient;

    @Mock
    private PrsServiceClient prsServiceClient;

    @Mock
    private GroqClient groqClient;

    private AiCoachChatServiceImpl service() {
        return new AiCoachChatServiceImpl(chatSessionRepository, roadmapServiceClient,
                studentServiceClient, prsServiceClient, groqClient);
    }

    @Test
    void createSession_Student_PersistsWithStudentIdAndEmptyMessages() {
        when(roadmapServiceClient.fetchMyRoadmap(1L))
                .thenReturn(RoadmapResponseDto.builder().careerName("Backend Developer").build());
        when(chatSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().createSession("STUDENT", 1L);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getStudentId());
        assertTrue(captor.getValue().getMessages().isEmpty());
        assertEquals("Backend Developer", captor.getValue().getCareerPath());
    }

    @Test
    void createSession_NoRoadmap_CareerPathDefaultsGracefully() {
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(null);
        when(chatSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().createSession("STUDENT", 1L);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        assertEquals("Software Developer", captor.getValue().getCareerPath());
    }

    @Test
    void createSession_RoadmapServiceDown_CareerPathDefaultsGracefully() {
        when(roadmapServiceClient.fetchMyRoadmap(1L))
                .thenThrow(new CustomException("down", HttpStatus.SERVICE_UNAVAILABLE));
        when(chatSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChatSessionResponse response = service().createSession("STUDENT", 1L);

        assertEquals("Software Developer", response.getCareerPath());
    }

    @Test
    void listSessions_OnlyOwnSessionsReturned() {
        when(chatSessionRepository.findByStudentIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(
                ChatSession.builder().id("a").studentId(1L).careerPath("X").build()));

        List<ChatSessionSummary> result = service().listSessions("STUDENT", 1L);

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getId());
    }

    @Test
    void getSession_OtherStudentsSession_Throws404Not403() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 2L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service().getSession("STUDENT", 2L, "s1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void deleteSession_OtherStudentsSession_Throws404AndDeletesNothing() {
        when(chatSessionRepository.deleteByIdAndStudentId("s1", 2L)).thenReturn(0L);

        CustomException ex = assertThrows(CustomException.class,
                () -> service().deleteSession("STUDENT", 2L, "s1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void deleteSession_Own_PassesBothIdAndStudentIdToRepository() {
        when(chatSessionRepository.deleteByIdAndStudentId("s1", 1L)).thenReturn(1L);

        service().deleteSession("STUDENT", 1L, "s1");

        verify(chatSessionRepository, times(1)).deleteByIdAndStudentId(eq("s1"), eq(1L));
    }

    private static ChatSession sessionWithHistory(int messageCount) {
        List<ChatMessage> history = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            history.add(ChatMessage.builder().role(i % 2 == 0 ? "user" : "assistant")
                    .content("msg" + i).timestamp(LocalDateTime.now()).build());
        }
        return ChatSession.builder().id("s1").studentId(1L).careerPath("Backend Developer")
                .messages(history).build();
    }

    @Test
    void sendMessage_GroqFails_PersistsNothing() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 1L)).thenReturn(Optional.of(sessionWithHistory(0)));
        when(groqClient.chat(any())).thenThrow(new CustomException("down", HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(CustomException.class, () -> service().sendMessage("STUDENT", 1L, "s1", "hello"));

        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    void sendMessage_GroqFails_Throws503Not500() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 1L)).thenReturn(Optional.of(sessionWithHistory(0)));
        when(groqClient.chat(any())).thenThrow(new CustomException("down", HttpStatus.SERVICE_UNAVAILABLE));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().sendMessage("STUDENT", 1L, "s1", "hello"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void sendMessage_Success_PersistsUserAndAssistantInOneSave() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 1L)).thenReturn(Optional.of(sessionWithHistory(0)));
        when(groqClient.chat(any())).thenReturn("Here is my advice");

        MessageReply reply = service().sendMessage("STUDENT", 1L, "s1", "What next?");

        assertEquals("assistant", reply.getRole());
        assertEquals("Here is my advice", reply.getContent());

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository, times(1)).save(captor.capture());
        List<ChatMessage> saved = captor.getValue().getMessages();
        assertEquals(2, saved.size());
        assertEquals("user", saved.get(0).getRole());
        assertEquals("What next?", saved.get(0).getContent());
        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("Here is my advice", saved.get(1).getContent());
    }

    @Test
    void sendMessage_HistoryOverMax_SendsOnlyLastTwenty() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 1L)).thenReturn(Optional.of(sessionWithHistory(25)));
        when(groqClient.chat(any())).thenReturn("reply");

        service().sendMessage("STUDENT", 1L, "s1", "new message");

        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(groqClient).chat(captor.capture());
        // system prompt (1) + last 20 of 25 history + new user message (1) = 22
        assertEquals(22, captor.getValue().size());
        assertEquals("msg5", captor.getValue().get(1).get("content")); // oldest kept: index 5 of 0..24
    }

    @Test
    void sendMessage_HistoryUnderMax_SendsAll() {
        when(chatSessionRepository.findByIdAndStudentId("s1", 1L)).thenReturn(Optional.of(sessionWithHistory(5)));
        when(groqClient.chat(any())).thenReturn("reply");

        service().sendMessage("STUDENT", 1L, "s1", "new message");

        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(groqClient).chat(captor.capture());
        // system prompt (1) + all 5 history + new user message (1) = 7
        assertEquals(7, captor.getValue().size());
    }

    @Test
    void sendMessage_UnknownSession_Throws404() {
        when(chatSessionRepository.findByIdAndStudentId("missing", 1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service().sendMessage("STUDENT", 1L, "missing", "hi"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(groqClient, never()).chat(any());
    }

    @Test
    void buildSystemPrompt_AllUpstreamsDown_StillProducesPrompt() {
        when(studentServiceClient.fetchMyProfile(1L)).thenReturn(null);
        when(prsServiceClient.fetchMyPrs(1L)).thenReturn(null);

        String prompt = service().buildSystemPrompt(sessionWithHistory(0), 1L);

        assertTrue(prompt.contains("unknown"));
        assertTrue(prompt.contains("not available"));
        assertTrue(prompt.contains("not listed yet"));
        assertFalse(prompt.isBlank());
    }

    @Test
    void buildSystemPrompt_SkillNameWithNewlines_IsFlattened() {
        when(studentServiceClient.fetchMyProfile(1L)).thenReturn(StudentProfileDto.builder()
                .firstName("A").lastName("B")
                .skills(List.of(SkillDto.builder().skillName("Java\r\nSpring").build()))
                .build());
        when(prsServiceClient.fetchMyPrs(1L)).thenReturn(null);

        String prompt = service().buildSystemPrompt(sessionWithHistory(0), 1L);

        assertFalse(prompt.contains("Java\r\nSpring"));
        assertTrue(prompt.contains("Java Spring"));
    }

    @Test
    void buildSystemPrompt_ManySkills_TruncatedToCap() {
        List<SkillDto> twentySkills = IntStream.range(0, 20)
                .mapToObj(i -> SkillDto.builder().skillName("Skill" + i).build())
                .collect(Collectors.toList());
        when(studentServiceClient.fetchMyProfile(1L)).thenReturn(StudentProfileDto.builder()
                .firstName("A").lastName("B").skills(twentySkills).build());
        when(prsServiceClient.fetchMyPrs(1L)).thenReturn(null);

        String prompt = service().buildSystemPrompt(sessionWithHistory(0), 1L);

        assertTrue(prompt.contains("Skill14"));
        assertFalse(prompt.contains("Skill15"));
    }
}
