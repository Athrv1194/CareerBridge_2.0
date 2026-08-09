package com.careerbridge.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Bytes plus content type for an avatar or project cover image read out of the database. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageBlob {
    private byte[] bytes;
    private String contentType;
}
