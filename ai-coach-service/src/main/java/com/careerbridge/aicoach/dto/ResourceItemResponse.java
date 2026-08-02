package com.careerbridge.aicoach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItemResponse {

    private String title;
    private String url;
    private String type;
    private String platform;
}
