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
- Evaluator requirements: microservices proof, RabbitMQ events, PostgreSQL + MongoDB, JWT security, unit tests, AWS deployment
- P0 services (build first): api-gateway, auth-service, student-service, assessment-service, recommendation-service, notification-service
- P1 services (after P0 complete): organization, roadmap, recruiter, resume, ai-coach, payment, placement
- Frontend: `careerbridge-frontend/` (React 19 + Vite) — scaffolded at root level alongside the 6 services; folder structure and deps in place, no business logic yet

## Project overview

CareerBridge is a Spring Boot microservices project (Spring Boot 4.1.0, Java 21, Maven). Each service is an independent Maven project under its own top-level directory, with no shared parent POM or shared library module — they are only related by convention (identical package/folder layout, adjacent ports, common `com.careerbridge.<service>` base package).

Services and their ports/datastores:

| Service | Dir | Port | Datastore | Notes |
|---|---|---|---|---|
| API Gateway | `api-gateway/` | 8080 | — | Spring Cloud Gateway (servlet/webmvc flavor, Spring Cloud 2025.1.2 / SCG 5); validates JWTs and injects `X-User-Id`, the only component in the system that does; routes `/api/{auth,student,assessment,recommendation,notification}/**` to the other services by hardcoded `localhost:<port>` URIs in `application.yml` |
| Auth Service | `auth-service/` | 8081 | PostgreSQL `careerbridge_auth` | Spring Security + JWT (`jwt.secret`, `jwt.access-token-expiry`, `jwt.refresh-token-expiry` in `application.yml`) |
| Student Service | `student-service/` | 8082 | PostgreSQL `careerbridge_student` | Student profiles, education, skills, projects, certificates |
| Assessment Service | `assessment-service/` | 8083 | PostgreSQL `careerbridge_assessment` | Question bank, attempts/answers, scoring |
| Recommendation Service | `recommendation-service/` | 8084 | PostgreSQL `careerbridge_recommendation` | Consumes `assessment.completed` via RabbitMQ, ranks career paths, publishes `recommendation.generated` |
| Notification Service | `notification-service/` | 8085 | **Both** PostgreSQL `careerbridge_notification` and MongoDB `careerbridge_notifications` (Atlas) | Consumes `student.registered` (contact details) and `recommendation.generated` (email + in-app notification) via RabbitMQ, sends Gmail SMTP email |

The only dual-datastore service: PostgreSQL is the system of record (delivery audit + contact details, both needing unique constraints), MongoDB is the student-facing in-app feed. Note the database names differ by one character on purpose — Postgres `careerbridge_notification` singular, matching the other four; Mongo `careerbridge_notifications` plural, as already committed.

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

**Test dependencies are Boot 4's split `*-test` starters, not `spring-boot-starter-test`.** No service declares the latter; each pulls `spring-boot-starter-{webmvc,data-jpa,validation,actuator}-test` instead, and `webmvc-test` is what transitively supplies JUnit 5 + Mockito. Do not add `spring-boot-starter-test` — it is already there indirectly.

Frontend (from inside `careerbridge-frontend/`):

```bash
npm install
npm run dev       # Vite dev server
npm run build     # production build
npm run lint      # oxlint (NOT eslint) -- config in .oxlintrc.json
npm run preview   # serve the production build
```

`VITE_API_BASE_URL` in a gitignored `.env` points the frontend at the gateway (`http://localhost:8080/api`).

External infra each service expects to be running locally (not containerized in this repo — no `docker-compose.yml` present):
- PostgreSQL on `localhost:5432` (auth/student/assessment/recommendation services); the 4 databases (`careerbridge_auth`, `careerbridge_student`, `careerbridge_assessment`, `careerbridge_recommendation`) must be created manually first — `CREATE DATABASE ...;` — PostgreSQL has no `createDatabaseIfNotExist` JDBC flag the way MySQL did
- MongoDB — **notification-service uses MongoDB Atlas (cloud), not a local mongod**. The `mongodb+srv://` URI embeds credentials and lives only in that service's gitignored `application-local.yml`. No other service has a Mongo dependency. The property is **`spring.mongodb.uri`**, not `spring.data.mongodb.uri` — see the notification-service notes below.
- SMTP: Gmail on `smtp.gmail.com:587` (notification-service only). Needs a Gmail **App Password**, not the account password, and 2FA enabled on the account.
- RabbitMQ on `localhost:5672`, default `guest`/`guest` (recommendation/notification services)

