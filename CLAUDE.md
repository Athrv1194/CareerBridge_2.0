# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Security Constraints
- Always verify that `.env` files, API keys, database credentials, and any sensitive secrets are explicitly in `.gitignore` before executing any git commit or git push
- Never write sensitive API keys directly into any codebase file

## Systemic Incident & Bug Logging
Whenever you make a mistake, encounter a bug, or fix a performance issue, log it in `ai_incident_log.md` at the project root.

Requirements:
1. MANDATORY: Read and review `ai_incident_log.md` at the beginning of any new task before making changes
2. Ensure `ai_incident_log.md` is in `.gitignore` before writing to it
3. Log entries must be a table with these columns:
   - Timestamp (UTC)
   - Category (Bug / Error / Performance / Security)
   - Severity (SEV-1 Critical / SEV-2 High / SEV-3 Medium / SEV-4 Low)
   - Component (file, service, or feature affected)
   - Description & Diagnostics
   - Root Cause
   - Fix / Action Taken

## Incident Log Review
Before starting any new task, debugging, or implementing a fix — read `ai_incident_log.md` first to avoid repeating past mistakes.

## Working Instructions
- Always ask clarifying questions before starting any complex or multi-step task
- Do one service at a time — never work on multiple services simultaneously unless explicitly asked
- Outline the plan first and wait for approval before executing
- After each major step, summarize what was done and ask what's next
- Never delete or overwrite existing files without showing what will change and asking for confirmation

## Project Context
- Team size: 5 members, 18-20 day deadline
- Evaluator requirements: microservices proof, RabbitMQ events, MySQL + MongoDB, JWT security, unit tests, AWS deployment
- P0 services (build first): api-gateway, auth-service, student-service, assessment-service, recommendation-service, notification-service
- P1 services (after P0 complete): organization, roadmap, recruiter, resume, ai-coach, payment, placement
- Frontend: careerbridge-frontend/ (React 19 + Vite) — to be created at root level alongside the 6 services

## Project overview

CareerBridge is a Spring Boot microservices project (Spring Boot 4.1.0, Java 21, Maven). Each service is an independent Maven project under its own top-level directory, with no shared parent POM or shared library module — they are only related by convention (identical package/folder layout, adjacent ports, common `com.careerbridge.<service>` base package).

Services and their ports/datastores:

| Service | Dir | Port | Datastore | Notes |
|---|---|---|---|---|
| API Gateway | `api-gateway/` | 8080 | — | Spring Cloud Gateway (webmvc flavor) + Eureka client; routes `/api/{auth,student,assessment,recommendation,notification}/**` to the other services by hardcoded `localhost:<port>` URIs in `application.yml` (no service discovery wired up yet despite the Eureka dependency) |
| Auth Service | `auth-service/` | 8081 | MySQL `careerbridge_auth` | Spring Security + JWT (`jwt.secret`, `jwt.access-token-expiry`, `jwt.refresh-token-expiry` in `application.yml`) |
| Student Service | `student-service/` | 8082 | MySQL `careerbridge_student` | Student profiles, education, skills, projects, certificates |
| Assessment Service | `assessment-service/` | 8083 | MySQL `careerbridge_assessment` | Question bank, attempts/answers, scoring |
| Recommendation Service | `recommendation-service/` | 8084 | MySQL `careerbridge_recommendation` | Consumes assessment events via RabbitMQ, produces career recommendations |
| Notification Service | `notification-service/` | 8085 | MongoDB `careerbridge_notifications` | Consumes events (student registered, assessment completed, recommendation generated) via RabbitMQ, sends emails |

**Known gap:** `recommendation-service` and `notification-service` declare `spring.rabbitmq.*` config in `application.yml` but their `pom.xml` files do not yet include the `spring-boot-starter-amqp` dependency — add it when implementing the consumer/producer classes in `event`/`consumer` packages.

## Common commands

Each service is built and run independently from its own directory (no root aggregator POM).

```bash
# Build a service (from inside e.g. auth-service/)
./mvnw clean install

# Run a service locally
./mvnw spring-boot:run

# Run all tests for a service
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassNameTest

# Run a single test method
./mvnw test -Dtest=ClassNameTest#methodName

# Package (skip tests)
./mvnw clean package -DskipTests
```

