\# CareerBridge 2.0 — Contributors Branch



> \*\*Backend Microservices Contribution \& Evaluation Reference\*\*



This branch contains the \*\*team contribution structure for CareerBridge 2.0\*\*, organized by backend ownership area.



The purpose of this branch is to provide a clean, evaluator-friendly view of:



\* Team member ownership

\* Backend microservice allocation

\* Service ports

\* Datastore ownership

\* RabbitMQ event responsibilities

\* Synchronous REST interactions

\* Contributor-specific source code

\* Technical responsibilities and evaluation talking points



\---



\## Table of Contents



\* \[Project Overview](#project-overview)

\* \[Architecture Overview](#architecture-overview)

\* \[Team Allocation](#team-allocation)

\* \[Contributor Directory Structure](#contributor-directory-structure)

\* \[Service Ownership](#service-ownership)



&#x20; \* \[Alpha — Platform Security \& Monetization](#alpha--platform-security--monetization)

&#x20; \* \[Beta — Student Profile \& Recommendation](#beta--student-profile--recommendation)

&#x20; \* \[Gamma — Career Execution \& Recruiter Platform](#gamma--career-execution--recruiter-platform)

&#x20; \* \[Delta — AI, Notifications \& Infrastructure](#delta--ai-notifications--infrastructure)

\* \[Service \& Port Reference](#service--port-reference)

\* \[Database Ownership](#database-ownership)

\* \[RabbitMQ Event Architecture](#rabbitmq-event-architecture)

\* \[Synchronous REST Interactions](#synchronous-rest-interactions)

\* \[Security Architecture](#security-architecture)

\* \[Reliability \& Fault Tolerance](#reliability--fault-tolerance)

\* \[Evaluator Demonstration Guide](#evaluator-demonstration-guide)

\* \[Technical Defense](#technical-defense)

\* \[Contribution History](#contribution-history)

\* \[Development Notes](#development-notes)



\---



\# Project Overview



\*\*CareerBridge 2.0\*\* is a Spring Boot microservices platform designed to support the complete student-to-career lifecycle.



The backend uses:



\* \*\*Spring Boot 4.1.0\*\*

\* \*\*Java 21\*\*

\* \*\*Maven\*\*

\* \*\*PostgreSQL\*\*

\* \*\*MongoDB Atlas\*\*

\* \*\*RabbitMQ\*\*

\* \*\*Spring Cloud Gateway\*\*

\* \*\*JWT-based authentication\*\*

\* \*\*Docker / Docker Compose\*\*

\* \*\*REST-based synchronous service communication\*\*

\* \*\*Event-driven asynchronous workflows\*\*



The system separates responsibilities into independently deployable services while using RabbitMQ for asynchronous state-changing workflows and REST clients for real-time data aggregation.



\---



\# Architecture Overview



```text

&#x20;                        ┌───────────────────────┐

&#x20;                        │   Client / Frontend   │

&#x20;                        └───────────┬───────────┘

&#x20;                                    │

&#x20;                                 Port 8080

&#x20;                                    │

&#x20;                                    ▼

&#x20;                    ┌─────────────────────────────┐

&#x20;                    │       API GATEWAY           │

&#x20;                    │ JWT Authentication          │

&#x20;                    │ Header Injection             │

&#x20;                    └──────────────┬──────────────┘

&#x20;                                   │

&#x20;             ┌─────────────────────┼─────────────────────┐

&#x20;             │                     │                     │

&#x20;             ▼                     ▼                     ▼

&#x20;       ┌───────────┐        ┌────────────┐       ┌────────────┐

&#x20;       │   ALPHA   │        │    BETA    │       │   GAMMA    │

&#x20;       │ Security  │        │ Student    │       │ Career     │

&#x20;       │ Gateway   │        │ Assessment │       │ Roadmap    │

&#x20;       │ Auth      │        │ Recommend. │       │ PRS        │

&#x20;       │ Payment   │        │            │       │ Resume     │

&#x20;       └───────────┘        └────────────┘       │ Recruiter  │

&#x20;                                                 └────────────┘

&#x20;                                                        │

&#x20;                                                        │

&#x20;                                                  ┌─────▼─────┐

&#x20;                                                  │   DELTA   │

&#x20;                                                  │ AI Coach  │

&#x20;                                                  │ Notify    │

&#x20;                                                  │ Org       │

&#x20;                                                  │ Infra     │

&#x20;                                                  └───────────┘

```



\---



\# Team Allocation



| Member    | Focus Area                                   | Assigned Services                                                                         |

| --------- | -------------------------------------------- | ----------------------------------------------------------------------------------------- |

| \*\*ALPHA\*\* | Platform Security, Gateway \& Monetization    | `api-gateway`, `auth-service`, `payment-service`                                          |

| \*\*BETA\*\*  | Student Profile, Assessment \& Recommendation | `student-service`, `assessment-service`, `recommendation-service`                         |

| \*\*GAMMA\*\* | Career Execution, PRS \& Recruiter Platform   | `roadmap-service`, `prs-service`, `resume-service`, `recruiter-service`                   |

| \*\*DELTA\*\* | AI Assistant, Notifications \& Governance     | `ai-coach-service`, `notification-service`, `organization-service`, Docker/Infrastructure |



\---



\# Contributor Directory Structure



The `contributors` branch stores contribution snapshots under member-specific directories.



```text

CareerBridge\_Contributions/

│

├── Alpha-Bhupesh/

│   ├── api-gateway/

│   ├── auth-service/

│   └── payment-service/

│

├── Gamma-Sahil/

│   ├── roadmap-service/

│   ├── prs-service/

│   ├── resume-service/

│   └── recruiter-service/

│

└── Beta-Jayesh/

&#x20;   ├── student-service/

&#x20;   ├── assessment-service/

&#x20;   └── recommendation-service/

```



> \*\*Note:\*\* The contributor directories represent the assigned ownership areas and provide a clean separation for contribution/evaluation purposes.



\---



\# Service \& Port Reference



| Service                | Owner |   Port |

| ---------------------- | ----- | -----: |

| API Gateway            | Alpha | `8080` |

| Auth Service           | Alpha | `8081` |

| Student Service        | Beta  | `8082` |

| Assessment Service     | Beta  | `8083` |

| Recommendation Service | Beta  | `8084` |

| Notification Service   | Delta | `8085` |

| Organization Service   | Delta | `8087` |

| Roadmap Service        | Gamma | `8088` |

| PRS Service            | Gamma | `8089` |

| Recruiter Service      | Gamma | `8090` |

| Resume Service         | Gamma | `8091` |

| AI Coach Service       | Delta | `8092` |

| Payment Service        | Alpha | `8093` |



\---



\# ALPHA — Platform Security \& Monetization



\## Responsibilities



Alpha owns:



\* Central API Gateway

\* Authentication

\* JWT security

\* Identity propagation

\* Refresh token management

\* Subscription/payment infrastructure

\* Razorpay sandbox integration



\### Services



```text

Alpha-Bhupesh/

├── api-gateway/

├── auth-service/

└── payment-service/

```



\---



\## API Gateway



\*\*Port:\*\* `8080`



\*\*Package:\*\*



```text

com.careerbridge.gateway

```



\### Major responsibilities



\* Central entry point

\* JWT validation

\* Request authentication

\* Header sanitization

\* Trusted identity propagation

\* Swagger aggregation



\### Important files



```text

api-gateway/

└── src/main/

&#x20;   ├── java/com/careerbridge/gateway/

&#x20;   │   ├── GatewayApplication.java

&#x20;   │   ├── filter/

&#x20;   │   │   └── JwtAuthenticationFilter.java

&#x20;   │   └── config/

&#x20;   │       └── OpenApiConfig.java

&#x20;   │

&#x20;   └── resources/

&#x20;       └── application.yml

```



The `JwtAuthenticationFilter` validates HS384 JWT tokens and injects trusted downstream headers:



```text

X-User-Id

X-User-Role

X-User-Org-Id

X-User-Plan

```



Client-supplied versions of these headers are stripped before downstream forwarding.



\---



\## Auth Service



\*\*Port:\*\* `8081`



\*\*Database:\*\*



```text

careerbridge\_auth

```



\### Responsibilities



\* User registration

\* Login

\* BCrypt password encoding

\* JWT generation

\* Refresh token management

\* Refresh token revocation

\* Subscription claim updates

\* `student.registered` event publication



\### Key components



```text

AuthController

AuthService

AuthServiceImpl

JwtService

RefreshTokenService

User

RefreshToken

Role

UserRepository

RefreshTokenRepository

SecurityConfig

JwtConfig

RabbitMQConfig

AuthSubscriptionConsumer

```



\---



\## Payment Service



\*\*Port:\*\* `8093`



\*\*Database:\*\*



```text

careerbridge\_payment

```



\### Responsibilities



\* Payment plans

\* Razorpay order creation

\* Payment verification

\* Subscription history

\* HMAC-SHA256 signature verification

\* Subscription activation events



\### Event



```text

subscription.activated

```



This event is consumed by `auth-service` to update the user's subscription state.



\---



\# BETA — Student Profile \& Recommendation



\## Responsibilities



Beta owns:



\* Student onboarding

\* Student profile lifecycle

\* Profile completeness

\* Skill assessment

\* Assessment scoring

\* Career recommendation

\* Career catalog



\### Services



```text

Beta-Jayesh/

├── student-service/

├── assessment-service/

└── recommendation-service/

```



\---



\## Student Service



\*\*Port:\*\* `8082`



\*\*Database:\*\*



```text

careerbridge\_student

```



\### Responsibilities



\* Student profile creation

\* Education

\* Skills

\* Projects

\* Certificates

\* Resume information

\* Profile completion calculation



\### Key components



```text

StudentProfileController

StudentProfileService

StudentProfileServiceImpl

StudentProfile

Education

Skill

Project

Certificate

ProfileCompletionCalculator

StudentEventConsumer

```



The `ProfileCompletionCalculator` implements a weighted 100-point profile completion algorithm.



\### Consumed events



```text

student.registered

resume.generated

```



\---



\## Assessment Service



\*\*Port:\*\* `8083`



\*\*Database:\*\*



```text

careerbridge\_assessment

```



\### Responsibilities



\* Question management

\* Assessment attempts

\* Random question selection

\* Option shuffling

\* Weighted scoring

\* Assessment result persistence



\### Key components



```text

AssessmentController

QuestionController

AssessmentService

AssessmentServiceImpl

ScoringEngine

Category

Question

Option

AssessmentResult

AssessmentCompletedEvent

```



\### Published event



```text

assessment.completed

```



\---



\## Recommendation Service



\*\*Port:\*\* `8084`



\*\*Database:\*\*



```text

careerbridge\_recommendation

```



\### Responsibilities



\* Consume assessment completion events

\* Evaluate student skill results

\* Match against standardized career profiles

\* Rank career paths

\* Persist recommendations



\### Key components



```text

RecommendationController

RecommendationService

RecommendationServiceImpl

RecommendationEngine

CareerCatalog

AssessmentCompletedConsumer

RecommendationGeneratedEvent

```



The system uses \*\*7 standardized career paths\*\* in `CareerCatalog`.



\### Published event



```text

recommendation.generated

```



\---



\# GAMMA — Career Execution \& Recruiter Platform



\## Responsibilities



Gamma owns the complete career execution lifecycle:



\* Career roadmap

\* Milestone tracking

\* Placement Readiness Score

\* Resume generation

\* ATS scoring

\* PDF generation

\* Recruiter portal

\* Candidate search

\* Interview scheduling

\* Offer tracking

\* Placement metrics



\### Services



```text

Gamma-Sahil/

├── roadmap-service/

├── prs-service/

├── resume-service/

└── recruiter-service/

```



\---



\# Roadmap Service



\*\*Port:\*\* `8088`



\*\*Database:\*\*



```text

careerbridge\_roadmap

```



\### Responsibilities



\* Career roadmap generation

\* Milestone management

\* Progress calculation

\* Career-specific milestone data

\* Roadmap event publication



\### Key components



```text

RoadmapController

RoadmapService

RoadmapServiceImpl

RoadmapDataSeeder

RecommendationGeneratedConsumer

RoadmapUpdatedEvent

```



The service seeds:



```text

7 career paths

47 milestones

```



\### Consumed event



```text

recommendation.generated

```



\### Published event



```text

roadmap.updated

```



\---



\# PRS Service



\*\*Port:\*\* `8089`



\*\*Database:\*\*



```text

careerbridge\_prs

```



\## Placement Readiness Score



The PRS uses a weighted composite model:



```text

Assessment Performance    → 40%

Roadmap Progress          → 30%

Profile Completeness      → 20%

Resume Score              → 10%

```



```text

PRS =

&#x20;   Assessment × 0.40

&#x20; + Roadmap × 0.30

&#x20; + Profile × 0.20

&#x20; + Resume × 0.10

```



\### Key components



```text

PrsController

PrsService

PrsServiceImpl

StudentServiceClient

PrsEventConsumer

```



\### Consumed events



```text

student.registered

recommendation.generated

roadmap.updated

resume.generated

```



The service also supports organization-scoped leaderboard functionality.



\---



\# Resume Service



\*\*Port:\*\* `8091`



\*\*Database:\*\*



```text

careerbridge\_resume

```



\### Responsibilities



\* Resume creation

\* ATS scoring

\* Keyword matching

\* Career benchmark comparison

\* PDF generation

\* Resume storage

\* Student profile synchronization



\### Key components



```text

ResumeController

ResumeService

ResumeServiceImpl

AtsScorer

PdfRenderer

ResumeGeneratedEvent

```



The service uses \*\*OpenPDF\*\* to generate A4 resume PDFs.



Generated PDF binaries are stored in PostgreSQL `bytea` columns.



\### Published event



```text

resume.generated

```



\---



\# Recruiter Service



\*\*Port:\*\* `8090`



\*\*Database:\*\*



```text

careerbridge\_recruiter

```



\### Responsibilities



\* Recruiter/company onboarding

\* Job posting

\* Candidate search

\* Interview scheduling

\* Application tracking

\* Offer management

\* Placement outcome tracking

\* Recruiter statistics



\### Controllers



```text

RecruiterCompanyController

RecruiterJobController

RecruiterApplicationController

RecruiterCandidateController

RecruiterStatsController

```



\### REST clients



```text

StudentServiceClient

PrsServiceClient

```



\### Published events



```text

application.submitted

application.status.updated

placement.completed

```



\---



\# DELTA — AI, Notifications \& Infrastructure



\## Responsibilities



Delta owns:



\* AI Career Coach

\* Notifications

\* Organization management

\* Multi-tenancy

\* Docker infrastructure

\* RabbitMQ topology

\* External AI/resource integrations



\### Services



```text

ai-coach-service/

notification-service/

organization-service/

docker/

```



\---



\# AI Coach Service



\*\*Port:\*\* `8092`



\*\*Database:\*\*



```text

careerbridge\_ai\_coach

```



\*\*Datastore:\*\* MongoDB Atlas



\### External integrations



```text

Groq LLM

Tavily

YouTube

```



\### Internal REST clients



```text

RoadmapServiceClient

StudentServiceClient

PrsServiceClient

```



\### Controllers



```text

AiCoachChatController

MilestoneResourceController

```



\### Responsibilities



\* AI career guidance

\* Student-context-aware conversations

\* Milestone resources

\* Learning resource discovery



\---



\# Notification Service



\*\*Port:\*\* `8085`



\### Datastores



PostgreSQL:



```text

careerbridge\_notification

```



MongoDB:



```text

careerbridge\_notifications

```



\### Responsibilities



\* Email notifications

\* Gmail SMTP integration

\* In-app notification feed

\* Notification persistence

\* Recommendation notifications

\* Student registration notification bridge



\### Key components



```text

NotificationController

NotificationService

EmailService

NotificationEventConsumer

```



\---



\# Organization Service



\*\*Port:\*\* `8087`



\*\*Database:\*\*



```text

careerbridge\_organization

```



\### Responsibilities



\* Organization management

\* Department management

\* Multi-tenancy

\* Role-based access control

\* Organization-scoped data access



\### Controllers



```text

OrganizationController

DepartmentController

```



\---



\# Infrastructure



The root infrastructure includes:



```text

docker-compose.yml

docker/init.sql

```



The Docker Compose configuration orchestrates the application infrastructure and service stack.



PostgreSQL initialization provisions the project's databases.



RabbitMQ provides centralized event messaging through:



```text

careerbridge.exchange

```



\---



\# Database Ownership



\## PostgreSQL



| Database                      | Owner |

| ----------------------------- | ----- |

| `careerbridge\_auth`           | Alpha |

| `careerbridge\_payment`        | Alpha |

| `careerbridge\_student`        | Beta  |

| `careerbridge\_assessment`     | Beta  |

| `careerbridge\_recommendation` | Beta  |

| `careerbridge\_roadmap`        | Gamma |

| `careerbridge\_prs`            | Gamma |

| `careerbridge\_resume`         | Gamma |

| `careerbridge\_recruiter`      | Gamma |

| `careerbridge\_notification`   | Delta |

| `careerbridge\_organization`   | Delta |



\## MongoDB Atlas



| Database                     | Owner |

| ---------------------------- | ----- |

| `careerbridge\_ai\_coach`      | Delta |

| `careerbridge\_notifications` | Delta |



\---



\# RabbitMQ Event Architecture



All asynchronous event messaging uses:



```text

careerbridge.exchange

```



The exchange is configured as a durable topic exchange.



\## Event Flow



\### Student Registration



```text

auth-service

&#x20;    │

&#x20;    │ student.registered

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    ├──► student-service

&#x20;    ├──► notification-service

&#x20;    └──► prs-service

```



Purpose:



\* Automatically create student profile

\* Initialize PRS record

\* Create notification/audit information



\---



\### Assessment Completion



```text

assessment-service

&#x20;    │

&#x20;    │ assessment.completed

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    └──► recommendation-service

```



Purpose:



\* Trigger career recommendation generation



\---



\### Recommendation Generation



```text

recommendation-service

&#x20;    │

&#x20;    │ recommendation.generated

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    ├──► notification-service

&#x20;    ├──► roadmap-service

&#x20;    └──► prs-service

```



Purpose:



\* Notify student

\* Generate roadmap

\* Update PRS



\---



\### Roadmap Update



```text

roadmap-service

&#x20;    │

&#x20;    │ roadmap.updated

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    └──► prs-service

```



Purpose:



\* Update the roadmap component of PRS



\---



\### Resume Generation



```text

resume-service

&#x20;    │

&#x20;    │ resume.generated

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    ├──► prs-service

&#x20;    └──► student-service

```



Purpose:



\* Update resume component of PRS

\* Update student profile resume information



\---



\### Subscription Activation



```text

payment-service

&#x20;    │

&#x20;    │ subscription.activated

&#x20;    ▼

careerbridge.exchange

&#x20;    │

&#x20;    └──► auth-service

```



Purpose:



\* Update subscription plan

\* Refresh subscription-related identity claims



\---



\## Complete Event Reference



| Event                        | Publisher      | Consumers                  |

| ---------------------------- | -------------- | -------------------------- |

| `student.registered`         | Auth           | Student, Notification, PRS |

| `assessment.completed`       | Assessment     | Recommendation             |

| `recommendation.generated`   | Recommendation | Notification, Roadmap, PRS |

| `organization.created`       | Organization   | Reserved                   |

| `roadmap.updated`            | Roadmap        | PRS                        |

| `prs.updated`                | PRS            | Reserved                   |

| `application.submitted`      | Recruiter      | Reserved                   |

| `application.status.updated` | Recruiter      | Reserved                   |

| `placement.completed`        | Recruiter      | Reserved                   |

| `resume.generated`           | Resume         | PRS, Student               |

| `subscription.activated`     | Payment        | Auth                       |



\---



\# Synchronous REST Interactions



RabbitMQ handles asynchronous workflows.



REST clients are used for real-time reads and aggregation.



\## Resume → Student



```text

resume-service

&#x20;     │

&#x20;     │ GET /api/student/profiles/{id}

&#x20;     ▼

student-service

```



\## PRS → Student



```text

prs-service

&#x20;     │

&#x20;     │ GET /api/student/profiles/score/{id}

&#x20;     ▼

student-service

```



\## Recruiter → Student



```text

recruiter-service

&#x20;     │

&#x20;     │ GET /api/student/profiles/public

&#x20;     ▼

student-service

```



\## Recruiter → PRS



```text

recruiter-service

&#x20;     │

&#x20;     │ GET /api/prs/leaderboard

&#x20;     ▼

prs-service

```



\## AI Coach → Roadmap



```text

ai-coach-service

&#x20;     │

&#x20;     │ GET /api/roadmap/my

&#x20;     ▼

roadmap-service

```



\## AI Coach → Student



```text

ai-coach-service

&#x20;     │

&#x20;     │ GET /api/student/profile/me

&#x20;     ▼

student-service

```



\## AI Coach → PRS



```text

ai-coach-service

&#x20;     │

&#x20;     │ GET /api/prs/my

&#x20;     ▼

prs-service

```



\---



\# Security Architecture



Authentication is centralized at the API Gateway.



```text

Client

&#x20; │

&#x20; │ JWT

&#x20; ▼

API Gateway

&#x20; │

&#x20; ├── Validate JWT

&#x20; ├── Validate expiration

&#x20; ├── Remove spoofed headers

&#x20; └── Inject trusted identity headers

&#x20;         │

&#x20;         ▼

&#x20;    Microservices

```



Trusted headers include:



```text

X-User-Id

X-User-Role

X-User-Org-Id

X-User-Plan

```



The gateway uses HS384 JWT validation.



Downstream services operate on trusted identity information injected by the gateway.



\---



\# Multi-Tenancy



Organization-aware services use:



```text

X-User-Org-Id

```



to scope organization-specific operations.



Role-based access control distinguishes roles such as:



```text

SUPER\_ADMIN

ORG\_ADMIN

```



Organization administrators are restricted to their own organization's data.



\---



\# Reliability \& Fault Tolerance



The system follows a hybrid communication strategy.



\## Asynchronous operations



RabbitMQ is used for:



\* Registration workflows

\* Recommendation generation

\* Roadmap updates

\* Resume generation

\* Subscription activation

\* Notification workflows



Durable exchanges and queues support reliable event delivery.



Consumers use database constraints and idempotency checks where required.



\---



\## Synchronous operations



REST clients use explicit connection/read timeouts.



The architecture specifies approximately:



```text

3 second connect timeout

3 second read timeout

```



Fail-soft behavior is used where appropriate.



For example, if PRS data is temporarily unavailable during recruiter candidate aggregation, the system can return the candidate with a sentinel score such as:



```text

\-1.0

```



instead of failing the complete request.



\---



\# Evaluator Demonstration Guide



\## ALPHA Demo



\### Gateway



Open:



```text

http://localhost:8080/swagger-ui.html

```



Demonstrate:



```text

POST /api/auth/register

POST /api/auth/login

```



Show:



\* JWT generation

\* Authentication

\* Gateway filtering

\* Identity header injection



\### Payment



Demonstrate:



```text

POST /api/payment/orders

```



Follow with payment signature verification.



\---



\# BETA Demo



\## Student Profile



Demonstrate:



```text

GET /api/student/profile/me

```



Show:



\* Profile creation

\* Skills

\* Projects

\* Education

\* Profile completeness



\## Assessment



Start an assessment:



```text

POST /api/assessment/attempts/start

```



Submit:



```text

POST /api/assessment/attempts/submit

```



\## Recommendation



Demonstrate:



```text

GET /api/recommendation/my

```



Show the career recommendations generated from assessment results.



\---



\# GAMMA Demo



\## Roadmap



Demonstrate milestone completion:



```text

POST /api/roadmap/milestones/{id}/complete

```



Show:



\* Milestone status

\* Progress calculation

\* `roadmap.updated`



\## PRS



Retrieve the current score:



```text

GET /api/prs/my

```



Explain the:



```text

40 / 30 / 20 / 10

```



weighted calculation.



\## Resume



Generate:



```text

POST /api/resume/generate

```



Download:



```text

GET /api/resume/my/download

```



Show:



\* ATS score

\* Generated PDF

\* Resume storage

\* `resume.generated`



\## Recruiter



Demonstrate:



\* Company onboarding

\* Job creation

\* Candidate search

\* Interview scheduling

\* Application tracking

\* Offer outcome

\* Placement metrics



\---



\# DELTA Demo



\## AI Coach



Demonstrate:



```text

POST /api/ai-coach/chat

```



Show:



\* Student context

\* Groq LLM response

\* Career guidance

\* Milestone resources



\## Notifications



Show:



```text

GET /api/notification/feed

```



and unread notification count.



Demonstrate:



\* In-app notifications

\* Gmail SMTP notifications

\* MongoDB feed persistence



\## Organization



Demonstrate:



\* Organization creation

\* Department management

\* Tenant isolation

\* Role-based access control



\## Infrastructure



Show:



```bash

docker compose ps

```



and explain the service/infrastructure stack.



\---



\# Evaluator Technical Defense



\## Q1 — Why RabbitMQ instead of REST everywhere?



\*\*Answer:\*\*



CareerBridge uses a hybrid communication architecture.



State-changing workflows and asynchronous operations use RabbitMQ topic events. This keeps services loosely coupled and allows consumers to process events independently.



REST is reserved primarily for real-time read aggregation where an immediate response is required.



\---



\## Q2 — How is authentication handled?



\*\*Answer:\*\*



Authentication is centralized at the API Gateway.



The gateway validates the HS384 JWT, verifies expiration, removes client-supplied identity headers, and injects trusted identity headers into downstream requests.



This keeps authentication responsibilities centralized.



\---



\## Q3 — How is multi-tenancy handled?



\*\*Answer:\*\*



Organization identity is propagated through the authenticated user's identity context.



Services use:



```text

X-User-Org-Id

```



to enforce organization-scoped access.



Administrative roles can access broader scopes according to their authorization level.



\---



\## Q4 — What happens if a service goes down?



\*\*Answer:\*\*



Asynchronous workflows use durable RabbitMQ infrastructure so consumers can process messages when they become available.



Synchronous REST clients use explicit timeouts and fail-soft behavior where appropriate to prevent one unavailable service from causing an unnecessary system-wide failure.



\---



\# Contribution History



The `contributors` branch is organized around individual ownership areas.



Current contribution commits include:



| Contributor                   | Area  | Commit                                          |

| ----------------------------- | ----- | ----------------------------------------------- |

| \*\*Bhupesh-Patil\*\*             | Alpha | `feat: add Bhupesh Alpha service contributions` |

| \*\*Smaddy98\*\*                  | Gamma | `feat: add Sahil Gamma service contributions`   |

| \*\*jayeshsambarecmfeb26-lang\*\* | Beta  | `feat: add Jayesh Beta service contributions`   |



The branch preserves individual Git commit attribution for contribution/evaluation purposes.



\---



\# Development Notes



\## Java



```text

Java 21

```



\## Framework



```text

Spring Boot 4.1.0

```



\## Build System



```text

Maven

```



\## Databases



```text

PostgreSQL

MongoDB Atlas

```



\## Messaging



```text

RabbitMQ

```



\## Gateway



```text

Spring Cloud Gateway

```



\## Authentication



```text

JWT

HS384

BCrypt

```



\## Infrastructure



```text

Docker

Docker Compose

```



\---



\# Branch Purpose



The `contributors` branch is intended to preserve a structured representation of team-owned backend contributions.



It should be treated as a \*\*contribution/evaluation reference branch\*\*, while the main application branches remain responsible for the integrated CareerBridge 2.0 application.



\### Contribution structure



```text

contributors

&#x20;   │

&#x20;   ├── Alpha

&#x20;   │    └── Platform Security / Gateway / Payment

&#x20;   │

&#x20;   ├── Beta

&#x20;   │    └── Student / Assessment / Recommendation

&#x20;   │

&#x20;   ├── Gamma

&#x20;   │    └── Roadmap / PRS / Resume / Recruiter

&#x20;   │

&#x20;   └── Delta

&#x20;        └── AI / Notification / Organization / Infrastructure

```



\---



\# Final Architecture Summary



CareerBridge 2.0 combines:



```text

&#x20;               ┌─────────────────────┐

&#x20;               │       Frontend      │

&#x20;               └──────────┬──────────┘

&#x20;                          │

&#x20;                          ▼

&#x20;               ┌─────────────────────┐

&#x20;               │    API Gateway      │

&#x20;               │   JWT / Identity    │

&#x20;               └──────────┬──────────┘

&#x20;                          │

&#x20;       ┌──────────────────┼──────────────────┐

&#x20;       │                  │                  │

&#x20;       ▼                  ▼                  ▼

&#x20;    ALPHA               BETA               GAMMA

&#x20; Security/Auth       Student/Assess.    Career/Recruiter

&#x20; Payment             Recommendation     Roadmap/PRS/Resume

&#x20;       │                  │                  │

&#x20;       └──────────────────┼──────────────────┘

&#x20;                          │

&#x20;                   RabbitMQ Events

&#x20;                          │

&#x20;                          ▼

&#x20;                       DELTA

&#x20;               AI / Notifications /

&#x20;               Organization / Infra

```



The architecture therefore separates:



\* \*\*Security \& monetization\*\*

\* \*\*Student intelligence\*\*

\* \*\*Career execution\*\*

\* \*\*AI \& governance\*\*



while connecting them through \*\*event-driven messaging and controlled synchronous REST communication\*\*.



\---



\## CareerBridge 2.0



\*\*Backend Microservices Architecture\*\*



```text

Java 21

Spring Boot

Spring Cloud Gateway

PostgreSQL

MongoDB Atlas

RabbitMQ

Docker

JWT

Maven

```



> \*\*Contributors Branch — Team Contribution \& Evaluation Reference\*\*



