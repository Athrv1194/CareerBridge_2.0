package com.careerbridge.auth;

import com.careerbridge.auth.event.SubscriptionActivatedEvent;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.UserRepository;
import com.careerbridge.auth.service.SubscriptionSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    private SubscriptionSyncService service() {
        return new SubscriptionSyncService(userRepository);
    }

    private static User user() {
        return User.builder()
                .id(21L).email("s@test.com").firstName("S").lastName("T")
                .role(Role.STUDENT).subscriptionPlan("FREE").isDeleted(false)
                .build();
    }

    private static SubscriptionActivatedEvent event(LocalDateTime validUntil) {
        return SubscriptionActivatedEvent.builder()
                .userId(21L).planName("STUDENT_PREMIUM")
                .validUntil(validUntil).activatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Copies both the plan and the absolute expiry onto the User row")
    void applyActivation_SetsPlanAndExpiry() {
        LocalDateTime validUntil = LocalDateTime.now().plusDays(30);
        when(userRepository.findByIdAndIsDeletedFalse(21L)).thenReturn(Optional.of(user()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().applyActivation(event(validUntil));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("STUDENT_PREMIUM", captor.getValue().getSubscriptionPlan());
        assertEquals(validUntil, captor.getValue().getSubscriptionExpiry());
    }

    @Test
    @DisplayName("A redelivery SETS the same expiry, it never adds to the existing one")
    void applyActivation_Redelivery_ExpiryIsSetNotAccumulated() {
        // RabbitMQ is at-least-once. If this ever computed expiry.plusDays(n) instead of assigning
        // the event's absolute timestamp, one payment would silently buy two months.
        LocalDateTime validUntil = LocalDateTime.now().plusDays(30);
        User existing = user();
        existing.setSubscriptionPlan("STUDENT_PREMIUM");
        existing.setSubscriptionExpiry(validUntil);

        when(userRepository.findByIdAndIsDeletedFalse(21L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().applyActivation(event(validUntil));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(validUntil, captor.getValue().getSubscriptionExpiry(),
                "a redelivered event must leave the expiry exactly where the publisher put it");
    }

    @Test
    void applyActivation_UnknownUser_DoesNotThrowAndSavesNothing() {
        when(userRepository.findByIdAndIsDeletedFalse(21L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service().applyActivation(event(LocalDateTime.now().plusDays(30))));
        verify(userRepository, never()).save(any());
    }

    @Test
    void applyActivation_SoftDeletedUser_IsNotUpdated() {
        // findByIdAndIsDeletedFalse returns empty for a soft-deleted account, so the plan is not
        // applied to someone whose account is gone.
        when(userRepository.findByIdAndIsDeletedFalse(21L)).thenReturn(Optional.empty());

        service().applyActivation(event(LocalDateTime.now().plusDays(30)));

        verify(userRepository, never()).save(any());
    }

    @Test
    void applyActivation_NullEventOrUserId_DoesNotThrowAndNeverQueries() {
        assertDoesNotThrow(() -> service().applyActivation(null));
        assertDoesNotThrow(() -> service().applyActivation(
                SubscriptionActivatedEvent.builder().planName("STUDENT_PREMIUM").build()));

        verify(userRepository, never()).findByIdAndIsDeletedFalse(anyLong());
        verify(userRepository, never()).save(any());
    }
}
