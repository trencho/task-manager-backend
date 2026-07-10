# Task Manager — Spring Boot API

A RESTful API for managing personal tasks. Users register, sign in, and get JWT-authenticated
CRUD over their own tasks. Tasks are scoped to the authenticated user: you only ever see your own.

Pairs with [task-manager-frontend](https://github.com/trencho/task-manager-frontend), a Vue 3 SPA.

## Stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 |
| Security | Spring Security + JWT (`io.jsonwebtoken`, HS256) |
| Storage | MongoDB (Spring Data) |
| Build | Maven (wrapper committed) |
| Docs | springdoc-openapi |
| Tests | JUnit 5, Mockito, Testcontainers |
| Coverage | JaCoCo |

## Requirements

- **JDK 21.** Not 23, 24, or 25. Since JDK 23, `javac` no longer runs annotation processors found
  on the classpath, so Lombok and MapStruct silently generate nothing and the build fails with
  around thirty `cannot find symbol` errors that look like broken source. If you see those, check
  `java -version` before you check the code.
- Docker, for `docker compose` and for the Testcontainers-backed integration tests.
- MongoDB, if you run outside Docker.

## Configuration

**No credentials live in this repository, and the application will not start without them.**
Every value below is read from the environment with no fallback, so a missing variable fails
fast rather than silently booting on a default.

```text
Caused by: PlaceholderResolutionException:
  Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

Copy [`.env.example`](.env.example) to `.env.local` and fill it in. `.env.local` is git-ignored.

| Variable | Required | Notes |
|---|---|---|
| `JWT_SECRET` | yes | Signs and verifies every token. HS256 needs ≥ 256 bits, so a shorter value throws `WeakKeyException` at startup. Generate with `openssl rand -hex 32`. |
| `MONGODB_URI` | yes | e.g. `mongodb://user:pass@mongo:27017/task-manager?authMechanism=SCRAM-SHA-256` |
| `MONGO_ROOT_USERNAME` / `MONGO_ROOT_PASSWORD` | compose only | Mongo root account |
| `MONGO_APP_USERNAME` / `MONGO_APP_PASSWORD` | compose only | Application account created by `docker/mongo/init/init-mongo.js`. Must match `MONGODB_URI`. |
| `JWT_ACCESS_TOKEN_EXPIRATION` | no | ms, default `3600000` (1 hour) |
| `JWT_REFRESH_TOKEN_EXPIRATION` | no | ms, default `86400000` (24 hours) |
| `SERVER_PORT` | no | default `80` |
| `MANAGEMENT_PORT` | no | default `9090` |
| `LOG_LEVEL` | no | default `INFO`. Do not ship `DEBUG`: it writes tokens and credentials to the log. |

## Running

### With Docker Compose

```bash
cp .env.example .env.local     # then fill in every blank
docker compose --env-file .env.local up --build
```

Brings up the API and a MongoDB instance. Compose refuses to start if any required variable is
unset, rather than substituting an empty string.

### Locally

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
export MONGODB_URI="mongodb://localhost:27017/task-manager"
./mvnw spring-boot:run
```

## Testing

```bash
./mvnw clean verify
```

49 tests. The integration tests start a real MongoDB through Testcontainers, so **a Docker daemon
must be running** — without one they fail rather than skip. Tests supply their own throwaway
configuration from `src/test/resources/application.yml` and need no environment variables.

JaCoCo writes a coverage report to `target/site/jacoco/index.html`. Current coverage is 83% of
instructions, 73% of branches.

CI runs `clean verify` on every push and pull request. See [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## API

All task endpoints require `Authorization: Bearer <accessToken>`.

### Authentication — `/api/auth`

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/api/auth/signup` | `{username, email, password}` | `200` text, or `400` if the username is taken |
| `POST` | `/api/auth/login` | `{username, password}` | `{accessToken, refreshToken}`, or `401` |
| `POST` | `/api/auth/refresh-token` | `{refreshToken}` | `{accessToken, refreshToken}` — **the refresh token is rotated**; or `401` |
| `POST` | `/api/auth/logout` | `{refreshToken}` | `204`; revokes that refresh token |
| `POST` | `/api/auth/logout-all` | — | `204`; revokes **every** refresh token for the caller. **Requires authentication.** |

An expired refresh token is rejected and deleted; sign in again to obtain a new one.

**Refresh tokens rotate.** Redeeming one invalidates it and returns a replacement, so a captured
token is good for at most one use. Clients must store both values from the response.

`logout` needs no access token — possession of the refresh token is the authority to revoke it,
and a client whose access token has already expired must still be able to sign out. It is
idempotent: revoking an unknown token returns `204`, because a `404` would let a caller probe
which refresh tokens exist. The outstanding **access** token stays valid until it expires; that
is inherent to stateless JWT, which is why it is short-lived (1 hour by default).

### Tasks — `/api/tasks`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/tasks` | **Paginated.** Accepts `?page=0&size=10&sort=dueDate,asc`. Returns a Spring `Page`. |
| `POST` | `/api/tasks` | `201 Created` with a `Location` header. `status` is optional and defaults to `PENDING`. |
| `GET` | `/api/tasks/{taskId}` | A single task |
| `PUT` | `/api/tasks/{taskId}` | Updates the task. Omitting `status` leaves the current status unchanged. |
| `DELETE` | `/api/tasks/{taskId}` | `204 No Content` |

### Task shape

| Field | Type | Constraints |
|---|---|---|
| `title` | string | required, 3–50 chars |
| `description` | string | ≤ 200 chars |
| `dueDate` | `LocalDate` | `YYYY-MM-DD` |
| `status` | enum | `PENDING`, `IN_PROGRESS`, `COMPLETED` |

### Docs and health

Verified against a running instance:

| Endpoint | Port | Auth |
|---|---|---|
| `/swagger-ui/index.html` (`/swagger-ui.html` redirects here) | application | **none — public** |
| `/v3/api-docs` | application | **none — public** |
| `/actuator/health`, `/actuator/info`, `/actuator/metrics` | management (`9090`) | `401` without credentials |

`application.yml` also lists `httptrace`, `openapi`, `prometheus` and `swagger-ui` under
`management.endpoints.web.exposure.include`, but only three actuator endpoints are actually
exposed. `prometheus` needs `micrometer-registry-prometheus`, which is not a dependency;
`httptrace` needs an `HttpExchangeRepository` bean; and `openapi`/`swagger-ui` are springdoc
paths, not actuator endpoints. The extra names are inert.

## Layout

```text
src/main/java/com/project/taskmanager/
├── controller/   AuthController, TaskController
├── service/      interfaces + impl/
├── repository/   Spring Data MongoDB
├── entity/       Task, User, RefreshToken
├── dto/          request/response records, bean-validated
├── mapper/       MapStruct entity <-> DTO
├── security/     JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetails
├── config/       SecurityConfig
├── exception/    ControllerExceptionHandler
└── enums/        TaskStatus
```

Lombok and MapStruct are annotation processors. After changing either, rebuild clean — stale
generated sources produce confusing compile errors.

## Roadmap

Candidate features, derived from this README and the gaps between it and the code:

1. **Filter and search tasks** — `GET /api/tasks?status=&q=&dueBefore=`. The endpoint is already
   paginated, so this is a query-parameter and repository change rather than a redesign.
2. **Priority levels** — a `Priority` enum alongside `TaskStatus`, sortable.
3. ~~**Let `POST /api/tasks` accept a status.**~~ Done — `status` is optional on create and
   defaults to `PENDING`, and an update that omits it no longer nulls the field.
4. **Revoke every session for a user.** `deleteByUsername` exists on the service with no endpoint.
   Rotation and single-session logout are done.
5. **Rate-limit the auth endpoints.** `/login` and `/signup` are unauthenticated and unthrottled.
6. **Gate the OpenAPI docs.** `/v3/api-docs` and `/swagger-ui/index.html` are publicly readable.
   Set `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` outside dev,
   or put them behind the same authentication as everything else.
7. **Return a DTO from `GET /api/tasks`.** It serialises the `Task` entity directly, so each item
   carries the owner's `username` — redundant, and it leaks the field into the response body.

## Security notes

- The JWT signing secret was previously committed to this public repository. It has been removed
  from the code, but it remains present in the git history and must be treated as compromised.
  **Rotate it.** Every token minted under the old secret is forgeable.
- `POST /api/auth/signup` hashes passwords with BCrypt. Never insert users directly into Mongo.
