package com.careerbridge.roadmap.data;

import com.careerbridge.roadmap.model.MilestoneTemplate;
import com.careerbridge.roadmap.model.RoadmapTemplate;
import com.careerbridge.roadmap.repository.RoadmapTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds one roadmap template per career on startup.
 *
 * A CommandLineRunner rather than a data.sql, deliberately. data.sql would need
 * spring.jpa.defer-datasource-initialization plus a unique key on every seeded table to make
 * INSERT ... ON CONFLICT DO NOTHING genuinely idempotent -- both of which have already cost this
 * project incidents (SEV-2 twice in assessment-service). A runner executes after the context is
 * refreshed, so ddl-auto has finished, and it can express "seed this career only if absent" in
 * plain Java.
 *
 * Idempotency is per career, NOT a global count() == 0 check. With a count check, adding an eighth
 * career later would silently seed nothing on every existing deployment, because the table is no
 * longer empty -- a failure that looks exactly like the seeder working.
 *
 * THE SEVEN careerName VALUES BELOW MUST MATCH recommendation-service's
 * constants/CareerCatalog.java EXACTLY. That service names the career in the
 * recommendation.generated payload, and RoadmapServiceImpl looks the template up by that name. A
 * rename on either side means every affected student silently gets no roadmap: the lookup misses,
 * the consumer logs a warning, and nothing errors. Both files must be edited together.
 *
 * totalMilestones and estimatedWeeks are computed from the milestone list rather than typed by
 * hand, so they cannot drift when a milestone is added or its duration changed.
 */
