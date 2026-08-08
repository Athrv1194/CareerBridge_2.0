package com.careerbridge.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response element for GET /api/assessment/categories. Not part of the user's spec, but needed
 *  because dto/ is kept separate from model/ by project convention, and the controller exposes
 *  this endpoint even though the service spec only listed startAttempt/submitAttempt/getResult. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {

    private Long id;

    private String name;

    private String description;
}
