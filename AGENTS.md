# AGENTS.md

This file defines agent boundaries, responsibilities, and coordination rules
for agentic workflows operating on this repository.

Each section defines one agent: what it owns, what it must not touch,
and how it communicates with other agents.

---

## Agent overview

```
┌─────────────────────────────────────────────────────┐
│                    Orchestrator                     │
│   Reads task, delegates to correct service agent   │
└────┬──────────┬──────────┬──────────┬──────────────┘
     │          │          │          │
     ▼          ▼          ▼          ▼
 Check-     Tuple-     Watch-    Namespace-
 Agent      Agent      Agent     Agent
     │          │          │          │
     └──────────┴──────────┴──────────┘
                     │
              Shared-Agent
         (shared/ and api/ only)
```

---

## Orchestrator

### Role
Entry point for any multi-service task. Reads the task description,
identifies which service(s) are affected, and delegates to the correct agents.
Never writes code directly — only coordinates.

### Owns
- Nothing in the codebase
- Reads `CLAUDE.md` and `AGENTS.md` before delegating
- Reads phase plan to understand current progress

### Decision rules
| Task type | Delegate to |
|---|---|
| Permission check logic, cache, Zookie read path | Check-Agent |
| Writing tuples, outbox, RabbitMQ publish | Tuple-Agent |
| Streaming events, Watch gRPC, RabbitMQ consume | Watch-Agent |
| Namespace config, userset rewrite definitions | Namespace-Agent |
| Shared domain models, proto changes, test base | Shared-Agent |
| Multi-service feature (e.g. new permission type) | Shared-Agent first, then service agents |

### Must NOT
- Write any Java, YAML, or SQL directly
- Make assumptions about which service owns a concept — check `AGENTS.md` boundaries first
- Delegate the same task to two agents simultaneously if they share a dependency

---

## Check-Agent

### Role
Owns the hot path. Everything related to answering "does this subject have this permission?"

### Owns
```
services/check-service/src/
├── grpc/CheckGrpcService.java
├── grpc/interceptor/ZookieInterceptor.java
├── grpc/interceptor/TracingInterceptor.java
├── domain/CheckUseCase.java
├── domain/GraphTraverser.java
├── domain/CycleDetector.java
├── cache/TupleCache.java
├── cache/CacheKeyStrategy.java
├── repository/TupleReadRepository.java
├── config/RedisConfig.java
├── config/GrpcServerConfig.java
└── resources/application.yml
```

### Primary responsibilities
- Implement `Check` and `BatchCheck` gRPC methods
- Implement `GraphTraverser` — recursive userset rewrite evaluation
- Implement `CycleDetector` — track visited nodes per request
- Implement Zookie freshness check against Redis cache snapshot timestamp
- Read tuples from PostgreSQL (read-only — never writes to `relation_tuples`)
- Read namespace configs from `namespace-manager` via gRPC (never hardcode relations)

### Must NOT
- Write to `relation_tuples` table — that is Tuple-Agent's domain
- Implement `Watch` or `Expand` gRPC methods
- Import anything from `tuple-store`, `watch-service`, or `namespace-manager` Maven modules
- Put traversal logic inside `CheckGrpcService` — delegate to `CheckUseCase` then `GraphTraverser`
- Cache namespace configs in Redis — namespace-manager owns that

### Key invariants to preserve
- `GraphTraverser` must detect cycles — never recurse without `CycleDetector`
- Cache lookup must check `snapshot_ts >= zookie.ts` before returning cached result
- HMAC on Zookie must be validated via `ZookieValidator` before trusting the timestamp
- `BatchCheck` must run individual checks in parallel via `CompletableFuture`
- P99 latency target: under 5ms for cached checks

### Tests this agent must maintain
```
unit/GraphTraverserTest.java           — all rewrite combinations
unit/CycleDetectorTest.java            — direct cycle, transitive cycle
unit/CacheKeyStrategyTest.java
integration/CheckServiceIntegrationTest.java
integration/ZookieConsistencyTest.java — most critical, must never be skipped
```

---

## Tuple-Agent

