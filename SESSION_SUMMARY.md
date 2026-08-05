# CareerBridge Frontend — Session Summary (for next chat context)

## Critical ground rules (established after mistakes — don't repeat them)
- **Work directly inside `D:\Software\CareerBridge_2.0\careerbridge-frontend`. Never create a separate folder or git worktree for frontend work.** This was done wrong earlier in the session (worktrees at `CareerBridge_2.0-auth-pages` and `CareerBridge_2.0-onboarding`) and had to be undone — all that work was copied back into the main folder and the worktrees deleted.
- User wants **a separate branch per new page**, but built by switching branches *in this same folder* — not by creating new directories.
- The main folder's git branch may be sitting on something unrelated (e.g. `feature/subscription-invoice`, someone else's in-progress backend work) — check `git status`/`git branch` before assuming state, and don't disturb uncommitted files that aren't yours.
- Live-test everything against the real backend (Docker) before declaring something done — several bugs in this session were only caught by actually clicking through the running app, not by reading the code.

## What's built and working (all in `careerbridge-frontend/`)

| Page | Route | Notes |
|---|---|---|
| Homepage | `/` | Marketing page, design-system based |
| Register | `/register` | Real POST to auth-service, redirects to `/onboarding` on success |
| Login | `/login` | Real POST to auth-service; **smart redirect** (see below), show/hide password toggle |
| Forgot password | `/forgot-password` | 4-step: email → 4-digit OTP → new password → done. Full backend built for this (see below) |
| Onboarding | `/onboarding` | 4-step wizard: Education → Skills → Basic info → Assessment handoff. Writes to real student-service endpoints |

Not built yet: `/assessment`, `/dashboard`, `/recruiter-console`, `/college-dashboard` — these are dead links today (blank page if you land on them).

## Shared design system (`src/components/ui/index.jsx`)
Button, Input (with show/hide password toggle), Field, Checkbox, Select, Textarea, Switch, Tag, Badge, Alert, Logo, ScoreRing (color-graded by score: red/amber/blue/green at 20/40/60/80% thresholds, matches letter grade), StatTile, ProgressMeter, ListRow, OtpInput (4-digit code entry), Icon (react-icons/lu wrapper).

## Backend changes made this session
- **auth-service**: new `password_reset_otps` table + `POST /api/auth/forgot-password`, `/forgot-password/verify-otp`, `/forgot-password/reset`. Publishes `password.reset.requested` and `password.changed` events. **Had to add these 3 paths to `SecurityConfig`'s permitAll list** (auth-service has its own Spring Security chain, separate from the gateway's) — this was a real bug found live (401 with Spring Security's default error page).
- **notification-service**: two new RabbitMQ consumers (own queues) sending the OTP email and the "password changed" confirmation email, calling `EmailService` directly (no `NotificationRecord`/in-app notification — these are transactional, not feed items).
- **api-gateway**: 
  - Added the 3 forgot-password paths to `gateway.public-paths` in `application.yml`.
  - **CORS fix** — found and fixed *twice*: once as my own addition, then found a *different* independently-written `CorsConfig.java` already in the main folder with a real ordering bug (bare `@Bean CorsFilter` with `@Order` loses a tie against `JwtAuthenticationFilter`'s own `@Order(HIGHEST_PRECEDENCE)` non-deterministically). Fixed by wrapping in `FilterRegistrationBean` — this reliably wins the ordering tie. This is the version currently in the codebase.

## Frontend logic worth knowing
- **`src/utils/postLoginRedirect.js`** — students get sent to `/onboarding` (no education+skills yet), `/assessment` (onboarded but no recommendation yet — recommendation only exists after assessment completes), or `/dashboard` (everything done). Non-student roles go straight to their own console, unaffected by this chain.
- **Onboarding page persistence**: on load, fetches the real profile and **prefills** education/skills/basic-info from what's already saved, and **resumes at the first incomplete step** (not always step 0).
- **Duplicate-submission bug (fixed)**: Continue used to re-POST every education/skill entry including ones already saved, which the backend correctly rejects — this blocked navigation with "Skill 'X' is already on this profile." Now tracks what was already-saved on load and only submits new entries.
- **Known accepted limitation**: there's no update/edit endpoint for education or skills in student-service (only add). Editing an already-saved entry and clicking Continue silently won't persist the edit — it just gets skipped (safer than erroring). Fixing this for real needs a new backend PATCH endpoint — not done, out of scope so far.

## Bugs found and fixed via live testing (not just code review)
1. Progress ring stuck at 0%/Grade F even with data filled — was gated behind `step > N` instead of reflecting real-time state.
2. Sidebar education summary only ever showed qualification #1, ignoring additional ones.
3. Progress ring "not filling" visually — root cause was a `filter: brightness(0) invert(1)` CSS hack that zeroed every pixel to black then inverted everything to the *same* white, making track and fill indistinguishable. Replaced with a proper `tone="inverse"` prop giving genuinely different colors.
4. Onboarding form didn't remember anything on revisit (see persistence above).
5. Duplicate-submission "Something went wrong" bug (see above).
6. CORS ordering bug, twice (see backend changes above).
7. Content column in onboarding was left-aligned instead of centered in its pane — fixed by adding `alignItems: center` + matching `maxWidth` on tabs/divider/footer.

## Environment notes
- Full Docker stack (15 containers: 12 backend services + gateway + postgres + rabbitmq) is rebuilt and running from the main folder with all the above fixes included. `docker compose --env-file .env up -d --build` from `D:\Software\CareerBridge_2.0`.
- Frontend dev server: `npm run dev` from `careerbridge-frontend/`, typically lands on `5173` (auto-increments if occupied by an unrelated local project).
- `contextLoads` test failures in auth-service/notification-service are a **pre-existing, already-documented** local DB credential mismatch (`root`/`root` hardcoded vs whatever Postgres actually has) — not a regression from anything built this session.
- Docker Desktop crashed once mid-session (unrelated to anything done here) and was recovered; containers auto-restarted with the same images.
- There was significant confusion earlier from a *different, concurrent* Claude session also working in this same repo on `feature/subscription-invoice` (payment/notification invoice work) — that led to accidentally creating worktrees to avoid collision, which the user then correctly told me to undo. If branch/folder state looks unexpected, check for this before assuming something broke.
