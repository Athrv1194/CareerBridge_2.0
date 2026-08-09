package com.careerbridge.assessment.constants;

/**
 * The 3 fixed sections every assessment walks through, in order. The client only ever knows about
 * these -- never the real Category row backing a section (see AssessmentConstants.SECTION_CATEGORY_POOL).
 */
public enum AssessmentSection {
    APTITUDE("Aptitude", 5),
    DOMAIN_KNOWLEDGE("Domain Knowledge", 10),
    SOFT_SKILLS("Soft Skills", 5);

    private final String displayName;
    private final int targetSize;

    AssessmentSection(String displayName, int targetSize) {
        this.displayName = displayName;
        this.targetSize = targetSize;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTargetSize() {
        return targetSize;
    }
}
