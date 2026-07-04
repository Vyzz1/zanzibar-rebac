# CLAUDE.md

This file tells Claude Code how to work in this repository.
Read this before writing any code, creating any file, or running any command.

---

## Project overview

A Zanzibar-inspired ReBAC (Relationship-Based Access Control) authorization engine
built as Java 21 microservices. Inspired by Google's Zanzibar paper.

Core question this system answers: "Does subject S have relation R on object O?"

---

## Repository layout

```
zanzibar-rebac/
├── api/                  # protobuf definitions → generated gRPC classes
├── shared/               # shared domain models, utils, test base classes
├── services/
│   ├── check-service/    # hot path: Check + BatchCheck gRPC
│   ├── tuple-store/      # write path: WriteTuples gRPC + outbox
│   ├── watch-service/    # streaming: Watch gRPC + RabbitMQ consumer
│   └── namespace-manager/# REST API for namespace config CRUD
├── infra/                # docker-compose, k8s manifests, Helm charts
└── tools/                # load test scripts, token generator CLI
```

This is a **Maven multi-module project**. Root `pom.xml` manages all versions.
Never add a version to a child module `pom.xml` unless it overrides the BOM.

---

## Tech stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Java 21 | Use virtual threads, sealed classes, records |
| Framework | Spring Boot 3.5.1 | YAML config only, no properties files |
| gRPC | grpc-server-spring-boot-starter 3.1.0 | net.devh |
| ORM (reads) | Spring Data JPA | For simple queries |
| ORM (writes) | JOOQ | For tuple writes needing RETURNING clause |
| Cache | Redis Cluster | Spring Data Redis |
| Messaging | RabbitMQ | Spring AMQP |
| DB | PostgreSQL 16 | Single instance for dev, connection pool via HikariCP |
| Observability | Micrometer + OpenTelemetry | Prometheus scrape endpoint |
| Testing | JUnit 5 + Testcontainers + Mockito + AssertJ | |
| Build | Maven 3.9+ | |

---

## Coding conventions

### Java style
- Use **records** for immutable value objects: `RelationTuple`, `Zookie`, `WatchCursor`
- Use **sealed interfaces** for algebraic types: `UsersetRewrite` and its subtypes
- Use **virtual threads** for blocking I/O — configure in `application.yml`:
  `spring.threads.virtual.enabled: true`
- Never use `var` where the type is non-obvious
- Prefer `Optional` over null returns in domain layer
- Exception hierarchy: domain exceptions extend `ZanzibarException` (in shared)

### Package structure per service
```
zanzibar.huynhvy.{service}/
├── grpc/          # gRPC service implementations and interceptors
├── domain/        # use cases, domain logic, no framework annotations except @Service
├── repository/    # data access, JPA repositories, JOOQ DAOs
├── cache/         # Redis operations
├── rabbitmq/      # producers and consumers
└── config/        # Spring @Configuration classes only
```

### Naming
- gRPC impl classes: `{Name}GrpcService.java` annotated `@GrpcService`
- Use cases: `{Verb}{Noun}UseCase.java` annotated `@Service`
- Repositories: `{Entity}Repository.java` (interface) + `{Entity}RepositoryImpl.java`
- Config classes: `{Concern}Config.java` annotated `@Configuration`

### What NOT to do
- Never put business logic in `@Configuration` classes
- Never inject repositories directly into gRPC service classes — go through use cases
- Never use `System.out.println` — use SLF4J logger
- Never hardcode secrets or URLs — use `application.yml` placeholders
- Never add `@Transactional` to gRPC service layer — only on use case or repository layer
- Never import between service modules — services communicate via gRPC only

---

## Module dependency rules

```
check-service   → api, shared
tuple-store     → api, shared
watch-service   → api, shared
namespace-manager → api, shared
shared          → (nothing internal)
api             → (nothing internal)
```

Services never depend on each other as Maven modules.
Cross-service communication is gRPC only.

---

## Running locally

### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Start infrastructure
```bash
cd infra
docker-compose up -d
```
Starts: PostgreSQL 16, Redis 7, RabbitMQ.

### Run a service
```bash
# from repo root
mvn -pl services/check-service spring-boot:run

# or build first
mvn clean package -DskipTests
java -jar services/check-service/target/check-service.jar
```

### Default ports

