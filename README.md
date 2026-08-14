# zanzibar-rebac

A Zanzibar-inspired **ReBAC** (Relationship-Based Access Control) authorization
engine, built as Java 21 microservices and inspired by
[Google's Zanzibar paper](https://research.google/pubs/pub48190/).

The core question this system answers:

> **Does subject `S` have relation `R` on object `O`?**
> e.g. *"Does `user:bob` have `viewer` on `doc:report.pdf`?"*

> **Status:** the core is functionally complete. Every Zanzibar API method is
> implemented — Read, Write, Delete, Check, BatchCheck, Expand, Watch — over the
> full pipeline: relation tuples, per-namespace userset-rewrite config, recursive
> checks (with group/nested-group expansion), a Redis result cache gated by Zookie
> freshness and invalidated on writes, and an outbox → RabbitMQ event stream.
> Remaining work is hardening (observability, auth, flaky-test cleanup), not core
> features.

---

## What it does

- **Direct checks** — is there a stored tuple granting the relation?
- **Derived checks** — relations compose via per-namespace **userset rewrites**:
  - `ComputedUserset` — *editors are also viewers* (same object).
  - `TupleToUserset` — *viewers of the parent folder are viewers of the doc*.
  - `Union` / `Intersection` / `Exclusion`.
  - **Group expansion** — a tuple granting a userset subject (`group:eng#member`)
    is expanded recursively, so indirect and nested-group members resolve.
- **Consistency** — every write/delete returns a **Zookie** (signed commit token).
  A Check may pass a Zookie as a freshness floor: a cached result is only served
  when it is at least as fresh as a write the caller has already observed.
- **Caching** — Check results are cached in Redis and **evicted on tuple changes**
  (consumed from the event stream), so a revoke takes effect immediately.
- **Revocation** — `DeleteTuples` removes grants and propagates a `DELETE` event.
- **Introspection** — `ReadTuples` (paginated) lists raw tuples; `Expand` returns
  the effective userset tree for an `object#relation` (admin/debug).
- **Reliability** — tuple changes are published via the **outbox pattern**
  (tuple + event in one transaction, drained by a poller) to a **fanout exchange**,
  so every consumer (watch-service, check-service) gets every event.

---

## Architecture

A Maven multi-module project. Services never depend on each other as Maven
modules — they talk **gRPC only** at runtime and share code exclusively through
`api` (protobuf contract) and `shared` (domain models, Zookie, test base).

```
zanzibar-rebac/
├── api/                  # protobuf → generated gRPC classes
├── shared/               # shared domain models, Zookie, test base classes
├── check-service/        # query path: Check, BatchCheck, ReadTuples, Expand
├── tuple-store/          # write path: WriteTuples, DeleteTuples + outbox
├── watch-service/        # streaming: Watch (RabbitMQ → gRPC stream)
├── namespace-manager/    # config authority: REST + gRPC (GetNamespaceConfig)
├── config/               # shared Checkstyle config
└── infra/                # docker-compose for local dependencies
```

| Module | HTTP | gRPC | Role |
|---|---|---|---|
| check-service | 8081 | 9091 | Answer checks (Check/BatchCheck), Read & Expand; Redis cache |
| tuple-store | 8082 | 9092 | Write/Delete tuples (JOOQ) + outbox event publish |
| watch-service | 8083 | 9093 | Stream tuple changes to clients (consumes RabbitMQ) |
| namespace-manager | 8084 | 9094 | CRUD + validation + versioning of namespace configs |

### API surface

`AuthorizationService` (in `api/authorization.proto`) is split across services —
each implements only its slice, so a call to the wrong port returns `UNIMPLEMENTED`:

| Method | Served by | Port |
|---|---|---|
| `Check`, `BatchCheck`, `ReadTuples`, `Expand` | check-service | 9091 |
| `WriteTuples`, `DeleteTuples` | tuple-store | 9092 |
| `Watch` (server stream) | watch-service | 9093 |
| `NamespaceService.GetNamespaceConfig` | namespace-manager | 9094 |

Namespace configs are also managed over REST on namespace-manager (`8084`):
`PUT /api/v1/namespaces/{ns}`, `GET /api/v1/namespaces/{ns}[/versions/{v}]`.

### How a derived check flows

```
Check(doc:report.pdf#viewer@user:bob, zookie?)
  check-service ──gRPC──► namespace-manager   fetch config for "doc" (cached)
                └─► Redis                       fresh-enough cache hit? serve it
                └─► GraphTraverser              else evaluate the rewrite:
                        This            → direct tuple lookup (+ group expansion)
                        ComputedUserset → recurse on same object
                        TupleToUserset  → follow to another object
                    → cache the result (stamped with a DB snapshot)
```

A write flows the other way and keeps the cache honest:

```
WriteTuples / DeleteTuples (tuple-store)
  └─ tuple + outbox event, one transaction
       └─ OutboxPoller → RabbitMQ fanout exchange "tuple-changes"
            ├─ watch-service   → WatchEvent{CREATE|DELETE} to subscribers
            └─ check-service   → evict cached results for that object
```

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 (records, sealed interfaces, virtual threads) |
| Framework | Spring Boot 3.5.1 (YAML config only) |
| RPC | gRPC via `net.devh` grpc-server / grpc-client starters |
| Reads / Writes | Spring Data JPA (reads) · JOOQ (tuple writes, `RETURNING`) |
| Cache | Redis |
| Messaging | RabbitMQ (Spring AMQP), fanout exchange |
| Database | PostgreSQL 16 — one DB, a schema + least-privilege role per service |
| Config storage | JSONB (namespace configs), append-only versioned |
| Observability | Micrometer + OpenTelemetry (Prometheus scrape) |
| Testing | JUnit 5 · Testcontainers · Mockito · AssertJ |
| Build | Maven 3.9+ |
| Formatting / lint | Spotless (google-java-format) · Checkstyle |

---

## Getting started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Start local infrastructure

```bash
make infra            # Redis + RabbitMQ (bring your own Postgres on :5432)
make infra-postgres   # also start a containerized PostgreSQL
```

`infra/init-db.sql` provisions a per-service schema and login role on first
Postgres init. RabbitMQ management UI: http://localhost:15672 (guest / guest).

### Build

```bash
mvn clean package -DskipTests
```

### Run the services

Each service is a standalone Spring Boot app. The check path needs all four:

```bash
java -jar tuple-store/target/tuple-store.jar          # :9092
java -jar namespace-manager/target/namespace-manager.jar  # :8084 / :9094
java -jar check-service/target/check-service.jar      # :9091
java -jar watch-service/target/watch-service.jar      # :9093
# or during development:  mvn -pl check-service spring-boot:run
```

### Try it

Define how relations compose (REST), write a tuple, then check a derived grant:

```bash
# viewer = union(this, computedUserset(editor))
curl -X PUT http://localhost:8084/api/v1/namespaces/doc \
  -H 'Content-Type: application/json' \
  -d '{"editor":{"this":{}},"viewer":{"union":{"children":[{"this":{}},{"computedUserset":{"relation":"editor"}}]}}}'

# bob is an editor of report.pdf
grpcurl -plaintext -d '{"tuples":[{"namespace":"doc","objectId":"report.pdf","relation":"editor","subjectId":"user:bob"}]}' \
  localhost:9092 zanzibar.api.v1.AuthorizationService/WriteTuples

# ...so bob is a viewer, even without a viewer tuple:
grpcurl -plaintext -d '{"namespace":"doc","objectId":"report.pdf","relation":"viewer","subjectId":"user:bob"}' \
  localhost:9091 zanzibar.api.v1.AuthorizationService/Check    # -> { "allowed": true }
```

> gRPC server reflection is off — pass `-proto api/src/main/proto/authorization.proto`
> to `grpcurl`/`evans` if your client can't discover the service. All endpoints are
> plaintext (no TLS).

---

## Core concepts

- **RelationTuple** — the atomic unit, e.g.
  `(namespace="doc", object="report.pdf", relation="viewer", subject="user:bob")`.
  A subject may itself be a userset (`group:eng#member`).
- **UsersetRewrite** — how a relation composes (a sealed, Jackson-polymorphic
  model in `shared`), stored per namespace as JSONB and evaluated recursively by
  `GraphTraverser`, with `CycleDetector` bounding the recursion.
- **Zookie** — an opaque, HMAC-signed consistency token returned on every write /
  delete. Never trust one without validating the HMAC; check-service uses its
  commit timestamp as a cache freshness floor.
- **Outbox pattern** — `tuple-store` writes the tuple and an outbox event in the
  same transaction; `OutboxPoller` (`SELECT … FOR UPDATE SKIP LOCKED`) later
  publishes to the RabbitMQ fanout exchange. No event is lost on a crash.

---

## Development

```bash
make format          # apply the shared code style (mvn spotless:apply)
mvn spotless:check   # verify formatting without changing files
make checkstyle      # run static analysis (mvn checkstyle:check)
mvn test             # run unit + integration tests (Testcontainers)
mvn verify           # full build; enforces formatting + checkstyle
make down            # stop local infrastructure
```

Quality is enforced on `mvn verify`, which fails if any file is unformatted
(Spotless) or breaks a lint rule (Checkstyle). Integration tests use
Testcontainers (Postgres, RabbitMQ, Redis) and run on CI (Linux).

### Logging

Logging is configured once for all services in
[`shared/src/main/resources/logback-spring.xml`](shared/src/main/resources/logback-spring.xml).

- **Default / dev** — human-readable colored console, tagged with the service
  name and `[traceId,spanId]` correlation IDs.
- **`json` / `prod` profile** — ECS structured JSON to stdout:

  ```bash
  SPRING_PROFILES_ACTIVE=json java -jar check-service/target/check-service.jar
  ```

Always log through SLF4J — never `System.out.println` (Checkstyle enforces this).

See [`CLAUDE.md`](CLAUDE.md) for detailed coding conventions and
[`AGENTS.md`](AGENTS.md) for per-service ownership boundaries.