### Role
Owns the write path. Everything related to persisting relation tuples and
guaranteeing event delivery via outbox.

### Owns
```
services/tuple-store/src/
├── grpc/TupleStoreGrpcService.java
├── domain/WriteTuplesUseCase.java
├── domain/ZookieMinter.java
├── repository/TupleWriteRepository.java
├── repository/TupleWriteRepositoryImpl.java   # JOOQ
├── outbox/OutboxPoller.java
├── outbox/OutboxRepository.java
├── rabbitmq/TupleEventPublisher.java
└── resources/application.yml
```

### Primary responsibilities
- Implement `WriteTuples` gRPC method
- Write tuples using JOOQ (not JPA) to capture `RETURNING commit_timestamp`
- Mint Zookie from commit timestamp using `ZookieMinter`
- Implement outbox pattern: write tuple + outbox event in same transaction
- `OutboxPoller`: poll with `SELECT FOR UPDATE SKIP LOCKED`, publish to RabbitMQ, mark published

### Must NOT
- Read tuples for permission checking — that is Check-Agent's domain
- Publish to RabbitMQ directly in the write transaction — always go through outbox
- Use JPA `save()` for tuple inserts — must use JOOQ to get commit timestamp
- Implement any rewrite or traversal logic

### Key invariants to preserve
- Tuple write + outbox insert MUST be in the same `@Transactional` block
- `OutboxPoller` MUST use `SELECT FOR UPDATE SKIP LOCKED` — not simple SELECT
- Zookie HMAC key must come from config, never hardcoded
- Outbox events must be idempotent — publishing twice must not cause duplicate tuples

### DB tables this agent owns
```sql
relation_tuples   -- INSERT only, never UPDATE or DELETE from this agent
outbox_events     -- INSERT by WriteTuplesUseCase, UPDATE by OutboxPoller
```

### Tests this agent must maintain
```
unit/ZookieMinterTest.java
unit/OutboxPollerTest.java             — idempotency, SKIP LOCKED behavior
integration/WriteTuplesIntegrationTest.java
integration/OutboxSurvivalTest.java    — crash mid-publish, no event loss
```

---

## Watch-Agent

### Role
Owns realtime streaming. Consumes RabbitMQ events and pushes to connected gRPC clients.

### Owns
```
services/watch-service/src/
├── grpc/WatchGrpcService.java
├── rabbitmq/TupleChangeConsumer.java
├── stream/StreamRegistry.java
├── stream/WatchCursor.java
└── resources/application.yml
```

### Primary responsibilities
- Implement `Watch` gRPC server-streaming method
- Consume the `tuple-changes` RabbitMQ stream
- Manage `StreamRegistry` — map of namespace → connected `StreamObserver` list
- Handle client disconnect gracefully — remove from registry, no memory leak
- Decode Zookie as a RabbitMQ stream offset cursor — replay from correct offset on reconnect
- Handle backpressure — bounded buffer per client, disconnect slow clients

### Must NOT
- Read from PostgreSQL directly — has no DB connection
- Implement any permission check logic
- Implement `Check` or `WriteTuples` gRPC methods
- Hold unbounded state in `StreamRegistry` — enforce max connected clients per namespace

### Key invariants to preserve
- Client disconnect must always remove from `StreamRegistry` — use `onError` + `onCompleted` hooks
- On reconnect with a Zookie cursor, must replay from that offset — no event skipping
- Each Watch-Agent instance binds its own exclusive queue (fanout exchange) for broadcast behavior
- Never block the RabbitMQ consumer thread with slow client sends — use async dispatch

### Tests this agent must maintain
```
unit/StreamRegistryTest.java           — register, deregister, concurrent access
unit/WatchCursorTest.java              — decode Zookie to RabbitMQ stream offset
integration/WatchStreamIntegrationTest.java — write tuple → event appears in stream < 1s
integration/ReconnectTest.java         — disconnect, reconnect with cursor, no missed events
```

---

## Namespace-Agent

### Role
Owns schema management. Defines what relations exist and how they compose.

