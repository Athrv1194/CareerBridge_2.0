package com.careerbridge.aicoach.controller;

import com.careerbridge.aicoach.dto.ChatSessionResponse;
import com.careerbridge.aicoach.dto.ChatSessionSummary;
import com.careerbridge.aicoach.dto.MessageReply;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.exception.GlobalExceptionHandler;
import com.careerbridge.aicoach.service.AiCoachChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real LocalValidatorFactoryBean wired in (needed from Step 9 onward for @Valid on
 * SendMessageRequest) so @NotBlank/@Size genuinely fire rather than being silently skipped by
 * standalone MockMvc's default no-op validator.
 */
@ExtendWith(MockitoExtension.class)
class AiCoachChatControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock
    private AiCoachChatService chatService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new AiCoachChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void createSession_Valid_Returns201() throws Exception {
        when(chatService.createSession("STUDENT", 1L)).thenReturn(
                ChatSessionResponse.builder().id("s1").careerPath("Backend Developer").messages(List.of()).build());

        mockMvc().perform(post("/api/ai-coach/sessions")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isCreated());
    }

    @Test
    void listSessions_Valid_Returns200() throws Exception {
        when(chatService.listSessions("STUDENT", 1L)).thenReturn(List.of(
                ChatSessionSummary.builder().id("s1").careerPath("X").build()));

        mockMvc().perform(get("/api/ai-coach/sessions")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void getSession_NotFound_Returns404() throws Exception {
        when(chatService.getSession("STUDENT", 1L, "missing"))
                .thenThrow(new CustomException("Session not found", HttpStatus.NOT_FOUND));

        mockMvc().perform(get("/api/ai-coach/sessions/missing")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSession_Valid_Returns204() throws Exception {
        mockMvc().perform(delete("/api/ai-coach/sessions/s1")
                        .header(USER_ID_HEADER, "1")
                        .header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isNoContent());
    }

    @Test
    void sendMessage_BlankContent_Returns400() throws Exception {
        mockMvc().perform(post("/api/ai-coach/sessions/s1/messages")
                        .header(USER_ID_HEADER, "1").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_ContentOverMaxLength_Returns400() throws Exception {
        String tooLong = "a".repeat(2001);
        mockMvc().perform(post("/api/ai-coach/sessions/s1/messages")
                        .header(USER_ID_HEADER, "1").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_MalformedJson_Returns400() throws Exception {
        mockMvc().perform(post("/api/ai-coach/sessions/s1/messages")
                        .header(USER_ID_HEADER, "1").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendMessage_Valid_Returns200WithAssistantReply() throws Exception {
        when(chatService.sendMessage("STUDENT", 1L, "s1", "hello")).thenReturn(
                MessageReply.builder().role("assistant").content("hi there").build());

        mockMvc().perform(post("/api/ai-coach/sessions/s1/messages")
                        .header(USER_ID_HEADER, "1").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk());
    }
}
