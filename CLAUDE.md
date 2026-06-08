# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot REST API that stores a weighted graph of named nodes in MongoDB and exposes Dijkstra's shortest path algorithm over HTTP. Nodes and their connections are managed via CRUD endpoints; path queries run against an in-memory cache populated from MongoDB on startup.

## Build & Run

```bash
# Build
mvn clean install

# Run the application (default port 8080)
mvn spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName
```

MongoDB must be running. Connection is configured in `map-service/src/main/resources/application.properties` via `mongodb.uri`. Credentials are picked up by `MongoConfig` using `@Value("${mongodb.uri}")`.

## Architecture

Multi-module Maven project (Java 21, Spring Boot 4.0.5). Modules: `map-service`, `user-service`.

### Package structure (map-service)

| Package | Purpose |
|---|---|
| `controller` | `MapController` — single REST controller, routes to the two services |
| `services` | `NodeService` — CRUD + bidirectional connection logic; `PathService` — Dijkstra algorithm |
| `data` | `CacheData` — thread-safe in-memory cache, owns its own lifecycle |
| `database/document` | `NodeDocument` — MongoDB document (`nodes` collection) |
| `database/repository` | `NodeRepository` — `MongoRepository`; includes `findByName` |
| `dto` | `MapNode` — in-memory algorithm node; `Position` — `{ x, y }` coordinate pair |
| `dto/request` | `CreateMapRequest`, `NodeRequest`, `UpdateNodeRequest` |
| `dto/response` | `MapResponse`, `NodeResponse`, `PathResponse`, `PathSegment`, `ErrorResponse` |
| `exception` | `MapException` (custom), `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| `config` | `MongoConfig` — explicit `MongoClient` bean (bypasses Spring Boot auto-config) |

### Key design decisions

- **`NodeDocument`** stores connections as `Map<String, Integer>` (MongoDB ID → weight). Node names are never stored as connection keys in the DB — only IDs.
- **All connections are bidirectional**: every write operation (create map, add, update, delete) mirrors changes to neighbour documents.
- **`CacheData`** owns the cache lifecycle: it injects `NodeRepository`, initialises via its own `@PostConstruct`, and exposes `refresh()`. The `nodes` field is `volatile List` storing an unmodifiable snapshot, so reads need no locking. `NodeService` calls `cacheData.refresh()` after every write. Read endpoints (`getMap`, `getPath`) never hit MongoDB.
- **`PathService`** only reads from `CacheData`. It never writes to the DB or the cache.
- **`MapService.java`** exists as an empty deprecated stub (cannot be deleted); ignore it.

### Data model

```
NodeDocument { id: String (UUID), name: String, position: Position, connections: Map<String, Integer> }
Position     { x: double, y: double }
```

Connection keys in the DB are UUIDs. All API inputs and outputs use node **names**. `position` is optional — `null` means no position is set.

## REST API

All endpoints are under `/map`. Errors return `409 Conflict` with `{ "message": "..." }`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/map` | Return all nodes with named connections and position |
| `POST` | `/map` | Replace entire map (validates bidirectional connections + weights) |
| `POST` | `/map/node` | Add a single node; wires reverse connections on neighbours |
| `PUT` | `/map/node/{name}` | Update a node's connections and/or position (adds/updates/removes bidirectionally) |
| `DELETE` | `/map/node/{name}` | Delete a node; removes it from all neighbours |
| `GET` | `/map/path?from=X&to=Y` | Run Dijkstra; returns total distance and ordered path segments |

### Example POST /map body

```json
{
  "nodes": [
    { "name": "Amsterdam", "position": { "x": 1.0, "y": 2.0 }, "connections": { "Berlin": 7, "Paris": 3 } },
    { "name": "Berlin",    "position": { "x": 3.0, "y": 4.0 }, "connections": { "Amsterdam": 7, "Prague": 5 } },
    { "name": "Paris",     "connections": { "Amsterdam": 3 } },
    { "name": "Prague",    "connections": { "Berlin": 5 } }
  ]
}
```

### Example GET /map/path response

```json
{
  "distance": 12,
  "path": [
    { "from": "Amsterdam", "to": "Berlin",  "distance": 7 },
    { "from": "Berlin",    "to": "Prague",  "distance": 5 }
  ]
}
```

## Tests

Four test classes in `map-service/src/test/`:

| Class | What it tests |
|---|---|
| `NodeServiceTest` | All CRUD paths, validation, bidirectional connection wiring, position propagation — mocks `NodeRepository` + `CacheData` |
| `PathServiceTest` | Dijkstra correctness (direct, multi-hop, same node), node-not-found and no-path error cases — mocks `CacheData` |
| `MapControllerTest` | HTTP status codes, JSON shape, `@Valid` rejections, `MapException` → 409 — uses `MockMvcBuilders.standaloneSetup()` (note: `@WebMvcTest` was removed in Spring Boot 4.x) |
| `CacheDataTest` | `refresh()` replaces (not appends), returned list is unmodifiable, concurrent read/write stress test |

## Dependencies

| Dependency | Role |
|---|---|
| `spring-boot-starter-web` | REST layer |
| `spring-boot-starter-data-mongodb` | MongoDB driver + repositories |
| `spring-boot-starter-validation` | Bean Validation (`@NotBlank`, `@NotNull` on request DTOs) |
| `lombok` | `@Getter`/`@Setter`/`@Slf4j`/`@RequiredArgsConstructor` throughout |
| `spring-boot-starter-test` | JUnit 5, Mockito, MockMvc (test scope) |