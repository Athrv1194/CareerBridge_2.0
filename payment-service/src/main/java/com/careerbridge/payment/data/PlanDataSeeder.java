package com.careerbridge.payment.data;

import com.careerbridge.payment.model.SubscriptionPlan;
import com.careerbridge.payment.repository.SubscriptionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the four-row plan catalog.
 *
 * A CommandLineRunner rather than a data.sql, matching roadmap-service's RoadmapDataSeeder: a
 * runner executes after the context is refreshed, so ddl-auto has finished, it needs no
 * defer-datasource-initialization, and it sidesteps the INSERT ... ON CONFLICT DO NOTHING
 * idempotency trap that cost assessment-service two SEV-2s.
 *
 * Idempotency is per plan (existsByPlanName), NEVER a global count() > 0. With a count check,
 * adding a fifth plan later would silently seed nothing on every existing deployment, because the
 * table is no longer empty -- a failure that looks exactly like the seeder working. This is the
 * specific anti-pattern RoadmapDataSeeder's javadoc warns about, and the supplied build prompt
 * specified it.
 *
 * Deliberately not @Transactional: each save is a single statement and already transactional
 * inside SimpleJpaRepository.save. Wrapping the whole run would hold one transaction open across
 * four independent inserts for no gain.
 */
@Component
public class PlanDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanDataSeeder.class);

    /** Free tier. Named FREE, not STUDENT_FREE -- see the comment on SubscriptionPlan.planName. */
    public static final String PLAN_FREE = "FREE";
    public static final String PLAN_STUDENT_PREMIUM = "STUDENT_PREMIUM";
    public static final String PLAN_COLLEGE_BASIC = "COLLEGE_BASIC";
    public static final String PLAN_COLLEGE_PRO = "COLLEGE_PRO";

    public static final String CYCLE_MONTHLY = "MONTHLY";
    public static final String CYCLE_SEMESTER = "SEMESTER";
    public static final String CYCLE_ANNUAL = "ANNUAL";
    public static final String CYCLE_LIFETIME = "LIFETIME";

    private final SubscriptionPlanRepository planRepository;

    public PlanDataSeeder(SubscriptionPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(String... args) {
        seed(SubscriptionPlan.builder()
                .planName(PLAN_FREE)
                .description("Basic CareerBridge access. Every account starts here.")
                .price(BigDecimal.ZERO)
                .billingCycle(CYCLE_LIFETIME)
                .features(toJsonArray("Assessment", "Basic Profile", "Job Browse", "Notifications"))
                .build());

        seed(SubscriptionPlan.builder()
                .planName(PLAN_STUDENT_PREMIUM)
                .description("Full CareerBridge experience with AI-powered features")
                .price(new BigDecimal("199.00"))
                .billingCycle(CYCLE_MONTHLY)
                .features(toJsonArray("AI Career Coach", "Resume Builder", "Unlimited Roadmaps",
                        "Priority Support"))
                .build());

        seed(SubscriptionPlan.builder()
                .planName(PLAN_COLLEGE_BASIC)
                .description("Institution plan for up to 100 students")
                .price(new BigDecimal("4999.00"))
                .billingCycle(CYCLE_SEMESTER)
                .features(toJsonArray("Up to 100 students", "Admin Dashboard", "Placement Analytics"))
                .build());

        seed(SubscriptionPlan.builder()
                .planName(PLAN_COLLEGE_PRO)
                .description("Institution plan with unlimited students")
                .price(new BigDecimal("9999.00"))
                .billingCycle(CYCLE_SEMESTER)
                .features(toJsonArray("Unlimited students", "Admin Dashboard", "Placement Analytics",
                        "Priority Support", "Custom Branding"))
                .build());
    }

    private void seed(SubscriptionPlan plan) {
        if (planRepository.existsByPlanName(plan.getPlanName())) {
            log.debug("Subscription plan '{}' already present, skipping", plan.getPlanName());
            return;
        }
        planRepository.save(plan);
        log.info("Seeded subscription plan '{}' at {} {}",
                plan.getPlanName(), plan.getCurrency(), plan.getPrice());
    }

    /**
     * Builds the JSON array by hand rather than pulling in an ObjectMapper: the inputs are four
     * hardcoded literal lists with no quotes or backslashes in them, so escaping is not a real
     * concern, and the alternative is injecting a mapper into a seeder that runs once.
     */
    private static String toJsonArray(String... features) {
        return List.of(features).stream()
                .map(f -> "\"" + f + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");
    }
}