On Windows without a shell that resolves `./mvnw`, use `mvnw.cmd` instead (each service ships its own Maven wrapper — `.mvn/`, `mvnw`, `mvnw.cmd`).

Since there's no root POM, building/testing "the whole project" means repeating the above per service directory — there's no single command that builds all six at once.

External infra each service expects to be running locally (not containerized in this repo — no `docker-compose.yml` present):
- MySQL on `localhost:3306` (auth/student/assessment/recommendation services), user `root` / password `root`
- MongoDB on `localhost:27017` (recommendation/notification services)
- RabbitMQ on `localhost:5672`, default `guest`/`guest` (recommendation/notification services)

## Architecture conventions

Every service follows the identical layered package structure under `src/main/java/com/careerbridge/<service>/`:

- `controller/` — REST controllers
- `service/` — business logic; interface (`XService`) + implementation (`XServiceImpl`) pair
- `repository/` — Spring Data repository interfaces (JPA for MySQL services, Mongo for notification-service)
- `model/` — JPA entities / Mongo documents
- `dto/` — request/response DTOs, kept separate from `model/`
- `config/` — `SecurityConfig`, `JwtConfig` (auth only), `RabbitMQConfig` (recommendation/notification)
- `exception/` — `GlobalExceptionHandler` + `CustomException`
- `event/` — event payload classes for cross-service messaging (recommendation, notification)
- `consumer/` — RabbitMQ listener classes (notification-service only, e.g. `NotificationEventConsumer`)
- `filter/` — servlet filters (`JwtAuthenticationFilter` in api-gateway and auth-service)
- `util/` — service-specific helpers (e.g. `ProfileCompletionCalculator` in student-service, `ScoringEngine` in assessment-service)

Cross-service event flow (via RabbitMQ, MySQL-backed services publish, notification/recommendation consume): `StudentRegisteredEvent` → notification-service; `AssessmentCompletedEvent` → recommendation-service and notification-service; `RecommendationGeneratedEvent` → notification-service.

Configuration is `application.yml` per service (not `.properties`). Each service's `spring.application.name` matches its directory name and is used as the Eureka/service-registry identifier once discovery is wired in.

Implementation status: **auth-service is complete** (JWT auth, refresh tokens with DB-backed revocation, BCrypt, RabbitMQ registration event, 10 unit tests) — see branch `feature/auth`. All other services are still skeletons: `XApplication.java` plus empty class/interface bodies, no business logic.

Auth-service specifics worth knowing before touching it or consuming its events:
- Event contract published on registration: exchange `careerbridge.exchange` (topic, durable), routing key `student.registered`, JSON body `{userId, email, firstName, lastName, role, organizationId, registeredAt}`. Consumers declare their own queue and binding.
- Local credentials live in a gitignored `application-local.yml` (profile `local`, active by default via `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`). Never put a real password in `application.yml`.
- `/api/auth/refresh` is intentionally `permitAll` — it is called because the access token expired.
- Boot 4 gotchas already hit here: use `JacksonJsonMessageConverter` (Jackson 3), not `Jackson2JsonMessageConverter`; `UserDetailsServiceAutoConfiguration` now lives in `org.springframework.boot.security.autoconfigure`.

## Pending Tasks (Do Not Forget)
- notification-service: add spring-boot-starter-amqp to pom.xml (known gap, do when building notification service)
- recommendation-service: add spring-boot-starter-amqp to pom.xml (known gap, do when building recommendation service)
- api-gateway: needs jwt.secret in application.yml and JwtAuthenticationFilter implemented (do after all services built)
- api-gateway: JWT tokens are HS384 NOT HS256 — jjwt picks strongest HMAC based on key length (49-byte secret = HS384). Do not hardcode HS256 anywhere in gateway filter.
- actuator health check: /actuator/health returns 503 when RabbitMQ is down — point AWS ALB at /actuator/health/liveness instead
- Java PATH issue: JDK 21 must come before Java 8 on PATH for all team members — otherwise java -jar fails with UnsupportedClassVersionError
