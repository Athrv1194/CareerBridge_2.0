package com.careerbridge.organization;

import com.careerbridge.organization.dto.OrgRequestResponse;
import com.careerbridge.organization.dto.RejectOrgRequest;
import com.careerbridge.organization.dto.SubmitOrgRequest;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.model.Organization;
import com.careerbridge.organization.model.OrganizationRequest;
import com.careerbridge.organization.model.RequestStatus;
import com.careerbridge.organization.repository.OrganizationRepository;
import com.careerbridge.organization.repository.OrganizationRequestRepository;
import com.careerbridge.organization.service.OrganizationRequestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure Mockito -- no Spring context, no database, no broker. Mirrors OrganizationServiceTest's conventions. */
@ExtendWith(MockitoExtension.class)
class OrganizationRequestServiceTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ORG_ADMIN = "ORG_ADMIN";

    @Mock
    private OrganizationRequestRepository organizationRequestRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrganizationRequestServiceImpl organizationRequestService;

    private SubmitOrgRequest submitRequest;

    @BeforeEach
    void setUp() {
        submitRequest = new SubmitOrgRequest();
        submitRequest.setInstitutionName("COEP Technological University");
        submitRequest.setInstitutionCode("COEP");
        submitRequest.setContactPersonName("Prof. S. K. Sharma");
        submitRequest.setContactEmail("tpo@coep.ac.in");
        submitRequest.setContactPhone("+91 9876543210");
        submitRequest.setOrganizationType("ENGINEERING_COLLEGE");
    }

    private static OrganizationRequest pendingRequest(Long id) {
        return OrganizationRequest.builder()
                .id(id)
                .institutionName("COEP Technological University")
                .institutionCode("COEP")
                .contactPersonName("Prof. S. K. Sharma")
                .contactEmail("tpo@coep.ac.in")
                .contactPhone("+91 9876543210")
                .organizationType("ENGINEERING_COLLEGE")
                .status(RequestStatus.PENDING)
                .build();
    }

    private static Organization organization(Long id, String name) {
        return Organization.builder()
                .id(id)
                .name(name)
                .type("ENGINEERING_COLLEGE")
                .isActive(true)
                .departments(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("a public submit is accepted and starts PENDING")
    void submit_Success_ReturnsPending() {
        when(organizationRequestRepository.existsByInstitutionCodeIgnoreCase("COEP")).thenReturn(false);
        when(organizationRequestRepository.existsByInstitutionNameIgnoreCaseAndStatus(
                "COEP Technological University", RequestStatus.PENDING)).thenReturn(false);
        when(organizationRequestRepository.save(any(OrganizationRequest.class)))
                .thenReturn(pendingRequest(1L));

        OrgRequestResponse response = organizationRequestService.submit(submitRequest);

        assertEquals(1L, response.getId());
        assertEquals(RequestStatus.PENDING, response.getStatus());
    }

    @Test
    @DisplayName("a duplicate institution code is a 409, and nothing is saved")
    void submit_DuplicateCode_Throws409() {
        when(organizationRequestRepository.existsByInstitutionCodeIgnoreCase("COEP")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> organizationRequestService.submit(submitRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organizationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second application for a still-PENDING institution name is a 409")
    void submit_DuplicatePendingName_Throws409() {
        when(organizationRequestRepository.existsByInstitutionCodeIgnoreCase("COEP")).thenReturn(false);
        when(organizationRequestRepository.existsByInstitutionNameIgnoreCaseAndStatus(
                "COEP Technological University", RequestStatus.PENDING)).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> organizationRequestService.submit(submitRequest));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organizationRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve creates the Organization and publishes both events")
    void approve_Pending_CreatesOrganizationAndPublishesBothEvents() {
        OrganizationRequest request = pendingRequest(1L);
        when(organizationRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(organizationRepository.existsByNameIgnoreCase("COEP Technological University")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class)))
                .thenReturn(organization(5L, "COEP Technological University"));
        when(organizationRequestRepository.save(any(OrganizationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrgRequestResponse response = organizationRequestService.approve(1L, SUPER_ADMIN, 99L);

        assertEquals(RequestStatus.APPROVED, response.getStatus());
        assertEquals(5L, response.getCreatedOrganizationId());
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("approving an already-reviewed request is a 409, and no Organization is created")
    void approve_NotPending_Throws409() {
        OrganizationRequest approved = pendingRequest(1L);
        approved.setStatus(RequestStatus.APPROVED);
        when(organizationRequestRepository.findById(1L)).thenReturn(Optional.of(approved));

        CustomException ex = assertThrows(CustomException.class,
                () -> organizationRequestService.approve(1L, SUPER_ADMIN, 99L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a non-SUPER_ADMIN cannot approve, and the repository is never touched")
    void approve_NonSuperAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> organizationRequestService.approve(1L, ORG_ADMIN, 99L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(organizationRequestRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("a non-SUPER_ADMIN cannot list requests, and the repository is never touched")
    void list_NonSuperAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> organizationRequestService.list(null, ORG_ADMIN));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(organizationRequestRepository, never()).findAllByOrderByRequestedAtDesc();
        verify(organizationRequestRepository, never()).findByStatusOrderByRequestedAtDesc(any());
    }

    @Test
    @DisplayName("reject records the reason and moves the request to REJECTED")
    void reject_Pending_SetsRejectedWithReason() {
        OrganizationRequest request = pendingRequest(1L);
        when(organizationRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(organizationRequestRepository.save(any(OrganizationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RejectOrgRequest rejectRequest = new RejectOrgRequest();
        rejectRequest.setReason("Invalid accreditation documents");

        OrgRequestResponse response = organizationRequestService.reject(1L, rejectRequest, SUPER_ADMIN, 99L);

        assertEquals(RequestStatus.REJECTED, response.getStatus());
        assertEquals("Invalid accreditation documents", response.getRejectionReason());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a broker outage does not fail approval: both publishes are fail-soft")
    void approve_BrokerDown_StillSucceeds() {
        OrganizationRequest request = pendingRequest(1L);
        when(organizationRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(organizationRepository.existsByNameIgnoreCase("COEP Technological University")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class)))
                .thenReturn(organization(5L, "COEP Technological University"));
        when(organizationRequestRepository.save(any(OrganizationRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        OrgRequestResponse response = organizationRequestService.approve(1L, SUPER_ADMIN, 99L);

        assertEquals(RequestStatus.APPROVED, response.getStatus());
    }
}
