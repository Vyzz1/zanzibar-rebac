# Benchmark

Measured results for the check path, produced by [`tools/loadtest`](tools/loadtest). Reproduce with
`make bench` — the harness seeds its own fixtures, so any database will do.

## Environment

| | |
|---|---|
| Machine | Windows 11, 16 logical CPUs |
| Runtime | Java 21 |
| Layout | Postgres, Redis, RabbitMQ, all three services **and the client** on one laptop |
| Date | 2026-08-20 |

Everything shares the same cores, and the load generator is closed-loop: a fixed number of virtual
threads each issue the next call as soon as the previous returns. **These are not capacity numbers.**
Read the ratios between rows, not the absolute figures.

## Latency — one call at a time

`--concurrency=1 --duration=10`. Closest to what a single unloaded check costs.

| scenario | req/s | p50 ms | p95 ms | p99 ms |
|---|---|---|---|---|
| `direct-cached` | 1,054 | **0.88** | 1.49 | 1.94 |
| `direct-fresh` | 352 | 2.72 | 3.93 | 5.00 |
| `derived-fresh` | 241 | 4.01 | 5.50 | 6.63 |
| `group-fresh` | 227 | 4.23 | 5.99 | 7.24 |
| `batch10-cached` | 741 | 1.28 | 1.90 | 2.50 |

## Throughput — 32 concurrent callers

`--concurrency=32 --duration=15`. The p50 here is latency *under load* on a saturated laptop, which
is a different measurement from the table above.

| scenario | req/s | p50 ms | p95 ms | p99 ms |
|---|---|---|---|---|
| `direct-cached` | **8,835** | 3.38 | 5.81 | 7.97 |
| `direct-fresh` | 2,870 | 10.43 | 16.92 | 21.75 |
| `derived-fresh` | 1,614 | 18.63 | 30.27 | 38.44 |
| `group-fresh` | 1,839 | 16.90 | 24.21 | 28.74 |
| `batch10-cached` | 2,678 (**26,780 checks/s**) | 11.64 | 14.80 | 17.13 |

No errors in either run.

## What the numbers say

**The cache is worth about 3×**, on both latency (0.88 ms → 2.72 ms) and throughput (8.8k → 2.9k
req/s). That same 3× is the price of passing a Zookie: insisting on a result no older than a write
you have already observed means the cached answer cannot be used.

**Traversing the relation graph costs roughly 1.5×** over a direct lookup — 2.72 ms to 4.01 ms for
`union(this, computedUserset(editor))`, and 4.23 ms to resolve a subject by expanding
`group:eng#member`. Recursion is real but it is not the dominant cost; the round trip and the
database read are.

**Batching pays for itself.** Ten checks in one call take 1.28 ms, against 8.8 ms for ten separate
cached calls, and at 32 threads it moves ~27k checks/s where one-at-a-time manages ~8.8k. Nearly all
of that is round-trip overhead, not evaluation.

## How the uncached path is measured

The `*-fresh` scenarios send a **Zookie dated in the future**. It is always newer than any cached
snapshot, so check-service must bypass the cache and evaluate against Postgres. This measures the
cold path with the service configured exactly as it normally runs, rather than restarting it with
the cache disabled — the difference between the rows is then a property of the protocol, not of a
special build.

## Caveats

- One machine, so client and services compete for CPU; a real deployment separates them.
- Closed-loop, so `req/s` is what this client could pull at that concurrency, not an offered rate
  the system was asked to sustain.
- A single fixture object, so Postgres serves everything from cache and the numbers say nothing
  about behaviour on a large tuple set.
- Windows and Docker Desktop add overhead a Linux host would not.
