# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Three Spring Boot services sharing a `common` module:

- **map-service** (port 8080) — stores a weighted graph of named nodes in MongoDB and exposes Dijkstra's shortest path algorithm over HTTP. Requires Bearer token authentication on all endpoints.
- **user-service** (port 8081) — user management with role-based access (ADMIN → MANAGER → REGULAR) and opaque-token authentication. Publishes a `user.created` RabbitMQ event after every user creation.
- **notification-service** (no HTTP port) — RabbitMQ consumer; listens for `user.created` events and sends a welcome email via SMTP. Uses MailHog in dev.

## Build & Run

```bash
# Build all modules
mvn clean install

# Run map-service
mvn spring-boot:run -pl map-service

# Run user-service
mvn spring-boot:run -pl user-service

# Run notification-service
mvn spring-boot:run -pl notification-service

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

MongoDB must be running locally. map-service uses two connections (map DB + users DB for token validation). notification-service requires RabbitMQ and an SMTP server (MailHog on `localhost:1025` by default). See `application.properties` in each service for property keys.

## Docker

```bash
# Start everything
docker compose up --build

# Stop
docker compose down

# Build images individually
docker build -f DockerfileMapService -t map-service:latest .
docker build -f DockerfileUserService -t user-service:latest .
docker build -f DockerfileNotificationService -t notification-service:latest .
```

| Service | Host port | Notes |
|---|---|---|
| `mongodb` | 27017 | Data persisted in `mongo-data` Docker volume |
| `rabbitmq` | 5672 / 15672 | AMQP broker; management UI at `http://localhost:15672` (admin/password) |
| `mailhog` | 1025 / 8025 | Dev SMTP catch-all; web UI at `http://localhost:8025` |
| `map-service` | 8080 | Waits for MongoDB + RabbitMQ health checks |
| `user-service` | 8081 | Waits for MongoDB + RabbitMQ health checks |
| `notification-service` | — | No HTTP; waits for RabbitMQ + MailHog |
| `mongo-express` | 8082 | Web UI for browsing MongoDB — `http://localhost:8082` |

**Dockerfiles**: multi-stage builds — Maven build stage (`maven:3.9-eclipse-temurin-21`) then slim runtime (`eclipse-temurin:21-jre-jammy`). Each Dockerfile copies only the pom.xml of the other services (not their `src`) so Maven can resolve the parent module graph without compiling unused code. notification-service does not depend on `common` so it only copies `common/pom.xml`.

**`spring-boot-maven-plugin`** must be declared in each service's `pom.xml` for `mvn package` to produce an executable fat JAR. Without it, the plugin only exists in `pluginManagement` (from `spring-boot-starter-parent`) and `mvn package` produces a plain JAR with no `Main-Class`.

## Logging

Each service has a `logback-spring.xml` in `src/main/resources`. Logs are written to both console and a rolling file:

| Service | Active log | Archive pattern |
|---|---|---|
| map-service | `log/dijkstra-map.log` | `log/yyyy-MM/dijkstra-map.<i>.log.gz` |
| user-service | `log/dijkstra-user.log` | `log/yyyy-MM/dijkstra-user.<i>.log.gz` |
| notification-service | `log/dijkstra-notification.log` | `log/yyyy-MM/dijkstra-notification.<i>.log.gz` |

Files roll when they reach 5 MB; archives are grouped into `log/YYYY-MM/` subfolders with an incrementing index suffix. The `log/` directory is in `.gitignore`.

## Architecture

Multi-module Maven project (Java 21, Spring Boot 4.0.5). Modules: `common`, `map-service`, `user-service`, `notification-service`.

### common

Shared infrastructure used by both services.

| Package | Purpose |
|---|---|
| `exception` | `ServiceException` (base `RuntimeException`); `GlobalExceptionHandler` (`@RestControllerAdvice`, handles `ServiceException` + `MethodArgumentNotValidException` → 409) |
| `dto/response` | `ErrorResponse` — `{ "message": "..." }` |
| `config` | `SecurityConfig` — conditional `SecurityFilterChain` beans + `PasswordEncoder` + CORS; `MongoTypeMapperConfig` — `BeanPostProcessor` that removes the `_class` field from all MongoDB documents |
| `security` | `JwtFilter` — `@Component("jwtFilter")`, validates tokens and loads user via `tokenValidationMongoTemplate` |
| `data` | `Auditable` — interface with `onBeforeSave()` for timestamp logic; `AuditingMongoRepository` — custom repository base class |

