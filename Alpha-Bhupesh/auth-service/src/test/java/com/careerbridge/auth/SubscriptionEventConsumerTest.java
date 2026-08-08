package com.careerbridge.auth;

import com.careerbridge.auth.consumer.SubscriptionEventConsumer;
import com.careerbridge.auth.event.SubscriptionActivatedEvent;
import com.careerbridge.auth.service.SubscriptionSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionEventConsumerTest {

    @Mock
    private SubscriptionSyncService subscriptionSyncService;

    private SubscriptionEventConsumer consumer() {
        return new SubscriptionEventConsumer(subscriptionSyncService);
    }

    private static SubscriptionActivatedEvent event() {
        return SubscriptionActivatedEvent.builder()
                .userId(21L).planName("STUDENT_PREMIUM")
                .validUntil(LocalDateTime.now().plusDays(30)).build();
    }

    @Test
    void onSubscriptionActivated_ValidEvent_DelegatesToSyncService() {
        consumer().onSubscriptionActivated(event());

        verify(subscriptionSyncService, times(1)).applyActivation(any());
    }

    @Test
    void onSubscriptionActivated_NullPayload_LogsAndReturnsWithoutDelegating() {
        assertDoesNotThrow(() -> consumer().onSubscriptionActivated(null));

        verify(subscriptionSyncService, never()).applyActivation(any());
    }

    @Test
    void onSubscriptionActivated_ServiceThrows_DoesNotRethrow() {
        // Rethrowing would requeue the message and spin the listener forever on a payload this
        // service can never process.
        doThrow(new RuntimeException("boom")).when(subscriptionSyncService).applyActivation(any());

        assertDoesNotThrow(() -> consumer().onSubscriptionActivated(event()));
    }
}
