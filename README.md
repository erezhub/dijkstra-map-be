# Dijkstra Map Backend

Two Spring Boot microservices sharing a `common` module:

- **map-service** (port 8080) — stores a weighted graph of named nodes in MongoDB and exposes Dijkstra's shortest-path algorithm over HTTP.
- **user-service** (port 8081) — user management with role-based access control (ADMIN → MANAGER → REGULAR) and opaque-token authentication.

---

## Prerequisites

- Java 21, Maven 3.9+ (for local development)
- Docker & Docker Compose (for containerised deployment)

---

## Build & Deploy

### Local

```bash
# Build all modules
mvn clean install

# Start map-service (MongoDB must be running)
mvn spring-boot:run -pl map-service

# Start user-service (MongoDB must be running)
mvn spring-boot:run -pl user-service
```

Both services expect MongoDB at `localhost:27017` with credentials `admin / password`.  
See each module's `src/main/resources/application.properties` for all property keys.

### Docker Compose

```bash
# Build images and start all services
docker compose up --build

# Stop
docker compose down
```

| Service | URL |
|---|---|
| map-service | http://localhost:8080 |
| user-service | http://localhost:8081 |
| mongo-express (DB browser) | http://localhost:8082 |

---

## Logging

Each service writes logs to console and to a rolling file under a `log/` directory relative to the working directory:

| Service | Active log | Archives |
|---|---|---|
| map-service | `log/dijkstra-map.log` | `log/YYYY-MM/dijkstra-map.<index>.log.gz` |
| user-service | `log/dijkstra-user.log` | `log/YYYY-MM/dijkstra-user.<index>.log.gz` |

Files roll when they reach 5 MB and are archived into a monthly subfolder with an incrementing index.

---

## Authentication

All map-service endpoints and all `/users` endpoints require a Bearer token:

```
Authorization: Bearer <token>
```

Obtain a token via `POST /auth/login`. The default admin credentials are `admin / admin`.

---

## Errors

All errors — validation failures, not found, authorization — return `409 Conflict`:

```json
{ "message": "descriptive error message" }
```

---

## user-service API

### POST /auth/login

No authentication required.

**Request**
```json
{
  "identifier": "admin",
  "password": "admin"
}
```
> ADMIN logs in with `identifier: "admin"`. MANAGER and REGULAR log in with their email address.

**Response 200**
```json
{ "token": "550e8400-e29b-41d4-a716-446655440000" }
```

---

### POST /auth/logout

Invalidates the caller's current token.

**Headers** `Authorization: Bearer <token>`

**Response 204** No content.

---

### GET /users

Returns users one level below the caller.

**Headers** `Authorization: Bearer <token>`  
**Roles** ADMIN → returns MANAGERs; MANAGER → returns REGULARs

**Response 200**
```json
[
  {
    "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "username": "Alice",
    "email": "alice@example.com",
    "role": "MANAGER"
  }
]
```

---

### POST /users

Creates a user one level below the caller.

**Headers** `Authorization: Bearer <token>`  
**Roles** ADMIN → creates MANAGER; MANAGER → creates REGULAR

**Request**
```json
{
  "username": "Alice",
  "email": "alice@example.com",
  "password": "secret"
}
```

**Response 201**
```json
{
  "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
  "username": "Alice",
  "email": "alice@example.com",
  "role": "MANAGER"
}
```

---

### GET /users/{id}

**Headers** `Authorization: Bearer <token>`  
**Roles** ADMIN or MANAGER

**Response 200** — same shape as the object in `GET /users`

---

### PUT /users/{id}

All fields are optional. Password can only be changed when updating yourself.

**Headers** `Authorization: Bearer <token>`  
**Roles** ADMIN or MANAGER

**Request**
```json
{
  "username": "Alice Updated",
  "email": "alice-new@example.com",
  "password": "newpassword"
}
```

**Response 200** — updated user object

---

### DELETE /users/{id}

**Headers** `Authorization: Bearer <token>`  
**Roles** ADMIN or MANAGER

**Response 204** No content.

---

### GET /users/me

Returns the caller's own profile. Available to all roles.

**Headers** `Authorization: Bearer <token>`

**Response 200** — same shape as the object in `GET /users`

---

### PUT /users/me

Updates the caller's own profile. All fields are optional. Available to all roles.

**Headers** `Authorization: Bearer <token>`

**Request** — same shape as `PUT /users/{id}`

**Response 200** — updated user object

---

## map-service API

All endpoints require `Authorization: Bearer <token>`.

---

### GET /map

Returns the full node graph.

**Response 200**
```json
{
  "nodes": [
    {
      "name": "A",
      "position": { "x": 0.0, "y": 0.0 },
      "connections": { "B": 5, "C": 10 }
    },
    {
      "name": "B",
      "position": { "x": 3.0, "y": 0.0 },
      "connections": { "A": 5 }
    },
    {
      "name": "C",
      "position": { "x": 0.0, "y": 4.0 },
      "connections": { "A": 10 }
    }
  ]
}
```
> `connections` keys are node names; values are edge weights.

---

### POST /map

Replaces the entire map. All connections are stored bidirectionally — you only need to declare each edge once (in either direction); the reverse edge is created automatically.

**Request**
```json
{
  "nodes": [
    {
      "name": "A",
      "position": { "x": 0.0, "y": 0.0 },
      "connections": { "B": 5, "C": 10 }
    },
    {
      "name": "B",
      "position": { "x": 3.0, "y": 0.0 },
      "connections": { "A": 5 }
    },
    {
      "name": "C",
      "position": { "x": 0.0, "y": 4.0 },
      "connections": { "A": 10 }
    }
  ]
}
```

**Response 201** No content.

---

### POST /map/node

Adds a single node and wires reverse connections on any referenced neighbours.

**Request**
```json
{
  "name": "D",
  "position": { "x": 1.0, "y": 1.0 },
  "connections": { "A": 3, "B": 7 }
}
```

**Response 201** No content.

---

### PUT /map/node/{name}

Updates a node's connections and/or position. Both fields are optional — omit what you don't want to change.

**Request**
```json
{
  "position": { "x": 2.0, "y": 2.0 },
  "connections": { "C": 4 }
}
```

**Response 200** No content.

---

### DELETE /map/node/{name}

Deletes the node and removes it from all neighbours' connection lists.

**Response 204** No content.

---

### GET /map/path?from={name}&to={name}

Runs Dijkstra's algorithm between two nodes.

**Query params** `from` and `to` — node names

**Response 200**
```json
{
  "distance": 15,
  "path": [
    { "from": "A", "to": "C", "distance": 10 },
    { "from": "C", "to": "B", "distance": 5 }
  ]
}
```
> `distance` is the total cost of the shortest path. `path` is the ordered list of hops.
