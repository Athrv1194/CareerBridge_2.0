package com.careerbridge.organization.service;

import com.careerbridge.organization.config.RabbitMQConfig;
import com.careerbridge.organization.dto.CreateDepartmentRequest;
import com.careerbridge.organization.dto.CreateOrganizationRequest;
import com.careerbridge.organization.dto.DepartmentResponse;
import com.careerbridge.organization.dto.OrganizationResponse;
import com.careerbridge.organization.dto.UpdateOrganizationRequest;
import com.careerbridge.organization.event.OrganizationCreatedEvent;
import com.careerbridge.organization.exception.CustomException;
import com.careerbridge.organization.model.Department;
import com.careerbridge.organization.model.Organization;
import com.careerbridge.organization.repository.DepartmentRepository;
import com.careerbridge.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationServiceImpl.class);

    /** Matches auth-service's Role enum. Strings, because that is what arrives in X-User-Role. */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";

    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final RabbitTemplate rabbitTemplate;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                   DepartmentRepository departmentRepository,
                                   RabbitTemplate rabbitTemplate) {
        this.organizationRepository = organizationRepository;
        this.departmentRepository = departmentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, String callerRole) {
        requireSuperAdmin(callerRole);

        if (organizationRepository.existsByNameIgnoreCase(request.getName())) {
            throw new CustomException("An organization with this name already exists", HttpStatus.CONFLICT);
        }

        Organization saved = organizationRepository.save(Organization.builder()
                .name(request.getName())
                .type(request.getType())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .build());

        publishCreated(saved);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations(String callerRole) {
        // Listing every tenant is a SUPER_ADMIN capability by definition; an ORG_ADMIN asking for
        // "all" gets 403 rather than a one-element list, because silently narrowing the result
        // would make the endpoint mean two different things depending on who called it.
        requireSuperAdmin(callerRole);

        return organizationRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(Long id, String callerRole, Long callerOrgId) {
        requireCanAccessOrganization(id, callerRole, callerOrgId);

        return toResponse(findActiveOrThrow(id));
    }

    @Override
    @Transactional
    public OrganizationResponse updateOrganization(Long id, UpdateOrganizationRequest request,
                                                   String callerRole, Long callerOrgId) {
        requireCanAccessOrganization(id, callerRole, callerOrgId);

        Organization organization = findActiveOrThrow(id);

        // Null means "leave as is". A partial update that overwrote unset fields with null would
        // silently wipe an address every time a caller renamed an organization.
        if (request.getName() != null) {
            organization.setName(request.getName());
        }
        if (request.getType() != null) {
            organization.setType(request.getType());
        }
        if (request.getContactEmail() != null) {
            organization.setContactEmail(request.getContactEmail());
        }
        if (request.getContactPhone() != null) {
            organization.setContactPhone(request.getContactPhone());
        }
        if (request.getAddress() != null) {
            organization.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            organization.setCity(request.getCity());
        }
        if (request.getState() != null) {
            organization.setState(request.getState());
        }

        return toResponse(organizationRepository.save(organization));
    }

    @Override
    @Transactional
    public void deactivateOrganization(Long id, String callerRole) {
        // SUPER_ADMIN only, with no ORG_ADMIN branch: letting a tenant delete itself would strand
        // every student, assessment and recommendation carrying its organization id.
        requireSuperAdmin(callerRole);

        Organization organization = findActiveOrThrow(id);
        organization.setIsActive(false);
        organizationRepository.save(organization);
    }

    @Override
    @Transactional
    public DepartmentResponse createDepartment(Long organizationId, CreateDepartmentRequest request,
                                               String callerRole, Long callerOrgId) {
        requireCanAccessOrganization(organizationId, callerRole, callerOrgId);

        Organization organization = findActiveOrThrow(organizationId);

        if (departmentRepository.existsByNameIgnoreCaseAndOrganizationId(request.getName(), organizationId)) {
            throw new CustomException("A department with this name already exists in this organization",
                    HttpStatus.CONFLICT);
        }

        Department saved = departmentRepository.save(Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .organization(organization)
                .build());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getDepartmentsByOrg(Long organizationId, String callerRole,
                                                        Long callerOrgId) {
        requireMemberOrSuperAdmin(organizationId, callerRole, callerOrgId);

        // Confirms the organization exists and is active before returning an empty list, so a
        // caller can tell "no departments" apart from "no such organization".
        findActiveOrThrow(organizationId);

        return departmentRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------------------------------

    private void requireSuperAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN may perform this operation", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * SUPER_ADMIN reaches any organization; ORG_ADMIN reaches only its own. Every other role, and
     * an absent role, is refused.
     *
     * Objects.equals, not ==: both sides are Long objects well outside the Integer cache, so
     * reference comparison would be false for the very ids this is meant to allow. A null
     * callerOrgId can never match, which is the correct outcome for an ORG_ADMIN whose token
     * carries no organization.
     */
    private void requireCanAccessOrganization(Long organizationId, String callerRole, Long callerOrgId) {
        if (ROLE_SUPER_ADMIN.equals(callerRole)) {
            return;
        }
        if (ROLE_ORG_ADMIN.equals(callerRole) && Objects.equals(callerOrgId, organizationId)) {
            return;
        }
        throw new CustomException("You do not have access to this organization", HttpStatus.FORBIDDEN);
    }

    /**
     * Deliberately looser than requireCanAccessOrganization, and only for this one read. Listing
     * department NAMES is what lets a student self-select theirs on the Profile page (auth-service's
     * assignOwnDepartment) -- restricting it to ORG_ADMIN/SUPER_ADMIN, as every other organization
     * endpoint does, would make that impossible for the exact audience it exists for. Any role whose
     * own organizationId matches the one being read may see its department list; SUPER_ADMIN, as
     * everywhere else, reaches every organization.
     */
    private void requireMemberOrSuperAdmin(Long organizationId, String callerRole, Long callerOrgId) {
        if (ROLE_SUPER_ADMIN.equals(callerRole)) {
            return;
        }
        if (Objects.equals(callerOrgId, organizationId)) {
            return;
        }
        throw new CustomException("You do not have access to this organization", HttpStatus.FORBIDDEN);
    }

    private Organization findActiveOrThrow(Long id) {
        return organizationRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new CustomException("Organization not found", HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------------------------------------
    // Events and mapping
    // ---------------------------------------------------------------------------------------------

    /**
     * Fail-soft. A broker outage must not fail organization creation: the row is already committed
     * and the caller's request succeeded, so rethrowing here would report a failure for work that
     * actually happened, and a retry would then hit the duplicate-name check.
     */
    private void publishCreated(Organization organization) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_CREATED_ROUTING_KEY,
                    OrganizationCreatedEvent.builder()
                            .organizationId(organization.getId())
                            .name(organization.getName())
                            .type(organization.getType())
                            .contactEmail(organization.getContactEmail())
                            .createdAt(organization.getCreatedAt())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for organizationId={}",
                    RabbitMQConfig.ORGANIZATION_CREATED_ROUTING_KEY, organization.getId(), ex);
        }
    }

    private OrganizationResponse toResponse(Organization organization) {
        // Reads the LAZY departments collection while the transaction is still open. Moving this
        // mapping outside the service would throw LazyInitializationException during serialisation.
        List<DepartmentResponse> departments = organization.getDepartments() == null
                ? List.of()
                : organization.getDepartments().stream()
                        .filter(Department::getIsActive)
                        .map(this::toResponse)
                        .toList();

        return OrganizationResponse.builder()
                .id(organization.getId())
                .name(organization.getName())
                .type(organization.getType())
                .contactEmail(organization.getContactEmail())
                .contactPhone(organization.getContactPhone())
                .address(organization.getAddress())
                .city(organization.getCity())
                .state(organization.getState())
                .isActive(organization.getIsActive())
                .createdAt(organization.getCreatedAt())
                .departments(departments)
                .build();
    }

    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .isActive(department.getIsActive())
                .organizationId(department.getOrganization().getId())
                .createdAt(department.getCreatedAt())
                .build();
    }
}