## Git workflow

`feature/<name>` → `dev` → `main`. All four branches exist on `origin`.

- One feature branch per service, cut from `dev` (`feature/auth`, `feature/student`).
- Merge into `dev` with `--no-ff` so each service lands as a reviewable merge bubble rather than a flattened fast-forward.
- Verify the build is green on `dev` after merging **before** pushing.
- Do not commit directly to `dev` or `main` for service work.

## Architecture conventions

Every service follows the identical layered package structure under `src/main/java/com/careerbridge/<service>/`:

- `controller/` — REST controllers
- `service/` — business logic; interface (`XService`) + implementation (`XServiceImpl`) pair
- `repository/` — Spring Data repository interfaces (JPA for PostgreSQL services, Mongo for notification-service)
- `model/` — JPA entities / Mongo documents
- `dto/` — request/response DTOs, kept separate from `model/`
- `config/` — `SecurityConfig`, `JwtConfig` (auth only), `RabbitMQConfig` (any service that publishes or consumes: auth, student, recommendation, notification)
- `exception/` — `GlobalExceptionHandler` + `CustomException`
- `event/` — event payload classes for cross-service messaging (auth, student, recommendation, notification)
- `consumer/` — RabbitMQ listener classes (`StudentEventConsumer` in student-service, `NotificationEventConsumer` in notification-service)
- `constants/` — service-scoped constant holders, private ctor, no instantiation (`JwtConstants` in auth, `SkillConstants` in student)
- `filter/` — servlet filters (`JwtAuthenticationFilter` in api-gateway and auth-service)
- `util/` — service-specific helpers (e.g. `ProfileCompletionCalculator` in student-service, `ScoringEngine` in assessment-service)

Cross-service event flow via RabbitMQ, all on the topic exchange `careerbridge.exchange`. Each consumer declares its own queue and binding; the publisher declares only the exchange.

| Event | Routing key | Publisher | Consumers | Status |
|---|---|---|---|---|
| `StudentRegisteredEvent` | `student.registered` | auth-service | student-service (creates the profile), notification-service (stores contact details) | **both consumers implemented** — each on its own queue |
| `AssessmentCompletedEvent` | `assessment.completed` | assessment-service | recommendation-service | **recommendation-service consumer implemented.** notification-service deliberately does NOT consume this — see Pending Tasks |
| `RecommendationGeneratedEvent` | `recommendation.generated` | recommendation-service | notification-service (email + in-app notification) | **fully wired end to end** (not yet verified against a live broker) |

Queue ownership, one per consumer per event — a queue is never shared between services or between event types:

| Queue | Owner | Bound routing key |
|---|---|---|
| `careerbridge.student.queue` | student-service | `student.registered` |
| `careerbridge.recommendation.queue` | recommendation-service | `assessment.completed` |
| `careerbridge.notification.queue` | notification-service | `recommendation.generated` |
| `careerbridge.notification.student.queue` | notification-service | `student.registered` |

Configuration is `application.yml` per service (not `.properties`). Each service's `spring.application.name` matches its directory name. There is no discovery/Eureka anywhere in this project — api-gateway's routes are hardcoded `localhost:<port>` URIs, and the Eureka client dependency api-gateway briefly carried was removed as dead weight (see its specifics below).

