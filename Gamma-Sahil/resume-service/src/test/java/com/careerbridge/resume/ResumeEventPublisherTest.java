package com.careerbridge.resume;

import com.careerbridge.resume.config.RabbitMQConfig;
import com.careerbridge.resume.event.ResumeGeneratedEvent;
import com.careerbridge.resume.messaging.ResumeEventPublisher;
import com.careerbridge.resume.model.StudentResume;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * This is where the fail-soft contract actually lives, so this is where it is tested. Mocking the
 * publisher inside ResumeServiceTest and making it throw would only prove the mock throws.
 */
@ExtendWith(MockitoExtension.class)
class ResumeEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private ResumeEventPublisher publisher;

    private static StudentResume resume() {
        return StudentResume.builder()
                .id(500L).studentId(42L)
                .fileName("resume_42_v2.pdf")
                .version(2).atsScore(72.5).isDefault(true)
                .generatedAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .build();
    }

    @Test
    @DisplayName("publish: sends to careerbridge.exchange with the resume.generated routing key")
    void publish_SendsCorrectExchangeAndRoutingKey() {
        publisher.publishResumeGenerated(resume());

        ArgumentCaptor<ResumeGeneratedEvent> event =
                ArgumentCaptor.forClass(ResumeGeneratedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY),
                event.capture());

        // Both consumers key off these: prs-service needs studentId + atsScore, student-service
        // needs studentId + resumeId to build the download URL.
        assertEquals(500L, event.getValue().getResumeId());
        assertEquals(42L, event.getValue().getStudentId());
        assertEquals(72.5, event.getValue().getAtsScore());
        assertEquals(2, event.getValue().getVersion());
    }

    /**
     * The resume row and its PDF bytes are already committed by the time this runs. Rethrowing
     * would report failure for work that actually happened, and the student would retry -- landing
     * a second identical resume at version+1.
     */
    @Test
    @DisplayName("publish: a broker outage is swallowed, never rethrown to the caller")
    void publish_BrokerDown_DoesNotThrow() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishResumeGenerated(resume()));
    }
}