### Owns
```
services/namespace-manager/src/
├── controller/NamespaceController.java
├── domain/NamespaceConfig.java
├── domain/ValidateNamespaceUseCase.java
├── repository/NamespaceConfigRepository.java
└── resources/application.yml
```

### Primary responsibilities
- REST CRUD for namespace configs (`/api/v1/namespaces`)
- Validate namespace config before saving — reject configs that reference undefined relations
- Detect cycles in userset rewrite definitions at config time (not at check time)
- Serve namespace configs to `check-service` via gRPC `GetNamespaceConfig` RPC
- Version namespace configs — changes must not break existing tuples

### Must NOT
- Make permission check decisions
- Write to `relation_tuples` — that is Tuple-Agent's domain
- Allow namespace configs with cycles — validate eagerly, reject at write time
- Delete a namespace that has existing tuples — return 409 Conflict

### Key invariants to preserve
- Namespace config changes are versioned — `check-service` caches by version
- Validation must reject: undefined relations in rewrites, cycles in rewrite graph
- Config stored as JSONB in PostgreSQL — never as raw string

### DB tables this agent owns
```sql
namespace_configs   -- full CRUD
```

### Tests this agent must maintain
```
unit/ValidateNamespaceUseCaseTest.java  — valid config, cycle detection, undefined relation
integration/NamespaceControllerTest.java
integration/NamespaceVersioningTest.java — change config, check-service picks up new version
```

---

## Shared-Agent

### Role
Owns everything in `shared/` and `api/`. The only agent allowed to change
proto definitions or shared domain models.

### Owns
```
api/proto/                             # all .proto files
api/src/                               # generated gRPC stubs (do not edit manually)
shared/src/main/java/
├── domain/RelationTuple.java
├── domain/Zookie.java
├── domain/UsersetRewrite.java         # sealed interface + all subtypes
├── security/ZookieValidator.java
├── observability/TracingConfig.java
├── observability/MetricsConfig.java
└── testing/BaseIntegrationTest.java
```

### Primary responsibilities
- Maintain proto definitions — single source of truth for all gRPC contracts
- Maintain shared domain records and sealed interfaces
- Maintain `BaseIntegrationTest` — Testcontainers lifecycle, `@DynamicPropertySource`
- Maintain `ZookieValidator` — used by Check-Agent and Tuple-Agent

### Must NOT
- Add Spring-managed beans to `shared/` that depend on service-specific config
- Add any HTTP client or repository to `shared/`
- Change a proto message in a backward-incompatible way without bumping the version
- Add service-specific logic to shared domain objects

### Proto change protocol
1. Check if change is backward compatible (adding optional field = ok, removing field = not ok)
2. If breaking: bump proto package version (`v1` → `v2`), keep old version until all consumers migrated
3. Regenerate Java stubs: `mvn compile -pl api`
4. Notify Orchestrator — all service agents must update their imports

### Tests this agent must maintain
```
unit/ZookieValidatorTest.java          — valid HMAC, tampered token, expired token
unit/UsersetRewriteTest.java           — sealed interface exhaustiveness
```

---

## Cross-agent coordination rules

### When a task touches multiple agents
Sequence matters. Always in this order:
1. **Shared-Agent first** — if proto or shared domain changes are needed
2. **Namespace-Agent** — if new relation types are introduced
3. **Tuple-Agent** — if new tuple shapes need to be written
4. **Check-Agent** — if check logic needs updating for new relations
5. **Watch-Agent** — if new event types need streaming

### Communication between agents
- Agents never share Maven module code — only through `api` and `shared`
- Service-to-service calls at runtime are gRPC only
- Agents must not assume another agent's internal implementation — only the gRPC contract

### Conflict resolution
If two agents need to modify the same file, stop and escalate to Orchestrator.
This should never happen if boundaries above are respected.
The most common mistake: Check-Agent trying to put read logic in `tuple-store` package.

### Definition of done per agent task
- [ ] Unit tests pass for changed classes
- [ ] Integration tests pass (Testcontainers)
- [ ] No imports across service module boundaries
- [ ] `mvn verify` passes at root level
- [ ] No hardcoded values — all config in `application.yml`
