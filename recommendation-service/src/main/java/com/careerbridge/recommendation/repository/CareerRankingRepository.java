package com.careerbridge.recommendation.repository;

import com.careerbridge.recommendation.model.CareerRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerRankingRepository extends JpaRepository<CareerRanking, Long> {

    /**
     * Every ranking for a recommendation, best match first.
     *
     * One query serves both response lists: the service partitions the result on
     * isTopRecommendation, which keeps rank order inside each half for free. Separate
     * ...IsTopRecommendationTrue / ...False finders would cost a second round trip to rebuild
     * exactly this list.
     *
     * `Rank` here is the entity property; the physical column is quoted because RANK is reserved in
     * MySQL 8.0.2+. Spring Data parses the property name, so the two never need to agree.
     */
    List<CareerRanking> findByRecommendationIdOrderByRankAsc(Long recommendationId);
}
