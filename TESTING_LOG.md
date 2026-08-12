# CareerBridge — Testing Log

Compiled from the project's maintained records (`CLAUDE.md`) for evaluator review. Two kinds of
testing are tracked separately below, because they prove different things:

- **Unit tests** — JUnit 5 + Mockito, run via `mvnw test` per service. These prove logic in
  isolation (mocked repositories, mocked HTTP clients). A green suite does **not** prove a queue
  exists, a downstream service is reachable, or a schema migration succeeded against real data —
  several incidents in this project were unit-test-green while broken live (see `ai_incident_log.md`).
- **Live/integration verification** — exercised against the real Docker stack (Postgres, MongoDB
  Atlas, RabbitMQ, and the other services), by hand, with the specific checks and outcomes each
  service's section notes below.

Frontend (`careerbridge-frontend/`) has no unit test suite configured (`package.json` has no
`test` script) — verification there is `npm run lint` (oxlint) and `npm run build` passing cleanly,
plus manual browser walkthroughs of each page as it was built.

## Summary

| Service | Unit tests | Live/integration verified |
|---|---|---|
| auth-service | 61 | Yes — registration, login, refresh, JWT round-trip, admin endpoints |
| student-service | 35 | Yes — profile CRUD, `student.registered` consumer, `resumeUrl` consumer |
| assessment-service | 59 | Yes — full attempt flow, admin question-bank CRUD |
| recommendation-service | 26 | Partial — logic verified; `recommendation.generated` delivery not yet confirmed against a live broker |
| notification-service | 28 | Partial — REST endpoints and datastores verified live; full event chain (`student.registered` → `recommendation.generated` → email/Mongo) not yet run end-to-end |
| api-gateway | 34 | Yes — JWT validation, header injection/stripping, spoofing blocked, routing to all 12 services |
| organization-service | 13 | Yes — RBAC, soft delete, `organization.created` publish |
| roadmap-service | 16 | Partial — seeding and REST verified live; `recommendation.generated` → roadmap event leg not yet confirmed against a live broker |
| prs-service | 26 | Yes — full 4-event composite score, tenant-scoped leaderboard, fail-soft client with student-service stopped |
| recruiter-service | 129 | Yes — candidate search, applications, interviews, offers/placement, both fail-soft clients with targets stopped |
| resume-service | 53 | Yes — PDF generation/download, ATS scoring, both consumer queues, fail-soft client stopped |
| ai-coach-service | 53 | Yes — full 47-document catalog refresh against real Tavily/YouTube, real Groq conversations, roadmap-service outage handling |
| payment-service | 96 | Yes — real Razorpay sandbox order, real card payment, signature verification, idempotent re-verify, forged-signature rejection |
| **Total** | **629** | 10 of 13 services fully verified live, 3 partially |

## Per-service detail

### auth-service — 61 unit tests
JWT auth (HS384), refresh-token rotation with DB-backed revocation, BCrypt hashing, admin user
management (list/role-change/deactivate/activate/stats), RabbitMQ `student.registered` publisher
and `subscription.activated` consumer.
- Live: registration → login → refresh → logout cycle exercised against real Postgres.
- Live: admin endpoints exercised with real SUPER_ADMIN and ORG_ADMIN tokens.
- Live: `subscription.activated` → JWT `plan` claim confirmed via a real payment-service order (see payment-service below).

### student-service — 35 unit tests
Profile CRUD, education/skills/projects/certificates/experience, profile completion scoring,
avatar/cover/certificate file upload, public candidate search.
- Live: `student.registered` consumer auto-creates the profile row on real registration.
- Live: `resume.generated` consumer confirmed to move `profileCompletionPercentage` 35 → 50 on a real event.

### assessment-service — 59 unit tests
Question bank (seeded), 3-section attempt flow (Aptitude / Domain Knowledge / Soft Skills),
weighted scoring, career matching, admin CRUD on the question bank (add/edit/activate/deactivate).
- Live: a real attempt was started, answered, and submitted end to end through the gateway.
- Live: admin question CRUD exercised with a real SUPER_ADMIN token.

### recommendation-service — 26 unit tests
Career ranking engine, tie-break logic, history tracking, publishes `recommendation.generated`.
- **Outstanding**: delivery of `recommendation.generated` to its three consumers has not yet been
  confirmed against a running RabbitMQ broker (queue-exists-with-traffic check).

### notification-service — 28 unit tests
Dual-datastore (Postgres audit + MongoDB Atlas in-app feed), Gmail SMTP, unread count, mark-as-read.
- Live: MongoDB Atlas connectivity confirmed with a real `ping`.
- Live: all three REST endpoints exercised against a running instance.
- **Outstanding**: the full event chain (`student.registered` → contact row → `recommendation.generated`
  → real email + Mongo document) needs the broker running to confirm end to end.

