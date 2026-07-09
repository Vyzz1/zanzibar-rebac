# zanzibar-rebac

A Zanzibar-inspired **ReBAC** (Relationship-Based Access Control) authorization
engine, built as Java 21 microservices and inspired by
[Google's Zanzibar paper](https://research.google/pubs/pub48190/).

The core question this system answers:

> **Does subject `S` have relation `R` on object `O`?**
> e.g. *"Does `user:bob` have `viewer` on `doc:report.pdf`?"*

> **Status:** early scaffolding. The Maven/Spring/gRPC skeleton compiles and the
> module boundaries are in place, but the core authorization logic is not yet
> implemented — most classes are placeholders.

---

## Architecture

A Maven multi-module project. Services never depend on each other as Maven
modules — they talk **gRPC only** at runtime and share code exclusively through
`api` and `shared`.

```
zanzibar-rebac/
├── api/                  # protobuf → generated gRPC classes
├── shared/               # shared domain models, utils, test base classes
├── check-service/        # hot path: Check + BatchCheck gRPC
├── tuple-store/          # write path: WriteTuples gRPC + outbox
├── watch-service/        # streaming: Watch gRPC + RabbitMQ consumer
├── namespace-manager/    # REST API for namespace config CRUD
└── infra/                # docker-compose for local dependencies
```

| Module | HTTP | gRPC | Role |
|---|---|---|---|
| check-service | 8081 | 9091 | Answer permission checks (read path, cache) |
| tuple-store | 8082 | 9092 | Persist relation tuples + outbox event publish |
| watch-service | 8083 | 9093 | Stream tuple changes to clients |
| namespace-manager | 8084 | — | CRUD + validation of namespace configs |

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 (records, sealed interfaces, virtual threads) |
| Framework | Spring Boot 3.5.1 (YAML config only) |
| RPC | gRPC via `net.devh` grpc-server-spring-boot-starter |
| Reads / Writes | Spring Data JPA (reads) · JOOQ (tuple writes) |
| Cache | Redis |
| Messaging | RabbitMQ (Spring AMQP) |
| Database | PostgreSQL 16 |
| Observability | Micrometer + OpenTelemetry (Prometheus scrape) |
| Testing | JUnit 5 · Testcontainers · Mockito · AssertJ |
| Build | Maven 3.9+ |
| Formatting | Spotless (google-java-format) |

---

## Getting started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Start local infrastructure

Starts PostgreSQL, Redis, and RabbitMQ:

```bash
make infra
# or:  docker compose --profile main up
```

RabbitMQ management UI is available at http://localhost:15672 (guest / guest).

### Build

```bash
mvn clean package -DskipTests
```

### Run a service

```bash
mvn -pl check-service spring-boot:run
# or run the built jar:
java -jar check-service/target/check-service.jar
```

### Call a gRPC endpoint

```bash
grpcurl -plaintext localhost:9092 zanzibar.api.v1.AuthorizationService/WriteTuples
grpcurl -plaintext localhost:9091 zanzibar.api.v1.AuthorizationService/Check
```

---

## Core concepts

- **RelationTuple** — the atomic unit, e.g.
  `(namespace="doc", object="report.pdf", relation="viewer", subject="user:bob")`.
- **UsersetRewrite** — how relations compose (UNION / INTERSECTION / EXCLUSION,
  computed usersets). Evaluated recursively by `GraphTraverser`, with cycle
  detection.
- **Zookie** — an opaque consistency token returned on every write; used to
  guarantee a check never returns a stale result. Its HMAC must be validated
  before the token is trusted.
- **Outbox pattern** — `tuple-store` writes the tuple and an outbox event in the
  same transaction; `OutboxPoller` later publishes to RabbitMQ. This guarantees
  no event is lost even if the service crashes mid-publish.

---

## Development

```bash
make format          # apply the shared code style (mvn spotless:apply)
mvn spotless:check   # verify formatting without changing files
make checkstyle      # run static analysis (mvn checkstyle:check)
mvn test             # run all tests
mvn verify           # full build; enforces formatting + checkstyle
make down            # stop local infrastructure
```

Quality is enforced on `mvn verify`, which fails if any file is unformatted
(Spotless) or breaks a lint rule (Checkstyle). Run `make format` before
committing. Checkstyle rules live in
[`config/checkstyle/checkstyle.xml`](config/checkstyle/checkstyle.xml) — they
ban `System.out.println`, unused/redundant imports, and empty catch blocks.

### Logging

Logging is configured once for all services in
[`shared/src/main/resources/logback-spring.xml`](shared/src/main/resources/logback-spring.xml).

- **Default / dev** — human-readable colored console, tagged with the service
  name and `[traceId,spanId]` correlation IDs.
- **`json` / `prod` profile** — ECS structured JSON to stdout, ready for
  Elasticsearch / Loki / any log pipeline:

  ```bash
  SPRING_PROFILES_ACTIVE=json java -jar check-service/target/check-service.jar
  ```

Always log through SLF4J (`private static final Logger log = ...`) — never
`System.out.println`.

See [`CLAUDE.md`](CLAUDE.md) for detailed coding conventions and
[`AGENTS.md`](AGENTS.md) for per-service ownership boundaries.
