package com.careerbridge.roadmap.repository;

import com.careerbridge.roadmap.model.RoadmapTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoadmapTemplateRepository extends JpaRepository<RoadmapTemplate, Long> {

    /**
     * The lookup the consumer makes with the event's topCareerName. IgnoreCase because the two
     * catalogues are maintained by hand in separate services; it is not fuzzy, so a genuine rename
     * on either side still misses and the consumer logs a warning rather than guessing.
     */
    Optional<RoadmapTemplate> findByCareerNameIgnoreCaseAndIsActiveTrue(String careerName);

    List<RoadmapTemplate> findByIsActiveTrueOrderByCareerNameAsc();

    /**
     * The seeder's guard. Deliberately per-career rather than a global count() == 0: with a count
     * check, adding an eighth career later would silently seed nothing, because the table is no
     * longer empty. Not filtered on isActive -- a deactivated template still occupies the name.
     */
    boolean existsByCareerNameIgnoreCase(String careerName);
}
