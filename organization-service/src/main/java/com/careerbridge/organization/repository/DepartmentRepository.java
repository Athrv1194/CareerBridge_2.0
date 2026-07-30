package com.careerbridge.organization.repository;

import com.careerbridge.organization.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByOrganizationIdAndIsActiveTrueOrderByNameAsc(Long organizationId);

    /**
     * Organization-scoped by id, not a bare findById. Looking a department up by id alone would let
     * an ORG_ADMIN who guessed an id read another tenant's row; carrying the organization id into
     * the query makes cross-tenant access a miss rather than a hit.
     */
    Optional<Department> findByIdAndOrganizationId(Long id, Long organizationId);

    /** Uniqueness is per organization, not global: two colleges may both have a "Computer Science". */
    boolean existsByNameIgnoreCaseAndOrganizationId(String name, Long organizationId);
}
