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

Multi-module Maven project (Java 21, Spring Boot 4.0.5), single module `map-service`.

### Package structure

| Package | Purpose |
|---|---|
| `controller` | `MapController` — single REST controller, routes to the two services |
| `services` | `NodeService` — CRUD + cache management; `PathService` — Dijkstra algorithm |
| `data` | `CacheData` — in-memory `List<NodeDocument>` cache, shared singleton |
| `database/document` | `NodeDocument` — MongoDB document (`nodes` collection) |
| `database/repository` | `NodeRepository` — `MongoRepository`; includes `findByName` |
| `dto/request` | `CreateMapRequest`, `NodeRequest`, `UpdateNodeRequest` |
| `dto/response` | `MapResponse`, `NodeResponse`, `PathResponse`, `PathSegment`, `ErrorResponse` |
| `exception` | `MapException` (custom), `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| `config` | `MongoConfig` — explicit `MongoClient` bean (bypasses Spring Boot auto-config) |

### Key design decisions

- **`NodeDocument`** stores connections as `Map<String, Integer>` (MongoDB ID → weight). Node names are never stored as connection keys in the DB — only IDs.
- **All connections are bidirectional**: every write operation (create map, add, update, delete) mirrors changes to neighbour documents.
- **`CacheData`** is populated via `@PostConstruct` in `NodeService` and refreshed after every write with a `findAll()`. Read endpoints (`getMap`, `getPath`) never hit MongoDB.
- **`NodeService`** owns the cache lifecycle. **`PathService`** only reads from `CacheData`.
- **`MapService.java`** exists as an empty deprecated stub (cannot be deleted); ignore it.

### Data model

```
NodeDocument { id: String (UUID), name: String, connections: Map<String, Integer> }
```

Connection keys in the DB are UUIDs. All API inputs and outputs use node **names**.

## REST API

All endpoints are under `/map`. Errors return `409 Conflict` with `{ "message": "..." }`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/map` | Return all nodes and their named connections |
| `POST` | `/map` | Replace entire map (validates bidirectional connections + weights) |
| `POST` | `/map/node` | Add a single node; wires reverse connections on neighbours |
| `PUT` | `/map/node/{name}` | Update a node's connections (adds/updates/removes bidirectionally) |
| `DELETE` | `/map/node/{name}` | Delete a node; removes it from all neighbours |
| `GET` | `/map/path?from=X&to=Y` | Run Dijkstra; returns total distance and ordered path segments |

### Example POST /map body

```json
{
  "nodes": [
    { "name": "Amsterdam", "connections": { "Berlin": 7, "Paris": 3 } },
    { "name": "Berlin",    "connections": { "Amsterdam": 7, "Prague": 5 } },
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

## Dependencies

| Dependency | Role |
|---|---|
| `spring-boot-starter-web` | REST layer |
| `spring-boot-starter-data-mongodb` | MongoDB driver + repositories |
| `spring-boot-starter-validation` | Bean Validation (`@NotBlank`, `@NotNull` on request DTOs) |
| `lombok` | `@Getter`/`@Setter`/`@Slf4j`/`@RequiredArgsConstructor` throughout |