@Component
public class RoadmapDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoadmapDataSeeder.class);

    private static final int DAYS_PER_WEEK = 7;

    private final RoadmapTemplateRepository roadmapTemplateRepository;

    public RoadmapDataSeeder(RoadmapTemplateRepository roadmapTemplateRepository) {
        this.roadmapTemplateRepository = roadmapTemplateRepository;
    }

    /** One milestone's data. orderIndex is not carried here -- it is the position in the list. */
    private record Step(String title, String description, int estimatedDays, String resourceUrl) {
    }

    @Override
    public void run(String... args) {
        seedFullStackDeveloper();
        seedBackendDeveloper();
        seedFrontendDeveloper();
        seedDataScientist();
        seedDevOpsEngineer();
        seedMobileDeveloper();
        seedSystemDesignEngineer();
    }

    private void seedFullStackDeveloper() {
        seed("Full Stack Developer",
                "Full Stack Developer Roadmap",
                "Work up from core Java to a deployed application you own end to end, backend and frontend.",
                List.of(
                        new Step("Master Core Java and OOP",
                                "Classes, interfaces, collections, generics and exception handling -- the base every later step assumes.",
                                14, "https://dev.java/learn/"),
                        new Step("Learn Spring Boot Fundamentals",
                                "Auto-configuration, dependency injection, application properties and the starter model.",
                                14, "https://spring.io/guides"),
                        new Step("Build Your First REST API",
                                "Controllers, request and response DTOs, validation and proper status codes.",
                                7, "https://spring.io/guides/gs/rest-service"),
                        new Step("Persist Data with Spring Data JPA",
                                "Entities, repositories, relationships and transactions against PostgreSQL.",
                                10, "https://spring.io/projects/spring-data-jpa"),
                        new Step("Learn React Frontend Basics",
                                "Components, props, hooks and how a single-page app is structured.",
                                14, "https://react.dev/learn"),
                        new Step("Connect Frontend to Backend",
                                "Call your own API from the browser and handle loading, error and empty states.",
                                7, "https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API"),
                        new Step("Deploy to AWS EC2",
                                "Package the app, run it on a real instance and expose it over the public internet.",
                                7, "https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html")));
    }

    private void seedBackendDeveloper() {
        seed("Backend Developer",
                "Backend Developer Roadmap",
                "Go deep on server-side engineering: data modelling, API design, messaging and testing.",
                List.of(
                        new Step("Master Core Java and OOP",
                                "Collections, generics, concurrency basics and the exception model.",
                                14, "https://dev.java/learn/"),
                        new Step("Relational Databases and SQL",
                                "Joins, indexes, normalisation and reading a query plan before you blame the ORM.",
                                10, "https://www.postgresql.org/docs/current/tutorial.html"),
                        new Step("Spring Boot and Dependency Injection",
                                "Bean lifecycle, configuration properties, profiles and auto-configuration.",
                                14, "https://docs.spring.io/spring-boot/index.html"),
                        new Step("Design and Document REST APIs",
                                "Resource modelling, status codes, versioning and an OpenAPI spec others can read.",
                                10, "https://swagger.io/docs/specification/v3_0/about/"),
                        new Step("Spring Data JPA and Transactions",
                                "Repositories, lazy loading, the N+1 problem and transaction boundaries.",
                                10, "https://spring.io/projects/spring-data-jpa"),
                        new Step("Message Queues and Async Processing",
                                "Exchanges, queues, routing keys and why consumers must be idempotent.",
                                10, "https://www.rabbitmq.com/tutorials"),
                        new Step("Unit and Integration Testing",
                                "JUnit 5, Mockito and knowing which of the two a given behaviour needs.",
                                7, "https://junit.org/junit5/docs/current/user-guide/")));
    }

    private void seedFrontendDeveloper() {
        seed("Frontend Developer",
                "Frontend Developer Roadmap",
                "Build interfaces that are fast, accessible and hold up on a real device, not just your laptop.",
                List.of(
                        new Step("HTML, CSS and Responsive Layout",
                                "Semantic markup, flexbox, grid and designing for the small screen first.",
                                10, "https://web.dev/learn/css"),
                        new Step("Modern JavaScript",
                                "ES6+ syntax, promises, async/await, modules and the event loop.",
                                14, "https://developer.mozilla.org/en-US/docs/Web/JavaScript"),
                        new Step("TypeScript Essentials",
                                "Types, interfaces, generics and catching a whole class of bug before runtime.",
                                10, "https://www.typescriptlang.org/docs/"),
                        new Step("React Components and Hooks",
                                "Composition, state, effects and when a re-render is your own fault.",
                                14, "https://react.dev/learn"),
                        new Step("State Management and Routing",
                                "Client-side routes, shared state and keeping the URL meaningful.",
                                10, "https://reactrouter.com/"),
                        new Step("Consume REST APIs from the UI",
                                "Fetching, caching, optimistic updates and every failure state a user can hit.",
                                7, "https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API"),
                        new Step("Accessibility and Performance",
                                "Keyboard navigation, ARIA, contrast and getting Core Web Vitals into the green.",
                                7, "https://web.dev/learn/accessibility")));
    }

    private void seedDataScientist() {
        seed("Data Scientist",
                "Data Scientist Roadmap",
                "From Python fundamentals to a trained model you can defend the evaluation of.",
                List.of(
                        new Step("Python for Data Analysis",
                                "Syntax, data structures, comprehensions and the standard library.",
                                14, "https://docs.python.org/3/tutorial/"),
                        new Step("NumPy and pandas",
                                "Arrays, dataframes, joins, grouping and cleaning data that arrives messy.",
                                14, "https://pandas.pydata.org/docs/getting_started/index.html"),
                        new Step("Statistics and Probability Foundations",
                                "Distributions, hypothesis testing and reading a confidence interval honestly.",
                                14, "https://www.khanacademy.org/math/statistics-probability"),
                        new Step("Data Visualisation",
                                "Choosing the right chart and making it readable without a paragraph of caption.",
                                7, "https://matplotlib.org/stable/tutorials/index.html"),
                        new Step("Machine Learning with scikit-learn",
                                "Regression, classification, clustering and the shape of a training pipeline.",
                                21, "https://scikit-learn.org/stable/tutorial/index.html"),
                        new Step("Model Evaluation and Validation",
                                "Cross-validation, overfitting, and picking a metric that matches the problem.",
                                14, "https://scikit-learn.org/stable/model_selection.html")));
    }

    private void seedDevOpsEngineer() {
        seed("DevOps Engineer",
                "DevOps Engineer Roadmap",
                "Own the path from a commit to a running, observable production system.",
                List.of(
                        new Step("Linux Command Line and Shell Scripting",
                                "Filesystem, permissions, processes, pipes and scripting the repetitive parts.",
                                14, "https://ubuntu.com/tutorials/command-line-for-beginners"),
                        new Step("Git and Branching Workflows",
                                "Branches, merges, rebases and resolving a conflict without panic.",
                                7, "https://git-scm.com/doc"),
                        new Step("Containerise an Application with Docker",
                                "Images, layers, multi-stage builds, volumes and Compose.",
                                14, "https://docs.docker.com/get-started/"),
                        new Step("Build a CI/CD Pipeline",
                                "Automated build, test and deploy triggered by a push, with secrets kept out of the repo.",
                                10, "https://docs.github.com/en/actions"),
                        new Step("Kubernetes Fundamentals",
                                "Pods, deployments, services, config and why a liveness probe is not a readiness probe.",
                                21, "https://kubernetes.io/docs/tutorials/"),
                        new Step("Infrastructure as Code with Terraform",
                                "Declarative infrastructure, state files and reproducible environments.",
                                14, "https://developer.hashicorp.com/terraform/tutorials"),
                        new Step("Monitoring and Observability",
                                "Metrics, logs, traces and alerts that fire on symptoms rather than causes.",
                                10, "https://prometheus.io/docs/introduction/overview/")));
    }

    private void seedMobileDeveloper() {
        seed("Mobile Developer",
                "Mobile Developer Roadmap",
                "Ship an app to a real store, including the parts that only matter on a real device.",
                List.of(
                        new Step("Kotlin Language Fundamentals",
                                "Null safety, data classes, coroutines and the idioms Android now assumes.",
                                14, "https://kotlinlang.org/docs/getting-started.html"),
                        new Step("Android Studio and the App Lifecycle",
                                "Activities, fragments, the back stack and surviving a configuration change.",
                                14, "https://developer.android.com/courses"),
                        new Step("Build Responsive Mobile Layouts",
                                "Declarative UI with Jetpack Compose across phone and tablet form factors.",
                                10, "https://developer.android.com/develop/ui/compose/documentation"),
                        new Step("Local Storage and Offline Support",
                                "On-device persistence and an app that stays usable on a bad connection.",
                                10, "https://developer.android.com/training/data-storage/room"),
                        new Step("Consume REST APIs from a Mobile App",
                                "HTTP clients, JSON parsing, retries and background work.",
                                10, "https://square.github.io/retrofit/"),
                        new Step("Cross-Platform with React Native",
                                "One codebase across iOS and Android, and where that trade-off stops paying.",
                                14, "https://reactnative.dev/docs/getting-started"),
                        new Step("Publish to the Play Store",
                                "Signing, release builds, store listing and the review process.",
                                5, "https://developer.android.com/distribute/console")));
    }

    private void seedSystemDesignEngineer() {
        seed("System Design Engineer",
                "System Design Engineer Roadmap",
                "Reason about systems that outgrow one machine, and defend the trade-offs you pick.",
                List.of(
                        new Step("Data Structures and Algorithms",
                                "Complexity analysis and the structures every scaling argument is built on.",
                                21, "https://cp-algorithms.com/"),
                        new Step("Databases at Depth",
                                "Indexing, transactions, isolation levels, replication and sharding.",
                                14, "https://www.postgresql.org/docs/current/index.html"),
                        new Step("Caching Strategies",
                                "Cache-aside, write-through, eviction policies and invalidation as the hard part.",
                                10, "https://redis.io/docs/latest/develop/"),
                        new Step("Message Queues and Event-Driven Design",
                                "Async boundaries, delivery guarantees and designing for at-least-once.",
                                14, "https://kafka.apache.org/documentation/"),
                        new Step("Scalability, Load Balancing and CAP",
                                "Horizontal scaling, statelessness and what a partition actually costs you.",
                                14, "https://aws.amazon.com/builders-library/"),
                        new Step("Design a Real System End to End",
                                "Take one product to a full design: data model, services, failure modes, capacity.",
                                14, "https://microservices.io/patterns/")));
    }

    /**
     * Not @Transactional: the single save below cascades to the milestones and is already
     * transactional inside SimpleJpaRepository.save. Wrapping the whole run would hold one
     * transaction open across all seven careers for no gain.
     */
    private void seed(String careerName, String templateTitle, String description, List<Step> steps) {
        if (roadmapTemplateRepository.existsByCareerNameIgnoreCase(careerName)) {
            log.debug("Roadmap template for '{}' already present, skipping", careerName);
            return;
        }

        int totalDays = steps.stream().mapToInt(Step::estimatedDays).sum();

        RoadmapTemplate template = RoadmapTemplate.builder()
                .careerName(careerName)
                .templateTitle(templateTitle)
                .description(description)
                .totalMilestones(steps.size())
                .estimatedWeeks((int) Math.ceil((double) totalDays / DAYS_PER_WEEK))
                .build();

        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            template.getMilestoneTemplates().add(MilestoneTemplate.builder()
                    .title(step.title())
                    .description(step.description())
                    // 1-based, and derived from list position so the seed data cannot carry a gap
                    // or a duplicate. Every ordering query in this service sorts on it.
                    .orderIndex(i + 1)
                    .estimatedDays(step.estimatedDays())
                    .resourceUrl(step.resourceUrl())
                    // MilestoneTemplate owns the foreign key; without this back-reference the
                    // cascade inserts rows with a null roadmap_template_id and the NOT NULL
                    // constraint rejects them.
                    .roadmapTemplate(template)
                    .build());
        }

        roadmapTemplateRepository.save(template);
        log.info("Seeded roadmap template for '{}': {} milestones, ~{} weeks",
                careerName, steps.size(), template.getEstimatedWeeks());
    }
}
