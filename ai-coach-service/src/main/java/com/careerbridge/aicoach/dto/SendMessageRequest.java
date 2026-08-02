package com.careerbridge.aicoach.dto;

import com.careerbridge.aicoach.constants.AiCoachConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Size(max=2000) is the whole guard against burning the Groq quota on one pasted document -- the
 * minimum honest mitigation for a student-controlled field entering the model's context, alongside
 * MAX_HISTORY capping per-request token cost.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = AiCoachConstants.MAX_MESSAGE_LENGTH, message = "Message content is too long")
    private String content;
}
