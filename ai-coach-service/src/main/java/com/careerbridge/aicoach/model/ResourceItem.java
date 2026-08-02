package com.careerbridge.aicoach.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded, not a document of its own -- a resource has no existence outside its milestone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceItem {

    private String title;
    private String url;
    private String type;       // "article", "video", "course", "documentation", "tutorial"
    private String platform;   // "GeeksforGeeks", "YouTube", "Baeldung", "freeCodeCamp", etc.
}
