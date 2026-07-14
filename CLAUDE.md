# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is the backend for **한걸음 (Hangeoreum)**, a Korean-learning web app. Right now the repo is an **unmodified Spring Initializr skeleton** — one `@SpringBootApplication` class, no controllers, entities, or migrations yet. The base package and artifact are still the Initializr placeholder `com.fornosalice.forno_salice_api` / `forno-salice-api`; the plan (see below) calls for renaming this to `com.hangeoreum.api` before real feature work starts — check whether that rename has happened before assuming the package name in new code.

The actual product/architecture plan lives one level up, in `../docs/plan/` (sibling `docs` repo alongside `coreano-api` and `coreano-front`). Treat `../docs/plan/backend/*.md` as the spec for what this service should become; the code here does not implement it yet.

## Commands

Use the Maven wrapper (`./mvnw` in Git Bash, `mvnw.cmd` in cmd/PowerShell).

```bash
./mvnw spring-boot:run          # run the app (auto-starts postgres via compose.yaml — see below)
./mvnw test                     # run all tests
./mvnw test -Dtest=ClassName                 # single test class
./mvnw test -Dtest=ClassName#methodName      # single test method
./mvnw clean package            # build the jar
```

`spring-boot-docker-compose` is on the classpath (runtime, optional), so `spring-boot:run` / running the app locally will automatically bring up the `postgres` service defined in `compose.yaml` — no need to `docker compose up` manually.

`compose.yaml` mounts `../docs/plan/database/schema.sql` (path relative to this module) as the Postgres init script — that file, not a Flyway migration yet, is the current source of truth for the schema (36 tables). Flyway is a declared dependency but `src/main/resources/db/migration` doesn't exist yet; the plan is for `V1__init.sql` to be this same schema.sql.

`src/main/resources/application.properties` currently only sets `spring.application.name` — no datasource/JPA/security config exists yet. Don't assume any wiring beyond what's actually in that file.

## Target architecture (per `../docs/plan/backend/00-architecture.md`)

Modular monolith, one Maven module, split into **bounded contexts**, each internally layered the same way:

```
com.hangeoreum.api/
├── shared/          # ErrorResponse, exceptions, security, config, Spring events
├── identity/        # auth, JWT, OAuth (Google/Kakao), user settings
├── learning/        # courses, lessons, alphabet, story
├── vocabulary/       # words, SM-2 spaced repetition, review sessions
├── media/           # clips, subtitles, Immerse
├── gamification/     # XP, streak, achievements
├── notification/
├── billing/          # Stripe, AccessPolicy / paywall
└── admin/            # thin: controllers over the other contexts' services
```

Each context follows:
```
{context}/
├── domain/          # JPA-entity-as-aggregate, value objects, domain services, repo interfaces, domain/event/
├── application/     # @Service use cases, transactional boundaries
├── infrastructure/  # Spring Data repos, external adapters (Stripe, storage)
└── api/             # @RestController, request/response DTOs, mappers
```

Key rules from the plan:
- **Contexts talk to each other only through application services or Spring application events** — never by reaching into another context's repository. Example: `LessonCompletedEvent` is consumed by `gamification` (award XP) and `vocabulary` (add words).
- Domain logic that's algorithmically interesting (SM-2 scheduling, `AccessPolicy`, streak calculation) should be plain classes, unit-testable without Spring.
- JPA entities double as domain entities (pragmatic for a monolith), but invariants are enforced through entity methods — no public setters for state that has rules.
- Security: JWT access token (15 min, header) + refresh token (30 days, httpOnly cookie, hashed in `refresh_tokens`, rotated on use). OAuth2 client for Google/Kakao populates `oauth_links`. Roles `USER`/`ADMIN`, with `/api/v1/admin/**` requiring `ADMIN`. Subscription gating goes through `billing`'s `AccessPolicy` (e.g. a `@RequiresPro` aspect on Story/Immerse endpoints); Free-tier limits go through `daily_activity`.
- API conventions: base path `/api/v1`; snake_case in Postgres, camelCase in JSON; error body shape `{ "code", "message", "details" }` (must match the frontend's `core/error` handling); status codes 400 (validation), 401, 403 (`PRO_REQUIRED` / `LIMIT_REACHED` / `FORBIDDEN`), 404, 409; offset pagination `?page=&size=` → `{content, totalElements, page}`, except the Immerse feed which is cursor-based. All input validated with `jakarta.validation`; springdoc/Swagger is meant to be the contract source for the frontend.

Per-context specs, one file each in `../docs/plan/backend/`: `01-identity.md`, `02-learning.md`, `03-vocabulary.md`, `04-media.md`, `05-gamification.md`, `06-billing.md`, `07-admin.md`, `08-notifications.md`. Table ownership per context is listed in `../docs/plan/README.md`. Full DB design/ER is in `../docs/plan/database/database-design.md`.

Build order per the plan: skeleton (package rename, Flyway V1, JWT security, GlobalExceptionHandler, Swagger, Testcontainers) → Identity → Learning read model → Vocabulary/SM-2 → Gamification listeners → Media/Story/Immerse → Notifications → Billing/Stripe → Admin (incremental).

"Definition of done" per context, per the plan: domain unit tests + a Testcontainers-backed controller integration test, Swagger annotations, forward-only Flyway migrations, transactional boundaries kept in the application layer, N+1s checked (fetch join / `EntityGraph`).

## Working conventions (from `.codex/skills/spring-senior` and `.codex/skills/postgres-senior`)

- Trace the request flow controller → service → repository/entity before editing anything.
- Grep for the same behavior elsewhere in `src/main/java` first and match the existing pattern rather than inventing a new one.
- Don't add a new layer, mapper, helper, or config class unless repetition or a framework constraint actually forces it.
- Constructor injection; prefer Spring Boot auto-configuration/properties over manual bean wiring.
- Treat every endpoint change as a security change: verify who can call it, which authorities/claims are required, and that unauthenticated/forbidden responses are still correct.
- Keep entity changes and Flyway migrations in lockstep; migrations are forward-only (don't edit ones that may already be applied elsewhere) and scoped to one endpoint/concern at a time.
- Prefer Spring Data derived queries over hand-written JPQL/native SQL unless derivation genuinely can't express the query.