Implementation status:
- **auth-service — complete** (JWT auth, refresh tokens with DB-backed revocation, BCrypt, RabbitMQ registration event, 10 unit tests) — branch `feature/auth`.
- **student-service — complete** (profile CRUD, education/skills/projects/certificates, profile completion scoring, RabbitMQ `student.registered` consumer that auto-creates profiles, 22 unit tests) — branch `feature/student`.
- **assessment-service — complete** (question bank seeded via `data.sql`, random 5-question attempts, weighted scoring, career matching, publishes `assessment.completed`, 22 unit tests) — branch `feature/assessment`.
- **recommendation-service — complete** (RabbitMQ `assessment.completed` consumer, ranks all 7 careers from a local `CareerCatalog` constant against `categoryName`/`categoryScorePercentage`, generated reason text, history + active-recommendation tracking, publishes `recommendation.generated`, 26 unit tests) — branch `feature/recommendation`.
- **notification-service — complete** (consumes `student.registered` into a `UserContact` bridge table and `recommendation.generated` into a Gmail email plus a MongoDB in-app notification; REST feed with unread count and mark-as-read; dual datastore PostgreSQL + MongoDB Atlas; 28 unit tests) — branch `feature/notification`.
- **api-gateway — complete** (servlet `JwtAuthenticationFilter` validates HS384 JWTs and injects `X-User-Id` into every request, stripping any client-supplied value; routes to all 5 services; Eureka client removed as unused; 20 unit tests, live-verified against a real downstream proxy) — branch `feature/api-gateway`.

All 6 P0 services now implemented. Remaining before the evaluator's microservices proof: docker-compose and the end-to-end event chain (auth → student → assessment → recommendation → notification) against a live broker.

**Not yet verified end-to-end:** the auth → student event flow has passing unit tests on both sides but the two services have never been run together against a live broker. Do this when docker-compose lands.

Auth-service specifics worth knowing before touching it or consuming its events:
- Event contract published on registration: exchange `careerbridge.exchange` (topic, durable), routing key `student.registered`, JSON body `{userId, email, firstName, lastName, role, organizationId, registeredAt}`. Consumers declare their own queue and binding.
- Local credentials live in a gitignored `application-local.yml` (profile `local`, active by default via `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`). Never put a real password in `application.yml`.
- `/api/auth/refresh` is intentionally `permitAll` — it is called because the access token expired.
- Boot 4 gotchas already hit here: use `JacksonJsonMessageConverter` (Jackson 3), not `Jackson2JsonMessageConverter`; `UserDetailsServiceAutoConfiguration` now lives in `org.springframework.boot.security.autoconfigure`.

Student-service specifics worth knowing before touching it or copying its patterns:
- **No Spring Security on the classpath, by design.** `userId` arrives in the `X-User-Id` header, forwarded by the gateway after it validates the JWT; this service never parses tokens. `config/SecurityConfig.java` is a deliberately empty stub. Consequence: port 8082 must not be publicly reachable — anyone who can hit it directly sets that header to any value and acts as that student. Enforce with a security group / bind address at deploy time.
- Consumes `student.registered` on its own queue `careerbridge.student.queue`, bound to `careerbridge.exchange` with `TopicExchange(EXCHANGE, true, false)` — durable/autoDelete args must match auth-service's declaration exactly or RabbitMQ answers `406 PRECONDITION_FAILED` and the consumer silently never starts.
- The `@RabbitListener` parameter must stay the concrete `StudentRegisteredEvent`. Spring AMQP's default `TypePrecedence.INFERRED` resolves the payload from the method signature, which is the only reason the differing package FQN between auth and student works with zero type-mapper config. Widening it to `Object`/`Message` falls back to the sender's `__TypeId__` header → `ClassNotFoundException`.
- The consumer's copy of the event declares `role` as `String`, not an enum: wire-identical, but a duplicated enum would make Jackson hard-fail every event the day auth-service adds a seventh role.
- Consumer is intentionally **not** `@Transactional` and swallows all exceptions. The `unique` constraint on `student_profiles.userId` is the real idempotency guarantee; rethrowing would requeue and spin the listener forever.
- `addCertificate` deliberately skips the completion recalculation — certificates carry 0% weight, so it would be a no-op costing 4 queries. Pinned by `addCertificate_Success_DoesNotRecalculate`; give certificates a weight and that test fails.
- Profile completion basic-info block is all-or-nothing (all five of firstName/lastName/phone/bio/city, or zero of the 20%). Blank strings count as absent, because `PUT /profile` is a full replace where null clears a field.

