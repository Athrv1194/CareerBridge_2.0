package com.careerbridge.student.constants;

import java.util.List;

/**
 * Curated skill catalogue offered as autocomplete suggestions and used to decide whether an
 * incoming skill counts as predefined or must be flagged isCustom.
 *
 * A List, not a Set: getSkillSuggestions serves this straight to the UI, so insertion order and
 * original casing are the display order and label. Membership is checked case-insensitively over
 * ~47 entries, which is not worth a second normalized collection.
 */
public class SkillConstants {

    public static final List<String> PREDEFINED_SKILLS = List.of(
            "Java", "Python", "JavaScript", "TypeScript", "C", "C++", "C#",
            "Spring Boot", "React", "Angular", "Vue.js", "Node.js",
            "MySQL", "MongoDB", "PostgreSQL", "Redis",
            "Docker", "Kubernetes", "AWS", "Azure",
            "Git", "Linux", "REST API", "GraphQL",
            "Machine Learning", "Deep Learning", "Data Science",
            "HTML", "CSS", "Tailwind CSS", "Bootstrap",
            "Hibernate", "Maven", "Gradle", "Jenkins",
            "Microservices", "RabbitMQ", "Kafka",
            ".NET", "PHP", "Ruby", "Swift", "Kotlin",
            "Figma", "Postman", "Jira", "Agile/Scrum"
    );

    private SkillConstants() {
    }
}
