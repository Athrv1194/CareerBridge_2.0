package com.careerbridge.payment.repository;

import com.careerbridge.payment.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    List<SubscriptionPlan> findByIsActiveTrueOrderByPriceAsc();

    /**
     * Optional is legal here because planName carries a real UNIQUE constraint. Also the seeder's
     * idempotency guard, via existsByPlanName below.
     */
    Optional<SubscriptionPlan> findByPlanName(String planName);

    /**
     * PlanDataSeeder's guard. Deliberately per plan rather than a global count() > 0: with a count
     * check, adding a fifth plan later would silently seed nothing on every existing deployment,
     * because the table is no longer empty -- a failure indistinguishable from the seeder working.
     * Same rule and same wording as roadmap-service's existsByCareerNameIgnoreCase.
     */
    boolean existsByPlanName(String planName);
}