Assessment-service specifics worth knowing before touching it:
- **Option weights must never reach the client.** Enforced structurally: `OptionDto` has no `weight` field, and `submitAttempt` reads `weightEarned` from the stored `Option` row, never from the request payload.
- **`orderIndex` in responses is the position in that response, not the stored value.** In the seed data the highest-weighted option is always `order_index = 1`, so echoing the DB value back would let a client sort by it and pick the best answer every time — defeating the option shuffle. Renumbering is a security fix, not cosmetics.
- Each attempt draws `QUESTIONS_PER_ATTEMPT` (5) questions at random from the category, reshuffled per call, with options shuffled too. Which 5 were shown is **not persisted**, so `submitAttempt` can only verify answers are ≤5 valid questions from the category, not that they were the ones displayed. Closing that needs a `selected_question_ids` column.
- `maxPossibleScore` = `QUESTIONS_PER_ATTEMPT × MAX_OPTION_WEIGHT` (15), a fixed server-side denominator. Not the category total (larger, so a full answer set would score under 100%) and not the answer count (client-controlled, so one perfect answer would be 100%). The answer-count cap in `validateAndScore` is what stops a client submitting every category question against that fixed denominator.
- `data.sql` re-runs on every startup (`spring.sql.init.mode: always`). It is idempotent **only because** of the unique constraints on `questions(category_id, order_index)` and `options(question_id, order_index)` — `INSERT ... ON CONFLICT DO NOTHING` (PostgreSQL; was `INSERT IGNORE` under MySQL) suppresses nothing without them. See `ai_incident_log.md`; this broke startup once already under MySQL, and the MySQL→PostgreSQL conversion itself introduced a second, self-inflicted data-corruption incident — also logged.
- **Every `@Lob String` field was migrated to `@Column(columnDefinition = "TEXT")`.** Hibernate's `PostgreSQLDialect` maps a bare `@Lob String` to the `oid` large-object type (MySQL's dialect mapped the same annotation to `LONGTEXT`), which rejects a plain string value from either `data.sql` or a normal `repository.save(...)`. Affects `CareerPath.description`, `Category.description`, `Question.questionText`, `AssessmentResult.allCareerScoresJson` here, plus 3 fields in student-service. See `ai_incident_log.md`.
- **`data.sql` now supplies `created_at` explicitly** on every seeded row in `career_paths`, `categories`, and `questions`. `@CreationTimestamp` only fires on Hibernate's own insert path, never on a raw SQL script; MySQL silently substituted an implicit default for the missing `NOT NULL` value (outside strict mode), but PostgreSQL always rejects it. `options` has no `@CreationTimestamp` column and needed no change.
- `spring.jpa.defer-datasource-initialization: true` is a **Boot** property. Under `spring.jpa.properties.hibernate` it is silently ignored and `data.sql` runs before the tables exist.
- **`AssessmentCompletedEvent.allCareerScores` carries every career; `AssessmentResult.allCareerScoresJson` carries only the top `TOP_CAREERS_TO_RECOMMEND`.** Same-sounding names, different contents, deliberately: the event feeds recommendation-service's full ranking, while the persisted JSON is what `AssessmentResultDto` returns to the client and stays capped. Pinned by `submitAttempt_PublishedEvent_CarriesAllCareerScores`.
- Adding a field to the event is backward compatible with consumers holding an older copy — Jackson 3 (`tools.jackson`, what Boot 4 ships) disables `FAIL_ON_UNKNOWN_PROPERTIES` by default, verified by round-tripping the 9-field payload into an 8-field replica through `JacksonJsonMessageConverter`. Removing or renaming a field is still breaking.

