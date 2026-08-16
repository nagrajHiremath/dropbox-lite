# Design Trade-offs

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

| Pattern | Current Approach |
|---|---|
| Idempotency | Upload Flow — optional `Idempotency-Key` on initiate; unique constraint on (user, operation, key) |
| Distributed Lock | Upload Flow (completion) and File Versioning (materialization) — atomic acquire, safe scripted release |
| Outbox | File Lifecycle, File Sharing, Upload Flow — DB write and outbox insert in one transaction, published by a poller (capped at 5 attempts, then marked `FAILED`) |
| Retry + DLT | File Lifecycle (MinIO cleanup) and File Versioning (materialization safety-net) consumers — fixed backoff 2s / 10s / 30s, then dead-letter topic |
| Async Processing | File Lifecycle — MinIO cleanup after permanent delete runs via a Kafka consumer, not inline in the request |

---

## Consistency

| Area | Current Approach | Trade-off |
|---|---|---|
| Cache-Aside | Redis caches specific reads (file lookup, folder listing, public share resolution); PostgreSQL is source of truth; cache miss reads DB and repopulates | Some write paths don't evict by design (e.g. version restore) — acceptable for how those are used today |
| Eventual Consistency | Metadata materialization happens synchronously, with an async Kafka consumer as a safety net; storage usage is Kafka-only, no synchronous path | Short delay only if the synchronous materialize call fails; storage usage always lags slightly |
| Storage Quota | Checked synchronously before upload starts; usage updated asynchronously from lifecycle events | Fast and decoupled, but concurrent uploads have a small race window before usage is recorded |
| Database Ownership | Each service owns its data; no cross-service database queries | Clear boundaries and independent evolution, at the cost of no cross-service joins |