**SecurityConfig conditional logic**: `authenticatedFilterChain` is `@ConditionalOnBean(name = "jwtFilter")` — active in both services because both scan `com.eRez.common` and pick up the common `JwtFilter`. map-service gets authenticated behaviour; `openFilterChain` (`@ConditionalOnMissingBean`) is the fallback if the bean is absent.

**JwtFilter** (common): uses `@Qualifier("tokenValidationMongoTemplate") MongoTemplate` to query the `tokens` and `users` collections in `dijkstra-users`. Validates `valid == true` and `expiresAt.after(now)`, then loads the user document to build a `UserDetails` principal (email or username as identifier, `ROLE_X` authority). Each service must expose a `"tokenValidationMongoTemplate"` bean pointing to `dijkstra-users`.

**Auditing**: `Auditable` interface declares `onBeforeSave()`. Each document implements its own timestamp logic there. `AuditingMongoRepository` overrides `save()` to invoke this hook — bypassing Spring Data MongoDB 4.2+'s `bulkWrite` path which skips entity callbacks. Each service's `MongoConfig` registers it via `@EnableMongoRepositories(repositoryBaseClass = AuditingMongoRepository.class)`.

### map-service

| Package | Purpose |
|---|---|
| `controller` | `MapController` — node/path endpoints; `RouteController` — saved-route endpoints |
| `services` | `NodeService` — CRUD + bidirectional connection logic; `PathService` — Dijkstra algorithm; `RouteService` — saved route CRUD + stale/recalculation logic |
| `consumer` | `RouteRecalculationConsumer` — `@RabbitListener`; calls `routeService.recalculateAllStale()` on any `map.node.*` event |
| `data` | `CacheData` — thread-safe in-memory cache, owns its own lifecycle |
| `database/document` | `NodeDocument` (`nodes` collection); `RouteDocument` (`routes` collection) |
| `database/repository` | `NodeRepository`; `RouteRepository` — includes `findRoute` (bidirectional), `findByPathContaining`, `findByStaleTrue`, `deleteByNodeAOrNodeB` |
| `dto` | `MapNode` — in-memory algorithm node; `Position` — `{ x, y }` coordinate pair |
| `dto/event` | `NodeChangedEvent` — RabbitMQ event payload `{ type, nodeName }` |
| `dto/request` | `CreateMapRequest`, `NodeRequest`, `UpdateNodeRequest` |
| `dto/response` | `MapResponse`, `NodeResponse`, `PathResponse`, `PathSegment`, `SavedRouteResponse` |
| `exception` | `MapException extends ServiceException` |
| `config` | `MongoConfig` (primary, `dijkstra-map`); `UsersMongoConfig` (secondary, `dijkstra-users`); `MapRabbitConfig` — exchange, queue, binding, `JacksonJsonMessageConverter` |

**Key decisions:**
- `NodeDocument` stores connections as `Map<String, Integer>` (MongoDB ID → weight). Names are never stored as connection keys.
- All connections are bidirectional — every write mirrors changes to neighbour documents.
- `CacheData` owns its own lifecycle (`@PostConstruct`). The `nodes` field is a `volatile` unmodifiable snapshot; reads need no locking. `NodeService` calls `cacheData.refresh()` after every write. Read endpoints never hit MongoDB.
- `MapService.java` is an empty deprecated stub (cannot be deleted); ignore it.
- `UsersMongoConfig` exposes `"usersMongoClient"` and `"tokenValidationMongoTemplate"` beans (connecting to `dijkstra-users`) so the common `JwtFilter` can validate tokens.
- Property keys: `mongodb.map.uri`, `mongodb.map.database` (primary); `mongodb.users.uri` (secondary); `rabbitmq.exchange`, `rabbitmq.queue.route-recalculation`.