### api-gateway — 34 unit tests
Servlet-based JWT filter, identity header injection (`X-User-Id`/`X-User-Role`/`X-User-Org-Id`/`X-User-Plan`),
routing to all 12 backend services, public-path allowlist.
- Live: header spoofing blocked on both protected and public routes (two dedicated tests + manual `curl` reproduction).
- Live: routed real traffic to every downstream service through the gateway.

### organization-service — 13 unit tests
Organizations + departments, RBAC on `X-User-Role`/`X-User-Org-Id`, soft delete.
- Live: verified through the gateway, including a blocked header-spoof attempt.

### roadmap-service — 16 unit tests
7 career templates / 47 milestones seeded via `CommandLineRunner`, milestone completion, publishes `roadmap.updated`.
- Live: seeding confirmed idempotent by restarting the service twice against real Postgres (7/47 both times).
- Live: REST endpoints exercised.
- **Outstanding**: `recommendation.generated` → roadmap creation not yet confirmed against a live broker.

### prs-service — 26 unit tests
Placement Readiness Score (40/30/20/10 weighted composite), tenant-scoped leaderboard, 4 RabbitMQ consumers.
- Live: full score composite verified against real events on all four inputs.
- Live: fail-soft `StudentServiceClient` verified with student-service deliberately stopped (score held, no hang).
- Live: leaderboard scoping verified with two real colleges, each admin seeing only their own students.

### recruiter-service — 129 unit tests
Company/job/application/interview management, candidate search (2 cross-service HTTP calls), offer
tracking and placement stats.
- Live: full candidate search, application, and multi-round interview flow exercised end to end.
- Live: both fail-soft clients (student-service, prs-service) verified with targets deliberately stopped.
- Live: offer flow — extend, correct, accept — and the resulting `placement.completed` event confirmed in logs.
- Live: every guard exercised (wrong-recruiter 403, wrong-student 403, terminal-outcome 400, frozen-terms 400, unknown-enum 400).
- Live: `GET /stats/placement` confirmed fail-closed to zeros with prs-service stopped, while `/stats/my` kept returning real numbers.

### resume-service — 53 unit tests
Resume generation (OpenPDF), ATS scoring against career keyword maps, versioned storage in Postgres `bytea`.
- Live: a real PDF was generated and downloaded.
- Live: fail-soft client verified with student-service deliberately stopped (503 in ~3.4s, not a hang).
- Live: both consumer queues (prs-service, student-service) confirmed via a probe on the routing key.

### ai-coach-service — 53 unit tests
Shared milestone resource catalog (Tavily + YouTube), Groq-backed chat coach, MongoDB only (no Postgres/RabbitMQ).
- Live: full 47-document catalog refresh run against real Tavily and YouTube APIs — 47/47 documents, zero empty, all 7 careers, confirmed directly in Atlas via `mongosh`.
- Live: two real multi-turn Groq conversations, confirming chat history actually reaches the model.
- Live: roadmap-service outage handling confirmed (503 in ~3s, not a hang).
- Live: cross-student access confirmed as a genuine 404, not 403.

### payment-service — 96 unit tests
Razorpay-backed subscription billing (hand-rolled `RestClient` + HMAC-SHA256, no SDK), plan catalog,
order/verify/reconcile flow.
- Live: a real Razorpay sandbox order was created, paid with a real test card through Checkout.js, and verified.
- Live: the hand-rolled signature verifier matched Razorpay's own HMAC exactly.
- Live: `users.subscription_plan`/`subscription_expiry` confirmed changed in Postgres, and a refreshed JWT carried the new `plan` claim.
- Live: idempotent re-verify (no duplicate subscription row), a forged signature rejected with 400, and `reconcile` correctly 409'd against a real unpaid order.
- **One real bug found and fixed during this verification** — see `ai_incident_log.md` (2026-08-03).

## CI/CD

- Jenkins auto-deploy on push to `main` (`Jenkinsfile` at repo root) requires a GitHub webhook
  pointed at `http://<elastic-ip>:9090/github-webhook/` — this is **not** created by the
  "GitHub hook trigger for GITScm polling" checkbox in the Jenkins job itself, that only makes
  Jenkins listen. The webhook has to be added by hand under the repo's Settings → Webhooks.
  Missing this meant every push after the pipeline was first set up silently never built anything,
  with no error anywhere — Jenkins showed a healthy last build from whenever it was last run
  manually, and nothing in git or GitHub surfaced the gap.
- Confirmed 2026-08-12: webhook added under repo Settings → Webhooks, GitHub's ping delivery to
  Jenkins showed a live green success. End-to-end confirmation (a real push actually starting a
  new Jenkins build) still pending as of this commit.

## Known gaps

- `recommendation.generated` and `roadmap.updated` (roadmap-service side) event delivery is proven
  by unit tests and by the consumers existing, but not yet confirmed against a live broker with a
  non-zero delivered-message count in the RabbitMQ UI.
- Frontend has no automated test suite — verification is lint + build + manual browser walkthroughs.
- Unit test counts above reflect the state at the time each service's section in `CLAUDE.md` was
  last updated. Run `mvnw test` in any service directory to reproduce the current count directly.