Recommendation-service specifics worth knowing before touching it:
- **No `career_paths` table.** The 7 careers live in `constants/CareerCatalog.java` as a `List<CareerPathDto>` constant, not an entity — nothing in this schema references a career by id, so a table would add ddl-auto surface and a `data.sql` idempotency risk for zero capability. It is a second copy of assessment-service's `data.sql` seed and **must be kept in the same order** — see the next point.
- **`CareerCatalog.ALL`'s order is load-bearing.** It mirrors assessment-service's `data.sql` insert order, which is the order its `findAll()` returns and therefore what its stable sort falls back to on a tie. This service's own ranking ties the same way today (see below), so the two orderings must be edited together or rank 1 can silently diverge between the two services.
- **The `assessment.completed` event carries no map of career scores** — 8 scalar fields only, and `topCareerPath`/`careerMatchPercentage` are nullable. The full 7-career ranking is recomputed locally via `RecommendationEngine.calculateMatchScore`, a byte-for-byte mirror of assessment-service's `ScoringEngine` relevance heuristic (`1.0` if `requiredSkills` contains the category name, else `0.3`).
- **Only 2 of assessment-service's 5 categories have seeded questions today, and neither matches any career's `requiredSkills`.** So every real event currently scores all 7 careers at `0.3` relevance (ceiling 30.0) — a 7-way tie. Rank 1 in that case is decided by a tie-break that prefers the event's own `topCareerPath` name, falling back to `CareerCatalog.ALL` order if it's null or unrecognized. There is deliberately **no minimum-score threshold** on `topRecommendations` (`isTopRecommendation = rank <= TOP_CAREERS_COUNT` only) — a threshold above 30.0 would make it permanently empty against today's data.
- **`CareerRanking.rank` needs a quoted column name.** `RANK` is a reserved word in both MySQL 8.0.2+ and PostgreSQL (ANSI SQL:1999 window-function keyword); `hibernate.auto_quote_keyword` defaults off and nothing in this project enables it, so an unquoted `rank` column fails `ddl-auto` at startup. Fixed via `@Column(name = "\"rank\"")` — verified against live MySQL 9.0.1 (error 1064 without the fix) and again against live PostgreSQL 18.4 in both DDL and DML (insert + `ORDER BY`) after the database migration.
- **`findByUserIdAndIsActiveTrue` returns a `List`, not an `Optional`.** Originally because MySQL has no partial unique index. **Now on PostgreSQL, which does support one** (`CREATE UNIQUE INDEX ... WHERE is_active`) — the `List` design was kept as-is during the DB migration rather than revisited, since two concurrent `assessment.completed` events racing is already handled gracefully (reads take the newest, writes deactivate every row found) and a partial index would only add a second, redundant guarantee. Worth reconsidering if this service is ever revisited for its own sake, not as a side effect of the database swap.
- `overallMatchPercentage` and `topCareerName` on `Recommendation` are always read from this service's **own** rank 1, never copied from the event's `careerMatchPercentage`/`topCareerPath` — keeps the response internally consistent even if the two catalogs ever drift.
- Publishes `recommendation.generated`, now consumed by notification-service on `careerbridge.notification.queue`. End-to-end delivery has **not** been verified against a live broker yet — do it with docker-compose.