**Saved routes:**
- `RouteDocument` stores `nodeA`, `nodeB`, `List<String> path` (ordered node names A→B inclusive), `List<Integer> segmentDistances` (per-hop), `distance`, `stale` boolean.
- Routes are bidirectional — stored once as A→B. `getRoute(B,A)` reverses both `path` and `segmentDistances` lists on the fly.
- `stale=true` means recalculation is pending. Set synchronously in the mutation thread before publishing the RabbitMQ event; async consumer calls `recalculateAllStale()`. If a user requests a stale route before background recalculation completes, it is recalculated on-the-fly.
- Invalidation rules: `addNode`/`updateNode` → mark all routes stale + publish `map.node.added/updated`; `deleteNode` → `deleteByEndpoint(name)` (endpoint routes deleted) + `markStaleByPath(name)` (intermediate routes marked stale) + publish `map.node.deleted`; `createMap` → `deleteAll()` (no event needed).
- `RouteRepository.findRoute` uses `$or` query to match either direction. `findByPathContaining` uses `{ path: "nodeName" }` — MongoDB naturally checks array membership.

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
- **Tokens are opaque UUIDs** stored in `tokens` collection with a `valid` boolean and a TTL index (`@Indexed(expireAfter = "0s")` on `expiresAt`). Validation = DB lookup only. `JwtFilter` (in common) checks both `valid == true` and `expiresAt.after(now)` — MongoDB's TTL cleanup runs every ~60 s so the document may still exist after expiration.
- **`JwtFilter`** (common) sets a Spring Security `UserDetails` object as the principal so `@AuthenticationPrincipal UserDetails` resolves correctly in controllers.
- **Login identifier**: MANAGER/REGULAR log in with email; ADMIN logs in with username `"admin"` (email is `null`). `AuthService.login()` tries `findByEmail` first, then falls back to `findByUsername`.
- **Role hierarchy**: ADMIN manages MANAGERs, MANAGER manages REGULARs, REGULAR can only access `/users/me`. `assertCanManage` also allows self-access at any role.
- **Password rule**: password can only be changed when updating oneself; any other update request with a non-null `password` throws `UserException`.
- **Sparse unique index** on `email` allows multiple `null` values (only admin has `null`).
- **RabbitMQ**: after `userRepository.save()` in `createUser()`, publishes a `UserCreatedEvent` (`{ id, username, email, role }`) to the `dijkstra.events` topic exchange with routing key `user.created`. `RabbitConfig` declares the exchange and registers `JacksonJsonMessageConverter`.
- Property keys: `mongodb.uri`, `mongodb.database`; `rabbitmq.exchange`, `rabbitmq.routing-key.user-created`.

### notification-service

| Package | Purpose |
|---|---|
| `config` | `RabbitConfig` — declares `TopicExchange`, `Queue` (`notification.user.created`), `Binding`; registers `JacksonJsonMessageConverter` and `SimpleRabbitListenerContainerFactory` |
| `consumer` | `UserCreatedConsumer` — `@RabbitListener` on `${rabbitmq.queue.user-created}`; calls `EmailService` |
| `service` | `EmailService` — builds a `MimeMessage` via `MimeMessageHelper`; sets from address + display name; sends via `JavaMailSender` |
| `dto` | `UserCreatedEvent` — `{ id, username, email, role }` plain POJO; matches what user-service publishes |

**Key decisions:**
- No dependency on `common` — avoids pulling in MongoDB, Spring Security, and token validation.
- `spring.main.web-application-type=none` — no embedded Tomcat; AMQP listener threads keep the JVM alive.
- Exchange is declared in both user-service and notification-service (idempotent in RabbitMQ). Queue and binding are declared only in notification-service (consumer owns its queue).
- `JacksonJsonMessageConverter` (Spring AMQP 4.x) replaces the deprecated `Jackson2JsonMessageConverter`.
- From address (`notification.mail.from`) and display name (`notification.mail.from-name`) are separate properties; `EmailService` combines them into `InternetAddress(from, fromName)`.
- MailHog (`localhost:1025`) is the default SMTP target for local and Docker dev. In Docker, `SPRING_MAIL_HOST: mailhog` overrides the host to the container name.
- `.env.example` documents the `MAIL_USERNAME` / `MAIL_PASSWORD` env vars needed for real SMTP; `.env` is gitignored.
- Property keys: `rabbitmq.exchange`, `rabbitmq.queue.user-created`, `rabbitmq.routing-key.user-created`; `notification.mail.from`, `notification.mail.from-name`.

### Data models

