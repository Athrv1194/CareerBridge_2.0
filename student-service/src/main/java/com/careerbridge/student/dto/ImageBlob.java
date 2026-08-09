package com.careerbridge.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bytes plus content type for an avatar, project cover image, or certificate file read out of the
 * database. fileName is null for avatar/cover (they don't preserve an original name); set for a
 * certificate credential file so downloads keep the name the student uploaded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageBlob {
    private byte[] bytes;
    private String contentType;
    private String fileName;
}
