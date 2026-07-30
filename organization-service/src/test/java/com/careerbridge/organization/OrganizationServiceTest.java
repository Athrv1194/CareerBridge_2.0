package com.careerbridge.organization;

import com.careerbridge.organization.dto.CreateDepartmentRequest;
import com.careerbridge.organization.dto.CreateOrganizationRequest;
import com.careerbridge.organization.dto.OrganizationResponse;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.model.Organization;
import com.careerbridge.organization.repository.DepartmentRepository;
import com.careerbridge.organization.repository.OrganizationRepository;
import com.careerbridge.organization.service.OrganizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito -- no Spring context, no database, no broker.
 *
 * The authorization tests are the point of this class: every one of them asserts a 403 for a caller
 * who must not reach the operation, because the gateway forwards X-User-Role verbatim and this
 * service is the only thing standing between an ORG_ADMIN and another tenant's data.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ORG_ADMIN = "ORG_ADMIN";

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private CreateOrganizationRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = new CreateOrganizationRequest();
        createRequest.setName("VIT Pune");
        createRequest.setType("ENGINEERING_COLLEGE");
        createRequest.setContactEmail("admin@vitpune.edu");
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
    @DisplayName("SUPER_ADMIN creates an organization and the created event is published")
    void createOrganization_SuperAdmin_Success() {
        when(organizationRepository.existsByNameIgnoreCase("VIT Pune")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization(1L, "VIT Pune"));

        OrganizationResponse response = organizationService.createOrganization(createRequest, SUPER_ADMIN);

        assertEquals(1L, response.getId());
        assertEquals("VIT Pune", response.getName());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("duplicate name is a 409, matched case-insensitively")
    void createOrganization_DuplicateName_Throws409() {
        when(organizationRepository.existsByNameIgnoreCase("VIT Pune")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> organizationService.createOrganization(createRequest, SUPER_ADMIN));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("a non-SUPER_ADMIN cannot create an organization, and nothing is written or published")
    void createOrganization_NonSuperAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> organizationService.createOrganization(createRequest, ORG_ADMIN));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // The role check must run before any repository work: a rejected caller should not even
        // cause a duplicate-name lookup, let alone a save.
        verify(organizationRepository, never()).existsByNameIgnoreCase(anyString());
        verify(organizationRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("ORG_ADMIN reads its own organization")
    void getOrganizationById_OrgAdmin_OwnOrg_Success() {
        when(organizationRepository.findByIdAndIsActiveTrue(5L))
                .thenReturn(Optional.of(organization(5L, "COEP")));

        OrganizationResponse response = organizationService.getOrganizationById(5L, ORG_ADMIN, 5L);

        assertEquals(5L, response.getId());
        assertEquals("COEP", response.getName());
    }

    @Test
    @DisplayName("ORG_ADMIN is refused another tenant's organization, without it being loaded")
    void getOrganizationById_OrgAdmin_OtherOrg_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> organizationService.getOrganizationById(9L, ORG_ADMIN, 5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // 403 before the lookup, not 404 after it: probing ids must not reveal which exist.
        verify(organizationRepository, never()).findByIdAndIsActiveTrue(anyLong());
    }

    @Test
    @DisplayName("duplicate department name within one organization is a 409")
    void createDepartment_DuplicateName_Throws409() {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Computer Science");

        when(organizationRepository.findByIdAndIsActiveTrue(5L))
                .thenReturn(Optional.of(organization(5L, "COEP")));
        when(departmentRepository.existsByNameIgnoreCaseAndOrganizationId("Computer Science", 5L))
                .thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
                () -> organizationService.createDepartment(5L, request, ORG_ADMIN, 5L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate is a soft delete: isActive flips to false, the row survives")
    void deactivateOrganization_SetsIsActiveFalse() {
        Organization existing = organization(5L, "COEP");
        when(organizationRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(existing));
        when(organizationRepository.save(any(Organization.class))).thenReturn(existing);

        organizationService.deactivateOrganization(5L, SUPER_ADMIN);

        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertFalse(saved.getValue().getIsActive());
        verify(organizationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a broker outage does not fail creation: the publish is fail-soft")
    void createOrganization_BrokerDown_StillSucceeds() {
        // The row is already committed by the time the event is published, so rethrowing here would
        // report a failure for work that actually happened -- and the retry would then hit the
        // duplicate-name check and fail for a second, more confusing reason.
        when(organizationRepository.existsByNameIgnoreCase("VIT Pune")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization(1L, "VIT Pune"));
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        OrganizationResponse response = organizationService.createOrganization(createRequest, SUPER_ADMIN);

        assertEquals(1L, response.getId());
    }
}