```
NodeDocument  { id: String (UUID), name: String, position: Position, connections: Map<String, Integer>,
                createdAt: LocalDateTime (UTC), updatedAt: LocalDateTime (UTC) }
Position      { x: double, y: double }

RouteDocument { id: String, nodeA: String, nodeB: String, path: List<String>, segmentDistances: List<Integer>,
                distance: int, stale: boolean, createdAt: LocalDateTime (UTC), updatedAt: LocalDateTime (UTC) }

UserDocument  { id: String (UUID), username: String, email: String (sparse unique), password: String (BCrypt), role: UserRole,
                createdAt: LocalDateTime (UTC), updatedAt: LocalDateTime (UTC) }
TokenDocument { id: String, token: String (unique UUID), userId: String, valid: boolean, expiresAt: Date (TTL),
                createdAt: LocalDateTime (UTC) }
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
| `POST` | `/map/route?from=X&to=Y` | Save (or overwrite) a route; returns 201 with `SavedRouteResponse` |
| `GET` | `/map/route?from=X&to=Y` | Return saved route (recalculates on-the-fly if stale); 409 if not found |
| `DELETE` | `/map/route?from=X&to=Y` | Delete saved route; 409 if not found |
| `GET` | `/map/routes` | List all saved routes |

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
| `AuditingMongoRepositoryTest` | `save()` calls `onBeforeSave()` on `Auditable` entities; sets `createdAt`+`updatedAt` on new entity; preserves `createdAt` and advances `updatedAt` on existing entity — mocks `MongoEntityInformation` + `MongoOperations` |

### map-service (`map-service/src/test/`)

| Class | What it tests |
|---|---|
| `NodeServiceTest` | All CRUD paths, validation, bidirectional wiring, position propagation; verifies route invalidation calls + RabbitMQ publish on each mutation — mocks `NodeRepository` + `CacheData` + `RouteService` + `RabbitTemplate` |
| `PathServiceTest` | Dijkstra correctness, node-not-found and no-path errors — mocks `CacheData` |
| `MapControllerTest` | HTTP status codes, JSON shape, `@Valid` rejections, `MapException` → 409 |
| `RouteControllerTest` | All `/map/route` and `/map/routes` endpoints — success and 409 cases |
| `RouteServiceTest` | saveRoute, getRoute (forward/reversed/stale/not-found), deleteRoute, getAllRoutes, markAllStale, markStaleByPath, deleteByEndpoint, deleteAll, recalculateAllStale (including delete-on-no-path) |
| `RouteRecalculationConsumerTest` | Any event type (NODE_ADDED/UPDATED/DELETED_INTERMEDIATE) calls `recalculateAllStale()` |
| `CacheDataTest` | `refresh()` replaces (not appends), list is unmodifiable, concurrent stress test |
| `NodeDocumentTest` | `onBeforeSave()` sets `createdAt`+`updatedAt` on first call; preserves `createdAt` and advances `updatedAt` on subsequent calls |

### user-service (`user-service/src/test/`)

| Class | What it tests |
|---|---|
| `AuthServiceTest` | Login via email/username fallback, wrong credentials, logout valid/not-found token, expiresAt set correctly — mocks repos + `PasswordEncoder`; injects `expirationMs` via `ReflectionTestUtils` |
| `UserServiceTest` | Role-based getUsers/createUser/getUserById/updateUser/deleteUser, password-change rules, getSelf/updateSelf — mocks `UserRepository` + `PasswordEncoder` + `RabbitTemplate` |
| `AuthControllerTest` | `POST /auth/login` (200, @Valid rejections, wrong credentials → 409), `POST /auth/logout` (204) |
| `UserControllerTest` | All `/users` endpoints — sets `SecurityContextHolder` + registers `AuthenticationPrincipalArgumentResolver` so `@AuthenticationPrincipal` resolves in `standaloneSetup` |
| `UserDocumentTest` | `onBeforeSave()` sets `createdAt`+`updatedAt` on first call; preserves `createdAt` and advances `updatedAt` on subsequent calls |
| `TokenDocumentTest` | `onBeforeSave()` sets `createdAt` on first call; preserves it on subsequent calls |

## Dependencies

| Dependency | Where | Role |
|---|---|---|
| `spring-boot-starter-web` | root | REST layer |
| `spring-boot-starter-data-mongodb` | root | MongoDB driver + repositories |
| `spring-boot-starter-validation` | root | Bean Validation on request DTOs |
| `lombok` | root | `@Getter`/`@Setter`/`@Slf4j`/`@RequiredArgsConstructor`/`@AllArgsConstructor` |
| `spring-boot-starter-test` | root (test) | JUnit 5, Mockito, MockMvc |
| `spring-boot-starter-security` | common | Spring Security filter chain |
| `spring-boot-starter-amqp` | map-service, user-service, notification-service | RabbitMQ messaging |
| `spring-boot-starter-mail` | notification-service | JavaMailSender / SMTP |
