package com.careerbridge.recruiter;

import com.careerbridge.recruiter.controller.RecruiterApplicationController;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.exception.GlobalExceptionHandler;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.OfferOutcome;
import com.careerbridge.recruiter.service.ApplicationService;
import com.careerbridge.recruiter.service.CandidateSearchService;
import com.careerbridge.recruiter.service.InterviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The offer endpoints on RecruiterApplicationController. That controller had NO test class before
 * this patch -- only RecruiterJobController did -- so this covers the two endpoints added here
 * rather than retrofitting the pre-existing ones.
 *
 * Same wiring as RecruiterJobControllerTest: GlobalExceptionHandler registered explicitly
 * (standalone setup does not scan @RestControllerAdvice) and a real LocalValidatorFactoryBean so
 * @Valid genuinely fires -- without it the missing-CTC test would pass for the wrong reason.
 */
@ExtendWith(MockitoExtension.class)
class RecruiterOfferControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock private ApplicationService applicationService;
    @Mock private CandidateSearchService candidateSearchService;
    @Mock private InterviewService interviewService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new RecruiterApplicationController(
                        applicationService, candidateSearchService, interviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    private static JobApplicationResponse offered() {
        return JobApplicationResponse.builder()
                .id(5L).jobId(100L).studentId(42L)
                .status(ApplicationStatus.OFFERED)
                .offeredCtc(new BigDecimal("8.50"))
                .build();
    }

    @Test
    @DisplayName("PATCH /applications/{id}/offer returns 200 with the offer detail")
    void extendOffer_Returns200() throws Exception {
        when(applicationService.extendOffer(anyString(), anyLong(), anyLong(), any()))
                .thenReturn(offered());

        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "7").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredCtc\":8.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFERED"))
                .andExpect(jsonPath("$.offeredCtc").value(8.50));
    }

    @Test
    @DisplayName("extendOffer: a missing CTC is a 400 and never reaches the service")
    void extendOffer_MissingCtc_Returns400() throws Exception {
        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "7").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.offeredCtc").exists());

        verify(applicationService, never()).extendOffer(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("extendOffer: zero and negative CTC are both rejected")
    void extendOffer_NonPositiveCtc_Returns400() throws Exception {
        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "7").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredCtc\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "7").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredCtc\":-3.5}"))
                .andExpect(status().isBadRequest());

        verify(applicationService, never()).extendOffer(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("extendOffer: malformed JSON is a 400, not a 500")
    void extendOffer_MalformedJson_Returns400() throws Exception {
        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "7").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("extendOffer: a non-numeric X-User-Id is a 400, not a 500")
    void extendOffer_NonNumericUserId_Returns400() throws Exception {
        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "abc").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredCtc\":8.50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("extendOffer: the service's 403 surfaces as a 403")
    void extendOffer_ServiceForbids_Returns403() throws Exception {
        when(applicationService.extendOffer(anyString(), anyLong(), anyLong(), any()))
                .thenThrow(new CustomException("You do not own the job this application belongs to",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(patch("/api/recruiter/applications/5/offer")
                        .header(USER_ID_HEADER, "999").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredCtc\":8.50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /applications/{id}/offer/respond returns 200 with the outcome")
    void respondToOffer_Returns200() throws Exception {
        JobApplicationResponse accepted = offered();
        accepted.setOfferOutcome(OfferOutcome.ACCEPTED);
        when(applicationService.respondToOffer(anyString(), anyLong(), anyLong(), any()))
                .thenReturn(accepted);

        mockMvc().perform(patch("/api/recruiter/applications/5/offer/respond")
                        .header(USER_ID_HEADER, "42").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerOutcome").value("ACCEPTED"));
    }

    @Test
    @DisplayName("respondToOffer: a missing outcome is a 400")
    void respondToOffer_MissingOutcome_Returns400() throws Exception {
        mockMvc().perform(patch("/api/recruiter/applications/5/offer/respond")
                        .header(USER_ID_HEADER, "42").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.outcome").exists());

        verify(applicationService, never()).respondToOffer(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("respondToOffer: an unrecognised outcome is a 400 from deserialization, not a 500")
    void respondToOffer_UnknownOutcome_Returns400() throws Exception {
        // WITHDRAWN is deliberately not a value -- this proves an unknown one is rejected at the
        // edge rather than reaching the service as a null.
        mockMvc().perform(patch("/api/recruiter/applications/5/offer/respond")
                        .header(USER_ID_HEADER, "42").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"WITHDRAWN\"}"))
                .andExpect(status().isBadRequest());

        verify(applicationService, never()).respondToOffer(anyString(), anyLong(), anyLong(), any());
    }
}
