package com.careerbridge.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Deliberately has NO weight field -- option weights must never reach the client. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionDto {

    private Long optionId;

    private String optionText;

    private Integer orderIndex;
}
