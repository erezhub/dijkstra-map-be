# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Two Spring Boot services sharing a `common` module:

- **map-service** (port 8080) — stores a weighted graph of named nodes in MongoDB and exposes Dijkstra's shortest path algorithm over HTTP. Requires Bearer token authentication on all endpoints.
- **user-service** (port 8081) — user management with role-based access (ADMIN → MANAGER → REGULAR) and opaque-token authentication.

## Build & Run

```bash
# Build all modules
mvn clean install

# Run map-service
mvn spring-boot:run -pl map-service

# Run user-service
mvn spring-boot:run -pl user-service

# Run all tests
mvn test

# Run tests for one module
mvn test -pl map-service
mvn test -pl user-service

# Run a single test class
mvn test -pl map-service -Dtest=ClassName

# Run a single test method
mvn test -pl user-service -Dtest=ClassName#methodName
```

MongoDB must be running. Both services connect to MongoDB; map-service uses two connections (map DB + users DB for token validation). See `application.properties` in each service for property keys.

## Architecture

Multi-module Maven project (Java 21, Spring Boot 4.0.5). Modules: `common`, `map-service`, `user-service`.

### common

Shared infrastructure used by both services.

| Package | Purpose |
|---|---|
| `exception` | `ServiceException` (base `RuntimeException`); `GlobalExceptionHandler` (`@RestControllerAdvice`, handles `ServiceException` + `MethodArgumentNotValidException` → 409) |
| `dto/response` | `ErrorResponse` — `{ "message": "..." }` |
| `config` | `SecurityConfig` — conditional `SecurityFilterChain` beans + `PasswordEncoder` + CORS |
| `security` | `JwtFilter` — `@Component("jwtFilter")`, validates tokens and loads user via `tokenValidationMongoTemplate` |

**SecurityConfig conditional logic**: `authenticatedFilterChain` is `@ConditionalOnBean(name = "jwtFilter")` — active in both services because both scan `com.eRez.common` and pick up the common `JwtFilter`. map-service gets authenticated behaviour; `openFilterChain` (`@ConditionalOnMissingBean`) is the fallback if the bean is absent.

**JwtFilter** (common): uses `@Qualifier("tokenValidationMongoTemplate") MongoTemplate` to query the `tokens` and `users` collections in `dijkstra-users`. Validates `valid == true` and `expiresAt.after(now)`, then loads the user document to build a `UserDetails` principal (email or username as identifier, `ROLE_X` authority). Each service must expose a `"tokenValidationMongoTemplate"` bean pointing to `dijkstra-users`.

### map-service

| Package | Purpose |
|---|---|
| `controller` | `MapController` — single REST controller |
| `services` | `NodeService` — CRUD + bidirectional connection logic; `PathService` — Dijkstra algorithm |
| `data` | `CacheData` — thread-safe in-memory cache, owns its own lifecycle |
| `database/document` | `NodeDocument` — MongoDB document (`nodes` collection) |
| `database/repository` | `NodeRepository` — `MongoRepository`; includes `findByName` |
| `dto` | `MapNode` — in-memory algorithm node; `Position` — `{ x, y }` coordinate pair |
| `dto/request` | `CreateMapRequest`, `NodeRequest`, `UpdateNodeRequest` |
| `dto/response` | `MapResponse`, `NodeResponse`, `PathResponse`, `PathSegment` |
| `exception` | `MapException extends ServiceException` |
| `config` | `MongoConfig` (primary, `dijkstra-map`); `UsersMongoConfig` (secondary, `dijkstra-users`) |

**Key decisions:**
- `NodeDocument` stores connections as `Map<String, Integer>` (MongoDB ID → weight). Names are never stored as connection keys.
- All connections are bidirectional — every write mirrors changes to neighbour documents.
- `CacheData` owns its own lifecycle (`@PostConstruct`). The `nodes` field is a `volatile` unmodifiable snapshot; reads need no locking. `NodeService` calls `cacheData.refresh()` after every write. Read endpoints never hit MongoDB.
- `MapService.java` is an empty deprecated stub (cannot be deleted); ignore it.
- `UsersMongoConfig` exposes `"usersMongoClient"` and `"tokenValidationMongoTemplate"` beans (connecting to `dijkstra-users`) so the common `JwtFilter` can validate tokens.
- Property keys: `mongodb.map.uri`, `mongodb.map.database` (primary); `mongodb.users.uri` (secondary).

### user-service

| Package | Purpose |
|---|---|
| `controller` | `AuthController` (`/auth`), `UserController` (`/users`) |
| `services` | `AuthService` — login/logout; `UserService` — CRUD with role enforcement |
| `database/document` | `UserDocument` (`users` collection), `TokenDocument` (`tokens` collection) |
| `database/repository` | `UserRepository`, `TokenRepository` |
| `dto` | `UserRole` enum (`ADMIN`, `MANAGER`, `REGULAR`) |
| `dto/request` | `LoginRequest`, `CreateUserRequest`, `UpdateUserRequest` |
| `dto/response` | `UserResponse`, `TokenResponse` |
| `exception` | `UserException extends ServiceException` |
| `config` | `MongoConfig` — also exposes `"tokenValidationMongoTemplate"` aliasing the primary template |
| `init` | `DefaultAdminInitializer` — creates the admin user on startup if none exists |

