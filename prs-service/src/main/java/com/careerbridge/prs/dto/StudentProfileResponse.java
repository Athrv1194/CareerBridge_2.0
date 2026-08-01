package com.careerbridge.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A deliberately partial copy of student-service's StudentProfileResponse -- that class has 22
 * fields; this one takes the single field the Placement Readiness Score needs.
 *
 * Safe because Jackson 3 disables FAIL_ON_UNKNOWN_PROPERTIES by default (Boot 4 ships
 * tools.jackson), so the 21 fields not declared here are simply ignored rather than throwing. The
 * consequence is that a rename upstream does NOT fail loudly: profileCompletionPercentage would
 * bind to null, StudentServiceClient would return its sentinel, and every student's profileScore
 * would quietly freeze at its last value. The field name was verified byte-for-byte against
 * student-service/src/main/java/com/careerbridge/student/dto/StudentProfileResponse.java before
 * this was written; re-verify after any change there.
 *
 * Integer, not int -- an absent field must bind to null so the null guard in the client can catch
 * it, rather than silently becoming a real score of 0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse {

    private Integer profileCompletionPercentage;
}
