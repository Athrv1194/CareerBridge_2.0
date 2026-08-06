package com.careerbridge.auth;

import com.careerbridge.auth.event.OrganizationRequestApprovedEvent;
import com.careerbridge.auth.model.PasswordResetOtp;
import com.careerbridge.auth.model.Role;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.PasswordResetOtpRepository;
import com.careerbridge.auth.repository.UserRepository;
import com.careerbridge.auth.service.OrgAdminProvisioningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure Mockito -- no Spring context, no database, no broker. */
@ExtendWith(MockitoExtension.class)
class OrgAdminProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetOtpRepository passwordResetOtpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrgAdminProvisioningServiceImpl orgAdminProvisioningService;

    private OrganizationRequestApprovedEvent event;

    @BeforeEach
    void setUp() {
        event = OrganizationRequestApprovedEvent.builder()
                .requestId(1L)
                .organizationId(5L)
                .organizationName("COEP Technological University")
                .adminName("Prof. S. K. Sharma")
                .adminEmail("tpo@coep.ac.in")
                .approvedAt(LocalDateTime.now())
                .build();
    }

    private static User savedUser(Long id) {
        return User.builder()
                .id(id).email("tpo@coep.ac.in").firstName("Prof.").lastName("S. K. Sharma")
                .role(Role.ORG_ADMIN).organizationId(5L).isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("no existing user: a new ORG_ADMIN is created, and both events are published")
    void provision_NewUser_CreatesOrgAdminAndPublishesBothEvents() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.findByEmail("tpo@coep.ac.in")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser(10L));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.empty());

        orgAdminProvisioningService.provision(event);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.ORG_ADMIN, userCaptor.getValue().getRole());
        assertEquals(5L, userCaptor.getValue().getOrganizationId());

        verify(passwordResetOtpRepository).save(any(PasswordResetOtp.class));
        // organization.admin.invited AND student.registered -- a brand-new admin gets both.
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("an existing user is promoted to ORG_ADMIN rather than duplicated")
    void provision_ExistingUser_PromotedNotDuplicated() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User existing = User.builder()
                .id(20L).email("tpo@coep.ac.in").firstName("Sharma").lastName("")
                .role(Role.STUDENT).isDeleted(false)
                .build();
        when(userRepository.findByEmail("tpo@coep.ac.in")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(20L)).thenReturn(Optional.empty());

        orgAdminProvisioningService.provision(event);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        assertEquals(Role.ORG_ADMIN, userCaptor.getValue().getRole());
        assertEquals(5L, userCaptor.getValue().getOrganizationId());

        // Promotion only, not a fresh registration: no second student.registered publish.
        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("redelivery reuses the existing unexpired token instead of issuing a second one")
    void provision_RedeliveryWithUnexpiredToken_ReusesExistingToken() {
        when(userRepository.findByEmail("tpo@coep.ac.in")).thenReturn(Optional.of(savedUser(10L)));
        when(userRepository.save(any(User.class))).thenReturn(savedUser(10L));

        PasswordResetOtp existingOtp = PasswordResetOtp.builder()
                .id(1L).userId(10L).otpHash("hashed").resetToken("existing-token")
                .used(false).expiresAt(LocalDateTime.now().plusHours(20))
                .build();
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.of(existingOtp));

        orgAdminProvisioningService.provision(event);

        // No new token issued -- the redelivery must not leave two working links in the inbox.
        verify(passwordResetOtpRepository, never()).save(any());
    }

    @Test
    @DisplayName("an expired prior token is not reused: a fresh one is issued")
    void provision_ExpiredPriorToken_IssuesFreshToken() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.findByEmail("tpo@coep.ac.in")).thenReturn(Optional.of(savedUser(10L)));
        when(userRepository.save(any(User.class))).thenReturn(savedUser(10L));

        PasswordResetOtp expiredOtp = PasswordResetOtp.builder()
                .id(1L).userId(10L).otpHash("hashed").resetToken("old-token")
                .used(false).expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(10L))
                .thenReturn(Optional.of(expiredOtp));

        orgAdminProvisioningService.provision(event);

        verify(passwordResetOtpRepository).save(any(PasswordResetOtp.class));
    }

    @Test
    @DisplayName("a broker outage does not fail provisioning: the user is still committed")
    void provision_BrokerDown_StillCommitsUser() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.findByEmail("tpo@coep.ac.in")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser(10L));
        when(passwordResetOtpRepository.findTopByUserIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.empty());
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> orgAdminProvisioningService.provision(event));

        verify(userRepository).save(any(User.class));
    }
}