**Key decisions:**
- **Tokens are opaque UUIDs** stored in `tokens` collection with a `valid` boolean and a TTL index (`@Indexed(expireAfterSeconds = 0)` on `expiresAt`). Validation = DB lookup only. `JwtFilter` (in common) checks both `valid == true` and `expiresAt.after(now)` — MongoDB's TTL cleanup runs every ~60 s so the document may still exist after expiration.
- **`JwtFilter`** (common) sets a Spring Security `UserDetails` object as the principal so `@AuthenticationPrincipal UserDetails` resolves correctly in controllers.
- **Login identifier**: MANAGER/REGULAR log in with email; ADMIN logs in with username `"admin"` (email is `null`). `AuthService.login()` tries `findByEmail` first, then falls back to `findByUsername`.
- **Role hierarchy**: ADMIN manages MANAGERs, MANAGER manages REGULARs, REGULAR can only access `/users/me`. `assertCanManage` also allows self-access at any role.
- **Password rule**: password can only be changed when updating oneself; any other update request with a non-null `password` throws `UserException`.
- **Sparse unique index** on `email` allows multiple `null` values (only admin has `null`).
- Property keys: `mongodb.uri`, `mongodb.database`.

### Data models

```
NodeDocument  { id: String (UUID), name: String, position: Position, connections: Map<String, Integer> }
Position      { x: double, y: double }

UserDocument  { id: String (UUID), username: String, email: String (sparse unique), password: String (BCrypt), role: UserRole }
TokenDocument { id: String, token: String (unique UUID), userId: String, valid: boolean, expiresAt: Date (TTL) }
```

## REST API

Errors always return `409 Conflict` with `{ "message": "..." }`.

### map-service (`/map`)

All endpoints require `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/map` | Return all nodes with named connections and position |
| `POST` | `/map` | Replace entire map (validates bidirectional connections + weights) |
| `POST` | `/map/node` | Add a single node; wires reverse connections on neighbours |
| `PUT` | `/map/node/{name}` | Update a node's connections and/or position |
| `DELETE` | `/map/node/{name}` | Delete a node; removes it from all neighbours |
| `GET` | `/map/path?from=X&to=Y` | Run Dijkstra; returns total distance and ordered path segments |

### user-service

All `/users` endpoints require `Authorization: Bearer <token>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/login` | none | Returns `{ "token": "..." }` |
| `POST` | `/auth/logout` | Bearer | Invalidates token (`valid = false`) |
| `GET` | `/users` | Bearer | ADMIN → list MANAGERs; MANAGER → list REGULARs |
| `POST` | `/users` | Bearer | ADMIN → create MANAGER; MANAGER → create REGULAR |
| `GET` | `/users/{id}` | Bearer | ADMIN or MANAGER (own level down) |
| `PUT` | `/users/{id}` | Bearer | ADMIN or MANAGER; password change only allowed on self |
| `DELETE` | `/users/{id}` | Bearer | ADMIN or MANAGER |
| `GET` | `/users/me` | Bearer | Any role — returns caller's own data |
| `PUT` | `/users/me` | Bearer | Any role — updates caller's own data |

## Tests

`@WebMvcTest` was removed in Spring Boot 4.x — all controller tests use `MockMvcBuilders.standaloneSetup()`.

### common (`common/src/test/`)

| Class | What it tests |
|---|---|
| `JwtFilterTest` | Valid token sets UserDetails authentication, expired → 401, invalidated → 401, unknown → 401, user not found → 401, missing header passes through — mocks `MongoTemplate` |

### map-service (`map-service/src/test/`)

| Class | What it tests |
|---|---|
| `NodeServiceTest` | All CRUD paths, validation, bidirectional wiring, position propagation — mocks `NodeRepository` + `CacheData` |
| `PathServiceTest` | Dijkstra correctness, node-not-found and no-path errors — mocks `CacheData` |
| `MapControllerTest` | HTTP status codes, JSON shape, `@Valid` rejections, `MapException` → 409 |
| `CacheDataTest` | `refresh()` replaces (not appends), list is unmodifiable, concurrent stress test |

### user-service (`user-service/src/test/`)

| Class | What it tests |
|---|---|
| `AuthServiceTest` | Login via email/username fallback, wrong credentials, logout valid/not-found token, expiresAt set correctly — mocks repos + `PasswordEncoder`; injects `expirationMs` via `ReflectionTestUtils` |
| `UserServiceTest` | Role-based getUsers/createUser/getUserById/updateUser/deleteUser, password-change rules, getSelf/updateSelf — mocks `UserRepository` + `PasswordEncoder` |
| `AuthControllerTest` | `POST /auth/login` (200, @Valid rejections, wrong credentials → 409), `POST /auth/logout` (204) |
| `UserControllerTest` | All `/users` endpoints — sets `SecurityContextHolder` + registers `AuthenticationPrincipalArgumentResolver` so `@AuthenticationPrincipal` resolves in `standaloneSetup` |

## Dependencies

| Dependency | Where | Role |
|---|---|---|
| `spring-boot-starter-web` | root | REST layer |
| `spring-boot-starter-data-mongodb` | root | MongoDB driver + repositories |
| `spring-boot-starter-validation` | root | Bean Validation on request DTOs |
| `lombok` | root | `@Getter`/`@Setter`/`@Slf4j`/`@RequiredArgsConstructor`/`@AllArgsConstructor` |
| `spring-boot-starter-test` | root (test) | JUnit 5, Mockito, MockMvc |
| `spring-boot-starter-security` | common | Spring Security filter chain |
