package com.careerbridge.student.repository;

import com.careerbridge.student.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    Boolean existsByUserId(Long userId);

    /**
     * Candidate search source (recruiter-service). Only profiles the student opted to publish, and
     * only rows whose role is the one passed in.
     *
     * The role predicate is load-bearing, not decorative: auth-service publishes student.registered
     * for EVERY registration, so this table holds a profile for recruiters and admins as well.
     * Without it the recruiter candidate pool lists them as candidates. Do not "simplify" this back
     * to findByIsPublicTrue().
     *
     * Rows created before StudentProfile.role existed carry a null role and therefore never match.
     * They were backfilled from careerbridge_auth.users; any environment restored from an older
     * dump needs the same backfill or its candidate pool comes back empty.
     */
    List<StudentProfile> findByIsPublicTrueAndRole(String role);

    /**
     * Every student regardless of isPublic -- placement statistics count the whole cohort, not only
     * the students visible to recruiters. Deliberately distinct from findByIsPublicTrueAndRole
     * above, which backs the candidate pool and must keep honouring that flag.
     */
    List<StudentProfile> findByRole(String role);
}
