package com.careerbridge.auth;

import com.careerbridge.auth.consumer.OrganizationEventConsumer;
import com.careerbridge.auth.event.OrganizationRequestApprovedEvent;
import com.careerbridge.auth.service.OrgAdminProvisioningService;
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

/** Mirrors SubscriptionEventConsumerTest's conventions exactly. */
@ExtendWith(MockitoExtension.class)
class OrganizationEventConsumerTest {

    @Mock
    private OrgAdminProvisioningService orgAdminProvisioningService;

    private OrganizationEventConsumer consumer() {
        return new OrganizationEventConsumer(orgAdminProvisioningService);
    }

    private static OrganizationRequestApprovedEvent event() {
        return OrganizationRequestApprovedEvent.builder()
                .requestId(1L).organizationId(5L).organizationName("COEP")
                .adminName("Prof. S. K. Sharma").adminEmail("tpo@coep.ac.in")
                .approvedAt(LocalDateTime.now()).build();
    }

    @Test
    void onOrganizationRequestApproved_ValidEvent_DelegatesToProvisioningService() {
        consumer().onOrganizationRequestApproved(event());

        verify(orgAdminProvisioningService, times(1)).provision(any());
    }

    @Test
    void onOrganizationRequestApproved_NullPayload_LogsAndReturnsWithoutDelegating() {
        assertDoesNotThrow(() -> consumer().onOrganizationRequestApproved(null));

        verify(orgAdminProvisioningService, never()).provision(any());
    }

    @Test
    void onOrganizationRequestApproved_IncompletePayload_DoesNotDelegate() {
        assertDoesNotThrow(() -> consumer().onOrganizationRequestApproved(
                OrganizationRequestApprovedEvent.builder().requestId(1L).build()));

        verify(orgAdminProvisioningService, never()).provision(any());
    }

    @Test
    void onOrganizationRequestApproved_ServiceThrows_DoesNotRethrow() {
        // Rethrowing would requeue the message and spin the listener forever on a payload this
        // service can never process.
        doThrow(new RuntimeException("boom")).when(orgAdminProvisioningService).provision(any());

        assertDoesNotThrow(() -> consumer().onOrganizationRequestApproved(event()));
    }
}
