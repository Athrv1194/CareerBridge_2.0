package com.careerbridge.organization.repository;

import com.careerbridge.organization.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findByIsActiveTrueOrderByNameAsc();

    /** Deactivated organizations read as absent, so a soft-deleted tenant 404s rather than 200s. */
    Optional<Organization> findByIdAndIsActiveTrue(Long id);

    /**
     * Case-insensitive on purpose: "VIT Pune" and "vit pune" are the same college, and letting both
     * exist would silently split one tenant's students across two organization ids.
     */
    boolean existsByNameIgnoreCase(String name);
}