Notification-service specifics worth knowing before touching it:
- **`spring.mongodb.uri`, NOT `spring.data.mongodb.uri`.** Boot 4 moved driver-level Mongo settings into `spring.mongodb.*`; the old key is deprecated at level **`error`** since 4.0.0, which means Boot neither binds it nor warns — the driver silently falls back to `localhost:27017` and database `test`. This shipped in the Initializr scaffold and cost a real debugging session; logged SEV-2 in `ai_incident_log.md`. `spring.data.mongodb.*` still exists but only for Spring-Data-level settings (`auto-index-creation`, `field-naming-strategy`, `gridfs.*`).
- **Two queues, not one.** `careerbridge.notification.queue` (`recommendation.generated`) and `careerbridge.notification.student.queue` (`student.registered`). Two `@RabbitListener` methods on a *single* queue would create two containers consuming it, and RabbitMQ round-robins between them — roughly half of each event type would be bound into the wrong class. Jackson 3 has `FAIL_ON_UNKNOWN_PROPERTIES` off, so that does not throw; it yields an all-null object the null guard discards with a WARN, and half the notifications vanish with no error anywhere.
- **`UserContact` exists because `RecommendationGeneratedEvent` carries no email address.** `student.registered` is consumed purely to harvest `email`/`firstName`/`lastName` into a bridge table, looked up when a recommendation arrives. Consequence: a student who registered *before* this service first ran has no contact row — their email is skipped, but the in-app notification is still created and the `NotificationRecord` is written `FAILED` with the reason. That path is pinned by a test.
- **Write order in `processRecommendationNotification` is load-bearing: Mongo → email → Postgres audit row.** The state that must never occur is "audit row says SENT, no in-app notification, and the unique constraint now blocks every retry" — the student would silently never see their result. In this order a Mongo outage aborts before anything irreversible; the naive order makes that bad state the guaranteed outcome of any Mongo hiccup.
- **No `@Transactional` anywhere in this service, deliberately.** Boot auto-configures only a `JpaTransactionManager`, so it would cover neither the Mongo write nor the SMTP call while pinning a JDBC connection across a multi-second SMTP round trip. The two JPA writes are single statements, already transactional inside `SimpleJpaRepository.save`. Consistency comes from write *order* instead. There is a class-level comment saying so — please do not "restore the convention".
- **`NotificationDocument.createdAt` is set explicitly by the service.** `@CreationTimestamp` is `org.hibernate.annotations.*` and does nothing on a Mongo document — it would silently leave the field null and break the `OrderByCreatedAtDesc` feed ordering.
- **`isRead` is a `Boolean`, never a primitive**, with `@Builder.Default = false`. A primitive makes Lombok emit `isRead()`, collapsing the JSON property to `read` and silently breaking the frontend contract; a missing `@Builder.Default` writes `null`, and a Mongo query for `{isRead: false}` does not match `null`, so every new notification would be invisible to the unread count. Both are pinned by tests.
- **No `@Indexed` on the Mongo document.** `MongoMappingContext.autoIndexCreation` defaults to `false`, so it would be a silent no-op, and enabling it would make a reachable Mongo a hard startup requirement for every developer and for CI. If the feed query ever gets slow, create the index by hand in `mongosh` and note it here.
- **`management.health.mail.enabled: false`** — `MailHealthIndicator` opens a real SMTP connection to Gmail on every health poll; an ALB polling every 10s means ~8,600 handshakes a day and near-certain throttling. Also note `/actuator/health` here aggregates **three** datastores (Postgres + Mongo + RabbitMQ), so it is the most fragile of the five behind a naive health check — the ALB must use `/actuator/health/liveness`.
- **JavaMail has no default timeouts** and SMTP runs on the `@RabbitListener` thread at concurrency 1, so one hung Gmail connection would stall every subsequent notification indefinitely. `connectiontimeout`/`timeout`/`writetimeout` are all set to 5000ms.
- The controller is `/api/notification` (**singular**) to match the existing gateway route `Path=/api/notification/**`; gateway predicates match whole segments, so a plural mapping would 404 every request arriving through the gateway.
- `EmailService` is a concrete `@Service` with no interface — one implementation, and Mockito mocks classes fine. `buildHtmlBody` is public only so the base-package test can call it directly.

