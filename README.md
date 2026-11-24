<div align="center">

# Arca Backend

**A versioned, moderated home for academic resources.**

[![Backend tests](https://github.com/Mingotrees/arca/actions/workflows/test.yml/badge.svg?branch=develop)](https://github.com/Mingotrees/arca/actions/workflows/test.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396?style=flat-square)
![Spring Boot 3.5.5](https://img.shields.io/badge/Spring_Boot-3.5.5-6DB33F?style=flat-square)

[Features](#features) · [Get started](#get-started) · [API](#api) · [Development](#development) · [Deployment](#deployment) · [Roadmap](#roadmap)

</div>

Arca is a REST API for sharing academic posts and attachments, organized by school, department, and subject. It supports moderated publishing, version history, voting, and personal saved collections called **vaults**. This repository contains the backend; frontend integration is separate.

## Features

- **Accounts and profiles** — registration, login, JWT access tokens, persisted refresh sessions, BCrypt passwords, profile editing, avatars, and user search.
- **Profile metadata and statistics** — UTC user/subject timestamps and authenticated aggregate profile endpoints, with approved/latest/non-deleted post eligibility.
- **Password recovery** — configurable email reset links, hashed single-use tokens, refresh-session revocation and database-backed rate limits; enable after SMTP/frontend setup.
- **Academic taxonomy** — schools, departments, subjects, aliases, and administrator-managed relationships.
- **Versioned posts** — approval/rejection queues, revision history, owner/admin edits, and logical-post soft deletion that hides every version while retaining stored data.
- **Post discovery** — full-text title/content search, combined subject/department/author/date filters, deterministic sorts, and bounded pagination through `/api/v1/posts/search`.
- **Resource sharing** — JPEG, PNG, and PDF post attachments, authenticated downloads, upvotes/downvotes, and labeled vault entries.
- **Resource security** — shared post visibility checks, bounded content-validated uploads, canonical storage containment, transactional avatar replacement, and startup JWT/CORS validation.
- **Administration** — account and role management, user soft deletion, banned-email controls, admin-only dashboard aggregates, and transaction-aware audit history.
- **Optional homepage modules** — scheduled global announcements and recent-vote/approval-age trending, independently feature-flagged off by default.
- **Operational tooling** — Flyway migrations, OpenAPI documentation, MySQL-backed integration tests, correlated deletion logs, and admin-only health/metrics endpoints.

Built with **Java 21**, **Spring Boot 3.5.5**, **Spring MVC**, **Spring Data JPA**, **Spring Security**, and **MySQL 8.4**. The application connects using the MariaDB JDBC driver.

## Get started

### Prerequisites

- JDK **21** and Git.
- Docker with the **Compose plugin** for the local database and Testcontainers tests.
- OpenSSL, or another tool that can generate a Base64-encoded random secret.

The Maven wrapper is included; a separate Maven installation is unnecessary. Commands below use a POSIX shell; on Windows, use WSL/Git Bash or adapt commands and use `mvnw.cmd`.

### 1. Clone and configure

```sh
git clone --branch develop https://github.com/Mingotrees/arca.git
cd arca
cp .env.example .env
```

Populate `.env` with local settings. Add `DB_ROOT_PASSWORD`, which Compose requires but the example file does not currently list:

```dotenv
DB_URL=jdbc:mariadb://localhost:3306/arca
DB_USERNAME=arca
DB_PASSWORD=local-app-password
DB_ROOT_PASSWORD=local-root-password

SECRET_KEY=REPLACE_WITH_GENERATED_BASE64_SECRET
ACCESS_TOKEN_EXPIRY=900000
REFRESH_TOKEN_EXPIRY=604800000
PORT=20255

USER_NAME=local-framework-user
USER_PASSWORD=local-framework-password
```

Generate the `SECRET_KEY` value with `openssl rand -base64 32` and paste it into `.env`. It must decode to at least 32 random bytes. These example passwords are for local development only; `.env` is ignored by Git.
Startup rejects missing, malformed or undersized signing secrets. Keep a deployment's existing valid secret to preserve issued JWTs.

Token lifetimes are **milliseconds**: the examples above mean 15 minutes and 7 days. `USER_NAME`/`USER_PASSWORD` populate existing Spring Security framework settings; they do **not** create an Arca account or admin user.

### 2. Start MySQL and the API

```sh
docker compose -p arca up -d --wait mysql
./mvnw spring-boot:run
```

Compose starts **only MySQL**, bound to `127.0.0.1:3306`, with a persistent database volume. The application loads `.env` from the repository root and defaults to the `dev` profile. On an empty database, Flyway applies V1–V11 before Hibernate validates the schema.

| Service | Local URL |
| --- | --- |
| REST API | `http://localhost:20255/api/v1` |
| Swagger UI | [localhost:20255/swagger-ui/index.html](http://localhost:20255/swagger-ui/index.html) |
| Generated OpenAPI | [localhost:20255/v3/api-docs](http://localhost:20255/v3/api-docs) |
| Health / metrics | `/actuator/health`, `/actuator/metrics` — admin JWT required |

> [!IMPORTANT]
> **Upgrading an existing database?** Follow the [migration and rollout runbook](docs/phase-1-operations.md) before starting this version. Nonempty, unversioned schemas require a reviewed baseline at version 1. Hibernate does not update the schema automatically, and migrations reject ambiguous legacy post identities.

### 3. Create an account and sign in

```sh
curl -sS http://localhost:20255/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"first_name":"Alex","last_name":"Student","email":"alex@example.test","password":"LocalExample123!"}'

curl -sS http://localhost:20255/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex@example.test","password":"LocalExample123!"}'
```

Copy `access_token` from the login response:

```sh
export ACCESS_TOKEN='<access_token from login>'
curl -sS http://localhost:20255/api/v1/posts \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Registration grants `ROLE_USER`. A fresh database has no seeded admin account or academic taxonomy. Provision the first administrator through controlled database setup; existing admins can then manage roles and taxonomy through the API.

## API

Most `/api/v1` routes require `Authorization: Bearer <access_token>`. Authentication routes and profile-picture/cover-picture reads are public; moderation, taxonomy mutations, and `/api/v1/admin/**` require `ROLE_ADMIN`. JSON uses `snake_case`.

| Area | Routes |
| --- | --- |
| Authentication | `/api/v1/auth/register`, `/login`, `/refresh`, `/logout` under the auth prefix |
| Password recovery | `/api/v1/auth/password-recovery`, `/api/v1/auth/password-reset` — anonymous, enabled after provider configuration |
| Profiles | `/api/v1/user/me`, `/api/v1/user/search`, `/api/v1/user/{userId}/profile-picture`, `/api/v1/user/{userId}/cover-picture` |
| Profile statistics | `/api/v1/user/{userId}/stats`, `/api/v1/subject/{subjectId}/stats` — aggregate DTOs, authenticated profile access |
| Posts | `/api/v1/posts`, `/mine`, `/subject/{subjectId}`, `/department/{departmentId}`, `/user/{userId}` under the posts prefix |
| Post links | Optional ordered `links` on post create/update; version-owned responses, HTTP(S)-only, no server-side fetching. [Contract and V8 rollout](docs/phase-7-operations.md) |
| Admin dashboard | `GET /api/v1/admin/dashboard`: current aggregates and bounded UTC creation/approval activity. [Definitions and V9 rollout](docs/phase-8-operations.md) |
| Audit events | `GET /api/v1/admin/audit-events`: admin-only filtered paging/export, transactional mutation records. [Action matrix, V10, retention and grants](docs/phase-9-operations.md) |
| Homepage modules | `/api/v1/admin/announcements`, `/api/v1/announcements/active`, `/api/v1/posts/trending`; set `ANNOUNCEMENTS_ENABLED` / `TRENDING_ENABLED` independently. [Contracts and V11 rollout](docs/phase-10-operations.md) |
| Search | `/api/v1/posts/search` — combined filters and stable paged results; legacy lists remain arrays |
| Moderation and history | `/api/v1/posts/pending`, `/api/v1/posts/{postId}/approve`, `/api/v1/posts/history/{postId}` |
| Attachments | `/api/v1/files/upload`, `/api/v1/files/{id}`, `/api/v1/files/download/{id}` |
| Votes and vaults | `/api/v1/votes/**`, `/api/v1/vaults/**` |
| Administration and taxonomy | `/api/v1/admin/**`, `/api/v1/schools/**`, `/api/v1/departments/**`, `/api/v1/subject/**` |

> [!NOTE]
> A post's **`id` identifies one version row**; **`post_id` identifies the logical post across versions**. Read/edit/delete, moderation, attachment uploads, votes, and vault operations use the version row ID. History uses the logical `post_id`. Deleting any version hides every version and returns `204 No Content`; rows and files remain retained.

Use [openapi.yaml](openapi.yaml) and the running Swagger UI for request/response contracts. [API_DOCUMENTATION.md](API_DOCUMENTATION.md) provides additional examples; some legacy examples predate the current contract. Post lists currently return unpaged arrays. Posts start in `PENDING_APPROVAL`, titles are limited to 30 characters, and post attachments are limited to three JPEG/PNG/PDF files per version. Default multipart limits are 10 MB per file **and per request**.

Owners/admins can read active private versions; other authenticated users see approved versions only, including through history, files, votes, and vaults. Missing/deleted/inaccessible posts return 404. Uploads enforce extension/MIME/content agreement, 10 MiB byte limits and 25 million decoded pixels; avatar uploads also support WebP. See [Phase 2 operations](docs/phase-2-operations.md) for the visibility matrix, multipart overhead, storage lifecycle and production configuration.

Search supports `q`, `subject_id`, `department_id`, `author_id`, local `created_at` date bounds `from`/`to`, and sorts `newest`, `oldest`, `relevance`, `vote_score`. Pages start at 0, default size is 20, maximum 100. Default results are approved/latest/non-deleted for all roles; explicit moderation-status filtering is admin-only. See [Phase 3 operations](docs/phase-3-operations.md) for tokenization, date semantics, V4 rollout and observed query plans.

Password recovery defaults disabled (new routes return 503) until reset URL, sender, separate rate-key secret and SMTP are configured. Requests use a generic 202; email links expire after 15 minutes by default. Completion accepts `token` and `new_password`, revokes every refresh session and requires fresh login; existing access JWTs keep normal remaining lifetime. See [Phase 4 operations](docs/phase-4-operations.md) for configuration, proxy/rate policy and delivery/retention behavior.

User/subject responses append optional UTC `created_at`/`updated_at` instants. Preexisting creation times use a migration-time approximation recorded internally. Profile statistics count approved/latest/non-deleted logical posts and eligible version-row votes; subject approval time follows existing stored Post.updated_at semantics. See [Phase 5 operations](docs/phase-5-operations.md) for exact definitions, V6 backfill and SQL-maintenance rules.

## Development

```sh
./mvnw verify                                      # Tests and executable JAR
java -jar target/arca-0.0.1-SNAPSHOT.jar             # Run from the repository root
```

Tests use disposable **MySQL 8.4 Testcontainers**, including migration, security, deletion, rollback, and concurrency coverage. Docker must be running; a manually started development database is unnecessary for tests.

To run the same OpenAPI lint as CI, with Node.js 22 available:

```sh
npx --yes @redocly/cli@2.78.0 lint --extends=minimal --format=summary openapi.yaml
```

### Project layout

```text
src/main/java/com/popman/arca/
  controller/v1/        REST endpoints
  dto/v1/               API request and response models
  service/              Business logic and authentication
  repository/           JPA queries and locking
  entity/               Persistence models
  config/ and filter/   Security, CORS, JWT, and deletion observability
src/main/java/db/migration/       Java migrations
src/main/resources/db/migration/  SQL migrations
src/test/                        Unit and MySQL integration tests
docs/                            Roadmap and operational runbooks
uploads/                         Local attachments and profile pictures
```

The request flow is **controller → service → repository → MySQL**. Flyway owns schema evolution; SQL and Java migrations must both ship with releases. The Maven Flyway plugin uses `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD`, not the application's `.env`; compile Java migrations before invoking it. See the [runbook](docs/phase-1-operations.md#explicit-baseline-and-rollout) for commands.

## Deployment

### Container image

With MySQL started using the `-p arca` command above, stop any local API process using port 20255, then run:

```sh
docker build -t arca-backend .
docker run --rm --name arca-api --network arca_default \
  --env-file .env \
  -e DB_URL=jdbc:mariadb://mysql:3306/arca -e PORT=8080 \
  -p 20255:8080 \
  --mount "type=bind,source=$PWD/uploads,target=/app/uploads" \
  arca-backend
```

The multi-stage Dockerfile builds with Maven/Java 21 and runs on a Java 21 JRE. The container uses the Compose service name `mysql`, rather than the host's `localhost`. Keep uploaded files on persistent storage; `file.upload-dir` defaults to `uploads`. Configure frontend origins with `app.cors.allowed-origins`; `http://localhost:5173` is allowed by default.

### CI and server deployment

[Backend tests](.github/workflows/test.yml) runs verification and OpenAPI lint on pushes and pull requests. [DCISM deployment](.github/workflows/deploy-backend.yml) runs on pushes to `master` or manual dispatch, verifies the build, uploads the JAR over SSH, and checks `/v3/api-docs` after restart. It requires the `DCISM_SSH_KEY`, `DCISM_KNOWN_HOSTS`, `DCISM_HOST`, `DCISM_PORT`, `DCISM_USER`, and `DCISM_DEPLOY_PATH` repository secrets, plus the server's runtime `.env` and Java 21 installation.

Deletion requests expose a safe `X-Request-ID`, content-free outcome logs, and the `arca.post.deletion` timer through admin-only Actuator metrics. Metrics are process-local by default. Database baseline, retention, deployment, and forward-fix procedures live in the [operations runbook](docs/phase-1-operations.md).

## Roadmap

[The backend roadmap](docs/arca-backend-roadmap.md) defines additive work on the existing `/api/v1` architecture. Phases 1–5 deliver soft deletion/migrations, shared authorization/media hardening, post search/pagination, password recovery, and profile timestamps/statistics. Next phases cover profile covers, structured links, admin aggregates, and audit events. Announcements and trending are optional; server-side draft persistence is outside the current backend scope.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Database connection fails | Run `docker compose -p arca ps` and `docker compose -p arca logs mysql`; verify port 3306, credentials, and the `jdbc:mariadb://` URL. Containerized API connections use `mysql:3306`. |
| JWT signing fails | Replace `SECRET_KEY` with valid Base64 encoding at least 32 random bytes; token lifetimes must be integer millisecond values. |
| Flyway refuses startup | Check schema history and follow the migration runbook. Do not enable automatic baselining or switch Hibernate to `update` to bypass the failure. |
| API returns 401 or 403 | Send an unexpired access token and check the account's roles. Public registration does not grant admin access. |
| Integration tests cannot start | Ensure Docker is available to the current user; tests create their own MySQL containers. |
| Browser requests fail CORS | Match the frontend's exact origin against `app.cors.allowed-origins`. |
