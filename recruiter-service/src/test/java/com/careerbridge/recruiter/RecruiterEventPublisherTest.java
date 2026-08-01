package com.careerbridge.recruiter;

import com.careerbridge.recruiter.config.RabbitMQConfig;
import com.careerbridge.recruiter.messaging.RecruiterEventPublisher;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * The fail-soft contract lives entirely here: a broker outage during either publish must never
 * propagate to the caller, since the JobApplication row is already committed by the time either
 * method runs and rethrowing would report failure for work that actually happened.
 */
@ExtendWith(MockitoExtension.class)
class RecruiterEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private RecruiterEventPublisher publisher;

    @Test
    @DisplayName("publishApplicationSubmitted: a broker exception is swallowed, not rethrown")
    void publishApplicationSubmitted_BrokerDown_DoesNotThrow() {
        doThrow(new AmqpException("connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishApplicationSubmitted(1L, 2L, 3L));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.APPLICATION_SUBMITTED_ROUTING_KEY), any(Object.class));
    }

    @Test
    @DisplayName("publishApplicationStatusUpdated: a broker exception is swallowed, not rethrown")
    void publishApplicationStatusUpdated_BrokerDown_DoesNotThrow() {
        doThrow(new AmqpException("connection refused"))
                .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

        assertDoesNotThrow(() ->
                publisher.publishApplicationStatusUpdated(1L, 2L, 3L, ApplicationStatus.SHORTLISTED));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.APPLICATION_STATUS_UPDATED_ROUTING_KEY),
                any(Object.class));
    }
}
