package com.careerbridge.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The plan catalog. Four rows, seeded by PlanDataSeeder, displayed on the public pricing page.
 *
 * price is a bare BigDecimal with no precision/scale, matching recruiter-service's Job.salaryMin
 * and salaryMax -- Hibernate maps it to numeric(38,2) on PostgreSQL, which is exact. This is the
 * human-authored catalog value that gets rendered; the money that actually moves is stored as
 * integer paise on Payment.amountPaise, exactly as Razorpay holds it.
 */
@Entity
@Table(name = "subscription_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FREE, STUDENT_PREMIUM, COLLEGE_BASIC, COLLEGE_PRO.
     *
     * The free tier is named FREE, not STUDENT_FREE: auth-service's User.subscriptionPlan has
     * defaulted to the literal "FREE" since the project started, so every existing user already
     * carries that value. Renaming the catalog row costs one string in a table with no rows;
     * renaming the users would cost a backfill plus a Java default change. It is also the more
     * accurate name -- a recruiter or an admin has no subscription either, so the free tier was
     * never student-specific.
     */
    @Column(nullable = false, unique = true)
    private String planName;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Rupees. Zero for FREE, which is why createOrder refuses it rather than charging nothing. */
    @Column(nullable = false)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "INR";

    /** MONTHLY, SEMESTER, ANNUAL, LIFETIME. Drives the endDate arithmetic in PaymentServiceImpl. */
    @Column(nullable = false)
    private String billingCycle;

    /**
     * A JSON array string, parsed to List<String> by the response mapper.
     *
     * columnDefinition = "TEXT", never @Lob String: Hibernate's PostgreSQLDialect maps a bare
     * @Lob String to the oid large-object type, which rejects a plain string from both seed data
     * and a normal repository.save. Logged SEV-2, 7 fields across 2 services.
     */
    @Column(columnDefinition = "TEXT")
    private String features;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
