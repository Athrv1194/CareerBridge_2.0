package com.careerbridge.recommendation.constants;

import com.careerbridge.recommendation.dto.CareerPathDto;

import java.util.List;

/**
 * The career paths this service ranks, and the body of GET /api/recommendation/careers.
 *
 * A second copy of assessment-service's career_paths seed
 * (assessment-service/src/main/resources/data.sql lines 2-9). That service needs a real table --
 * it stores topCareerPathId as an FK and resolves careers by id -- but this one never looks a
 * career up by id, so a table would add ddl-auto surface, a data.sql and the INSERT IGNORE
 * idempotency trap (SEV-2 in ai_incident_log.md) to buy nothing. A constant ships in the jar just
 * the same.
 *
 * AssessmentCompletedEvent carries only a single topCareerPath, not a map of all career scores, so
 * the full ranking has to be recomputed here. Everything needed is in the event: relevance depends
 * only on the category name.
 *
 * ORDER IS LOAD-BEARING. It mirrors the INSERT order in that data.sql, which is the order
 * assessment-service's findAll() returns and therefore the order its stable sort falls back to
 * when scores tie. Ranking here ties the same way (see RecommendationServiceImpl), so keeping
 * these in step is what makes both services name the same winner. Both files must be edited
 * together.
 */
public class CareerCatalog {

    public static final List<CareerPathDto> ALL = List.of(
            career("Full Stack Developer",
                    "Builds both frontend and backend of web applications",
                    "Programming,Database,Web Development"),
            career("Backend Developer",
                    "Specializes in server-side logic and APIs",
                    "Programming,Database,System Design"),
            career("Frontend Developer",
                    "Specializes in UI and user experience",
                    "Web Development,Programming"),
            career("Data Scientist",
                    "Analyzes data and builds ML models",
                    "Machine Learning,Programming,Database"),
            career("DevOps Engineer",
                    "Manages infrastructure, CI/CD, and deployments",
                    "Linux,Docker,Cloud,System Design"),
            career("Mobile Developer",
                    "Builds iOS and Android applications",
                    "Programming,Mobile Development"),
            career("System Design Engineer",
                    "Designs scalable distributed systems",
                    "System Design,Programming,Database"));

    private static CareerPathDto career(String name, String description, String requiredSkills) {
        return CareerPathDto.builder()
                .name(name)
                .description(description)
                .requiredSkills(requiredSkills)
                .build();
    }

    private CareerCatalog() {
    }
}
