# Design Trade-offs

This document explains the main architectural decisions in Dropbox Lite, why they are appropriate for the current system, their practical trade-offs, and how the design could evolve as scale and requirements increase.

---

## Scaling

| Area | Current Design | Scaling Consideration | Possible Evolution |
|---|---|---|---|
| Application Services | Stateless Spring Boot services (Account, Metadata, Upload, Download) behind the API Gateway | No in-memory session state, so any running instance can serve any request | Run additional replicas per service in Kubernetes |
| PostgreSQL | Each service owns a dedicated database (`account_db`, `metadata_db`, `upload_db`) | Application tier scales out easily; database write throughput is the eventual limit | Read replicas, partitioning, or a more capable managed database |
| Redis | One shared deployment for caching, distributed locks, and gateway rate limiting | Single shared node across several concerns at high volume | Redis HA / clustering |
| Kafka | Lifecycle events consumed via Kafka consumer groups and partitions | Consumer throughput bound by partition count and broker capacity | Add partitions, size brokers/consumer groups to workload |
| MinIO | Dedicated object storage for file bytes, separate from PostgreSQL | Already scales independently of application data | Distributed / managed object storage |
| Upload | Multipart upload — parts stream through Upload Service into MinIO, each part independently retryable | Upload bandwidth passes through the application tier | Presigned multipart upload URLs (browser writes directly to MinIO) |
| Download | Download Service streams file bytes from MinIO to the client | Long-lived downloads hold app/network resources for their duration | Presigned GET URLs, optionally behind a CDN |

---

## Failure Handling

| Pattern | What We Do | Why It Helps |
|---|---|---|
| Idempotency | Upload initiation requires an `Idempotency-Key`; unique constraint on (user, operation, key) | A retried request (e.g. lost response) can't create a duplicate upload session |
| Distributed Lock | Redis lock around upload completion (atomic acquire, safe scripted release) | Only one instance completes a given upload, even with concurrent requests or replicas |
| Outbox | Lifecycle events written to an outbox table in the same DB transaction as the change, then published to Kafka by a poller | Database change and event publish can't fall out of sync |
| Retry + DLT | Kafka consumers retry transient failures with fixed backoff (2s / 10s / 30s) before routing to a dead-letter topic | One failing message can't block the queue; failures are kept for investigation/replay |
| Async Processing | Downstream work (e.g. MinIO cleanup after permanent delete) runs via a Kafka consumer, not inline in the request | User isn't blocked waiting on non-critical follow-up work |

---

## Consistency

| Area | Current Approach | Trade-off |
|---|---|---|
| Cache-Aside | Redis caches file/folder metadata; PostgreSQL is source of truth; cache miss reads DB and repopulates | Brief staleness possible on a missed eviction — acceptable for listings |
| Eventual Consistency | Some effects (metadata materialization, storage usage) happen asynchronously via Kafka | Short delay between action and downstream effect, traded for decoupling and reliability |
| Storage Quota | Checked synchronously before upload starts; usage updated asynchronously from lifecycle events | Fast and decoupled, but concurrent uploads have a small race window before usage is recorded |
| Database Ownership | Each service owns its data; no cross-service database queries | Clear boundaries and independent evolution, at the cost of no cross-service joins |

---

## Overall Assessment

The current architecture is already horizontally scalable at the application-service layer and uses several production-oriented patterns: idempotency, distributed locking, transactional outbox, Kafka-based asynchronous processing, caching, distributed rate limiting, and retry/DLT handling.

The main scale boundaries are stateful infrastructure and high-bandwidth file transfer. These do not require rewriting the core service architecture. At larger scale, the main evolution would be scaling PostgreSQL/Redis/Kafka/MinIO and moving large file transfers directly between clients and object storage using presigned URLs.

The current design intentionally favors a working, reliable vertical slice while leaving clear evolution paths for higher scale.