| Service | HTTP | gRPC |
|---|---|---|
| check-service | 8081 | 9091 |
| tuple-store | 8082 | 9092 |
| watch-service | 8083 | 9093 |
| namespace-manager | 8084 | — |

### Test a gRPC endpoint
```bash
grpcurl -plaintext localhost:9092 zanzibar.api.v1.AuthorizationService/WriteTuples
grpcurl -plaintext localhost:9091 zanzibar.api.v1.AuthorizationService/Check
```

---

## Testing conventions

### Unit tests
- Location: `src/test/java/.../unit/`
- No Spring context — pure Java, Mockito only
- Must run in < 100ms each
- Cover: `GraphTraverser`, `CycleDetector`, `ZookieMinter`, `ZookieValidator`, all userset rewrite combinations

### Integration tests
- Location: `src/test/java/.../integration/`
- Extend `BaseIntegrationTest` from `shared` module
- `BaseIntegrationTest` spins up Testcontainers (PostgreSQL, Redis, RabbitMQ) once per test suite via `@DynamicPropertySource`
- Never spin up containers per test class — too slow
- Key integration tests to always have:
  - `CheckServiceIntegrationTest` — write tuple → check → ALLOW
  - `ZookieConsistencyTest` — write → receive zookie → check with zookie → never stale DENY
  - `OutboxSurvivalTest` — simulate crash mid-publish → restart → event delivered exactly once

### Running tests
```bash
# all tests
mvn test

# single module
mvn test -pl services/check-service

# single test class
mvn test -pl services/check-service -Dtest=ZookieConsistencyTest
```

---

## Domain concepts — must understand before coding

### RelationTuple
The atomic unit. Stored in PostgreSQL.
```
(namespace="doc", object_id="report.pdf", relation="viewer", subject_id="user:bob")
```

### Zookie
Opaque consistency token returned on every write.
Structure: `base64( version_byte | commit_timestamp_ns | HMAC-SHA256(secret, ts) )`
Never trust a Zookie without validating the HMAC first.

### UsersetRewrite
Defines how relations compose. Evaluated recursively by `GraphTraverser`.
```
viewer = UNION(
  This,                          // direct tuple lookup
  ComputedUserset("editor"),     // editors are also viewers
  INTERSECTION(
    ComputedUserset("org#member"),
    EXCLUSION(ComputedUserset("blocked"))
  )
)
```

### Cycle detection
`GraphTraverser` tracks `Set<String> visited` per request where key = `namespace:objectId:relation`.
If a node is visited twice, throw `CyclicRelationException` — never recurse infinitely.

### Cache key
```
{namespace}:{objectId}:{relation}:{subjectId}
```
Example: `doc:report.pdf:viewer:user:bob`
Cache value stores result + snapshot timestamp for Zookie freshness check.

---

## Outbox pattern — important

`tuple-store` never publishes to RabbitMQ directly in the write transaction.
Always:
1. INSERT tuple into `relation_tuples`
2. INSERT event into `outbox_events` — **same transaction**
3. `OutboxPoller` polls every 500ms, publishes to RabbitMQ, marks as published

This guarantees no event is lost even if the service crashes between write and publish.
`OutboxPoller` uses `SELECT ... FOR UPDATE SKIP LOCKED` to be safe with multiple instances.

---

## Common mistakes to avoid

- Adding `@Transactional` on gRPC layer — put it on use case or repository
- Calling `Expand` from `Check` logic — Expand is admin-only, never on hot path
- Forgetting `fill="none"` on connector paths — irrelevant here but habit from other projects
- Using `JpaRepository.save()` for tuple writes — use JOOQ to get `RETURNING commit_timestamp`
- Catching `Exception` broadly in `GraphTraverser` — only catch `CyclicRelationException`
- Putting tenant/namespace logic in `check-service` — namespace resolution lives in `namespace-manager`

---

## Adding a new feature — checklist

- [ ] Does it belong in a specific service? Check module dependency rules above
- [ ] Is there a unit test covering the core logic?
- [ ] Is there an integration test for the happy path?
- [ ] Does it need a new metric? Add to Micrometer config
- [ ] Does it change the proto contract? Update `api/` module and bump proto version
- [ ] Does it need a DB migration? Add SQL file to `src/main/resources/db/migration/`
- [ ] Does it affect the Zookie flow? Verify `ZookieConsistencyTest` still passes
