package com.careerbridge.payment.data;

import com.careerbridge.payment.model.SubscriptionPlan;
import com.careerbridge.payment.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDataSeederTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    private PlanDataSeeder seeder() {
        return new PlanDataSeeder(planRepository);
    }

    @Test
    void run_EmptyCatalog_SeedsAllFourPlans() {
        when(planRepository.existsByPlanName(anyString())).thenReturn(false);

        seeder().run();

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(planRepository, times(4)).save(captor.capture());

        List<String> names = captor.getAllValues().stream().map(SubscriptionPlan::getPlanName).toList();
        assertEquals(List.of("FREE", "STUDENT_PREMIUM", "COLLEGE_BASIC", "COLLEGE_PRO"), names);
    }

    @Test
    void run_FreePlanIsNamedFreeNotStudentFree() {
        // auth-service's User.subscriptionPlan has defaulted to the literal "FREE" since the
        // project started. The catalog must agree with the data that already exists.
        when(planRepository.existsByPlanName(anyString())).thenReturn(false);

        seeder().run();

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(planRepository, times(4)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(p -> "FREE".equals(p.getPlanName())));
        assertTrue(captor.getAllValues().stream().noneMatch(p -> "STUDENT_FREE".equals(p.getPlanName())));
    }

    @Test
    void run_PlanAlreadyPresent_DoesNotReseedIt() {
        when(planRepository.existsByPlanName(anyString())).thenReturn(false);
        when(planRepository.existsByPlanName("STUDENT_PREMIUM")).thenReturn(true);

        seeder().run();

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(planRepository, times(3)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(p -> "STUDENT_PREMIUM".equals(p.getPlanName())));
    }

    @Test
    void run_RunTwice_WritesNothingTheSecondTime() {
        when(planRepository.existsByPlanName(anyString())).thenReturn(true);

        seeder().run();

        verify(planRepository, never()).save(any());
    }

    @Test
    void run_ChecksExistencePerPlan_NeverGlobalCount() {
        // Pins the RoadmapDataSeeder anti-pattern: a global count() > 0 guard would silently seed
        // nothing on every existing deployment the day a fifth plan is added.
        when(planRepository.existsByPlanName(anyString())).thenReturn(false);

        seeder().run();

        verify(planRepository, times(1)).existsByPlanName(eq("FREE"));
        verify(planRepository, times(1)).existsByPlanName(eq("STUDENT_PREMIUM"));
        verify(planRepository, times(1)).existsByPlanName(eq("COLLEGE_BASIC"));
        verify(planRepository, times(1)).existsByPlanName(eq("COLLEGE_PRO"));
        verify(planRepository, never()).count();
    }

    @Test
    void run_FeaturesAreValidJsonArrays() {
        when(planRepository.existsByPlanName(anyString())).thenReturn(false);

        seeder().run();

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(planRepository, times(4)).save(captor.capture());
        for (SubscriptionPlan plan : captor.getAllValues()) {
            String features = plan.getFeatures();
            assertTrue(features.startsWith("[") && features.endsWith("]"),
                    "features must be a JSON array for " + plan.getPlanName() + " but was " + features);
        }
    }
}
