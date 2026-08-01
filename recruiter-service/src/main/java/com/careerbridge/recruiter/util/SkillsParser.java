package com.careerbridge.recruiter.util;

import java.util.Arrays;
import java.util.List;

/**
 * Jobs store requiredSkills as one comma-separated TEXT column rather than a child table: nothing
 * in this schema references a skill by id, and a join table would add a repository and a migration
 * for a field that is only ever read as a whole list.
 *
 * Shared because two call sites parse the same format -- JobServiceImpl reading the stored column,
 * and CandidateSearchServiceImpl reading the ?skills= query parameter.
 */
public final class SkillsParser {

    private SkillsParser() {
    }

    /** Null or blank yields an empty list, never null. Blank entries between commas are dropped. */
    public static List<String> parse(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }

        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
