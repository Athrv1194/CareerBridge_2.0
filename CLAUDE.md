# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

At present all files beyond the skeleton (`XApplication.java`, package declarations, empty class/interface bodies) are placeholders — business logic has not yet been implemented in any service.
