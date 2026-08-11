package com.careerbridge.mentor.repository;

import com.careerbridge.mentor.model.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {

    /**
     * An Optional finder is correct here, unlike the List finders in recommendation-service and
     * roadmap-service: uk_mentor_profile_user genuinely makes user_id unique, so this can never
     * match more than one row. The project rule is that a single-result finder needs a unique
     * constraint behind the queried column -- it has one.
     */
    Optional<MentorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Default browse: available mentors, best-rated first. */
    List<MentorProfile> findByIsAvailableTrueOrderByAverageRatingDesc();

    /**
     * LIKE %careerPath% against the comma-delimited column, so "Backend" also matches
     * "Backend Development". Substring matching is the intent for a discovery filter; see
     * MentorProfile.careerPaths for the trade this accepts.
     */
    List<MentorProfile> findByCareerPathsContainingIgnoreCaseAndIsAvailableTrue(String careerPath);

    List<MentorProfile> findByExpertiseAreasContainingIgnoreCaseAndIsAvailableTrue(String expertise);
}