API-gateway specifics worth knowing before touching it:
- **Servlet, not reactive.** The dependency is `spring-cloud-starter-gateway-server-webmvc` (Tomcat) — Spring Cloud Gateway 5 split reactive and servlet into separate starters, and the old catch-all `spring-cloud-starter-gateway` no longer exists at this version (404 on Maven Central). Consequence: no `Mono`, no `ServerWebExchange`, no `AbstractGatewayFilterFactory`, no `ErrorWebExceptionHandler` — those are reactive-only APIs and are not on this classpath. Authentication is a plain `OncePerRequestFilter`, the same mechanism auth-service uses for its own filter.
- **Routes bind under `spring.cloud.gateway.server.webmvc.routes`, NOT `spring.cloud.gateway.routes`.** The bare key was correct for SCG ≤ 4.1 and is what most online examples still show; under SCG 5 it binds to nothing, with no warning and no startup failure — the app reports healthy and every `/api/**` request 404s as if the route simply didn't exist. Logged SEV-2 in `ai_incident_log.md`, reproduced with an A/B test on the same jar (old prefix → 404, new prefix → 500-with-upstream-down, proving the route matched).
- **The `X-User-Id` header is stripped and reinjected on *every* request, including public ones.** `JwtAuthenticationFilter` wraps the `HttpServletRequest` unconditionally; on a public path the wrapper carries a `null` id, which hides any client-supplied `X-User-Id` rather than passing the raw request through. Every downstream service trusts this header blindly and has no Spring Security of its own (see student-service notes above), so a bare `chain.doFilter(request, response)` on the public-path branch would let any caller impersonate any user with one `curl -H "X-User-Id: 1"`. Verified live: a spoofed header on both a protected route (with a valid token) and a public route (with no token) never reaches the downstream service. Pinned by two tests.
- **All three `HttpServletRequestWrapper` header methods must be overridden — `getHeader`, `getHeaders`, and `getHeaderNames`.** Spring's `ServletRequestHeadersAdapter`, which is what the gateway's proxy step actually reads from, enumerates via `getHeaderNames()` and then calls `getHeaders(name)` — it does not call `getHeader(String)` at all. Overriding only `getHeader` compiles cleanly and is a silent no-op: the injected header would never leave the gateway. Verified by proxying through a real downstream stand-in and reading back what it received.
- **`userId` claim: `claims.get("userId", Long.class)`, never a `(Long)` cast.** auth-service writes the claim as a `Long`, but JSON only has "number", so Jackson deserializes anything under 2³¹ back as an `Integer` — which is every real user id. A cast throws `ClassCastException` on the first authenticated request. jjwt's typed accessor widens correctly.
- **HS384, not HS256** — same 49-byte-secret rule as auth-service (see below); this gateway must derive the key exactly the same way (`Keys.hmacShaKeyFor`, no explicit algorithm) or verification fails for every token.
- **`gateway.jwt-secret` must stay byte-identical to auth-service's `jwt.secret`.** One signs, the other verifies; there is no `application-local.yml` for this service because the committed default already matches auth-service and there is no other secret to protect.
- **Public paths are the whole `/api/auth/**` surface, `/actuator/**`, and `/api/recommendation/careers`.** `/api/auth/refresh` can never be JWT-validated (refresh tokens are opaque UUIDs checked against a Postgres table, not JWTs — see auth-service specifics), and `/logout` is called precisely when the access token has expired, so gating the whole auth surface behind a valid access token is wrong, not lazy. There is no `/api/assessment/careers` — that endpoint does not exist on assessment-service; don't re-add it from the original spec without checking.
- **No Spring Security dependency, and none of the five backend services has one either.** `config/SecurityConfig.java` is a deliberately empty stub, same convention as student-service's — the servlet filter does the authentication, and a full Spring Security filter chain here would immediately need `permitAll` everywhere, which is ceremony around code that already exists.
- Eureka client dependency was removed — nothing in this repo configures a discovery server, routes are hardcoded `localhost:<port>` URIs, and the dependency only produced continuous connection-refused log noise.
- Since this gateway is the only thing validating a JWT, **ports 8081–8085 must not be publicly reachable** at deploy time — the same constraint already noted for student-service's port 8082, now true of all five backend services.

