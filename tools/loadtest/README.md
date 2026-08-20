# loadtest

Measures the check path against a running system, so the numbers quoted anywhere about this project
come from a repeatable run rather than a guess.

## What it measures

The scenarios are paired deliberately — the interesting figures are the *ratios* between rows, not
any single number.

| Scenario | What it exercises |
|---|---|
| `direct-cached` | The steady-state hot path: answer served from Redis. |
| `direct-fresh` | The same question with a Zookie the cache cannot satisfy, so check-service evaluates against Postgres. The gap versus `direct-cached` is what the cache buys — and what strong consistency costs. |
| `derived-fresh` | `viewer` reached through `union(this, computedUserset(editor))`: one extra hop of graph traversal. |
| `group-fresh` | The grant is to `group:eng#member`, so the subject is only found by expanding a userset. |
| `batch10-cached` | Ten questions in one round trip, showing what batching saves on network overhead. |

The "fresh" rows use a **Zookie dated in the future**. That is always newer than any cached
snapshot, so check-service must bypass its cache — a legitimate in-protocol way to measure the cold
path with the service configured exactly as it runs normally, rather than restarting it with the
cache disabled.

## Running it

Start the infrastructure and the three services the check path needs:

```bash
make infra-postgres                                     # Postgres + Redis + RabbitMQ
mvn clean package -DskipTests

java -jar namespace-manager/target/namespace-manager.jar &   # :8084 / :9094 — configs
java -jar tuple-store/target/tuple-store.jar &               # :9092      — writes
java -jar check-service/target/check-service.jar &           # :9091      — checks
```

Then:

```bash
make bench
# or, with options:
java -jar tools/loadtest/target/loadtest.jar --concurrency=64 --duration=30
```

The tool seeds its own fixtures (a namespace whose `viewer` is partly derived, plus the tuples the
scenarios ask about) before measuring, so a fresh database is fine.

| Option | Default |
|---|---|
| `--check-host`, `--check-port` | `localhost`, `9091` |
| `--tuple-host`, `--tuple-port` | `localhost`, `9092` |
| `--namespace-url` | `http://localhost:8084` |
| `--token` | `dev-only-token-change-me` |
| `--zookie-secret` | `dev-only-secret-change-me` (must match the services) |
| `--concurrency` | `32` virtual threads |
| `--duration` / `--warmup` | `20` / `5` seconds per scenario |

## Reading the output

Every scenario is warmed up first and those samples are discarded — on the JVM the first seconds
measure the JIT, not the system.

This is a **closed-loop** test: a fixed number of virtual threads each issue the next call as soon
as the previous returns. Throughput is therefore what this client could pull at that concurrency,
not an offered rate the system was asked to sustain. Running the client on the same machine as the
services also means they compete for the same cores.

Treat the results as **relative** — cached versus fresh, direct versus derived — and quote them with
the environment line the tool prints above the table. They are not production capacity numbers.