## Pending Tasks (Do Not Forget)
- **notification-service does not consume `assessment.completed`** — scoped out deliberately, not forgotten. Today only recommendation-service consumes it. If an "assessment complete" in-app notification is wanted, add a third queue (`careerbridge.notification.assessment.queue`) with its own binding and single-type listener; do NOT add a second listener to an existing queue.
- **Migrating any service to Boot 4 requires checking `spring-configuration-metadata.json` for `"deprecation"` entries**, not assuming Boot 3 property names still bind. Entries at `level: error` are silently ignored — no warning, no startup failure, just default behaviour. This cost a debugging session on notification-service's Mongo URI (SEV-2). A `@Value` resolving a property proves only that it exists in the Environment, not that any auto-configuration consumes it.
- **Verify every external datastore with a real round trip, not a context load.** `contextLoads` passed while Mongo was pointed at the wrong host entirely, because the driver connects lazily.
- Any future `data.sql` needs a unique key on every table it inserts into, or `INSERT ... ON CONFLICT DO NOTHING` silently duplicates rows on each restart — logged SEV-2 in assessment-service. Also: never hand-roll SQL statement splitting with a plain regex when converting seed files between dialects — a `;` or `(`/`)` can appear inside a string literal and silently corrupt a "successful" conversion; logged SEV-1 (self-inflicted, caught before commit) during the MySQL→PostgreSQL migration.
- Any future `@Lob String` field needs `@Column(columnDefinition = "TEXT")` instead if this project ever changes database engine again — `@Lob` silently changes column type per-dialect (MySQL: `LONGTEXT`; PostgreSQL: `oid`), and `oid` rejects plain string values from both seed SQL and normal ORM saves.
- api-gateway: JWT tokens are HS384 NOT HS256 — jjwt picks strongest HMAC based on key length (49-byte secret = HS384). Do not hardcode HS256 anywhere in gateway filter or its tests.
- **Any test that "tampers" a JWT by appending a character to the signature is not testing anything.** An HS384 signature is 48 bytes = exactly 64 base64url characters, so a 65th character contributes 6 bits that decode back to the same value — the token still verifies and the test passes for the wrong reason. Tamper by flipping a byte inside an existing segment, or by grafting on a signature produced with a different key. Caught in api-gateway's own filter testing before it shipped as a false-positive test.
- actuator health check: /actuator/health returns 503 when RabbitMQ is down — point AWS ALB at /actuator/health/liveness instead. Reproduced on recommendation-service with the broker down: `/actuator/health` → 503, `/actuator/health/liveness` and `/readiness` → 200.
- Java PATH issue: JDK 21 must come before Java 8 on PATH for all team members — otherwise java -jar fails with UnsupportedClassVersionError
- **Every remaining service needs two exception handlers copied from student-service's `GlobalExceptionHandler`**: `HttpMessageNotReadableException` (malformed JSON) and `MethodArgumentTypeMismatchException` (non-numeric `X-User-Id`). Neither implements Spring's `ErrorResponse`, so both return a misleading HTTP 500 without an explicit handler. Both are logged incidents; see `ai_incident_log.md`. (student, assessment, recommendation and notification all have both — recommendation verified against a live server.)
- **auth-service is missing `MethodArgumentTypeMismatchException`** — it has 4 handlers where the other four services have 5. The fix from `ai_incident_log.md` (2026-07-25) was never back-ported to it. Lower risk there because its endpoints take JSON bodies rather than a numeric `X-User-Id` header, so nothing triggers it today — but any `@PathVariable Long` or numeric `@RequestParam` added later returns a 500 for a plain client typo. One-line fix on its own branch; do not fold it into another service's work.
- If a future service needs a reserved word as a column name (e.g. `rank`, `order`, `group` — reserved in both MySQL and PostgreSQL), quote it explicitly — `@Column(name = "\"rank\"")` — and confirm with `hibernate.auto_quote_keyword` off (the project default). ddl-auto silently fails on an unquoted reserved word. See recommendation-service's `CareerRanking.rank`.
- Each team member must create their own gitignored `application-local.yml` per PostgreSQL-backed service (`auth-service`, `student-service`, `assessment-service`, `recommendation-service`, and the rest as they are built) with their real DB password, or `mvnw clean install` fails on `contextLoads`. Add to team setup notes.
- **All 5 databases must exist before any service starts** (`CREATE DATABASE careerbridge_auth;` `_student;` `_assessment;` `_recommendation;` `_notification;` — PostgreSQL has no `createDatabaseIfNotExist` JDBC flag, unlike MySQL). Add this to team setup notes alongside the `application-local.yml` step.
- **notification-service's `application-local.yml` needs three secrets, not one**: the PostgreSQL password, the MongoDB Atlas `mongodb+srv://` URI (embeds its own user/password), and a Gmail **App Password** (16 chars, requires 2FA; the account password will not authenticate). Every team member needs their own, or `mvnw clean install` fails on `contextLoads`.
- **notification-service end-to-end is unverified**: `student.registered` → contact row → `recommendation.generated` → real email + Mongo document. Needs RabbitMQ running. Everything below the broker is proven — Postgres DDL, Atlas connectivity (live `ping`), and all three REST endpoints were exercised against a running instance.
- docker-compose still not present — needed for the end-to-end auth → student → assessment → recommendation → notification event verification, and for the evaluator's microservices proof.
