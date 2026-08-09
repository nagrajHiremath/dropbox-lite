# Dropbox-Lite — Implementation Plan

**Target:** Stable MVP by end of Day 4  
**Days 5–7:** Buffer for deployment, UI polish, reliability, documentation and demo.

**Architecture authority:** `docs/TECHNICAL_DESIGN.md`

---

# 1. Execution Strategy

Do NOT tell Claude Code:

```text
Build Dropbox-Lite.
```

Implementation must happen through small bounded tasks.

Flow:

```text
Design
  |
Task
  |
Claude Code
  |
Tests
  |
Manual verification
  |
Commit
  |
Next task
```

The primary goal is:

> Keep the system working throughout implementation.

Do not build five services independently and try integrating everything at the end.

---

# 2. Claude Code Working Contract

Before every task Claude Code must:

1. Read `docs/TECHNICAL_DESIGN.md`.
2. Read the relevant section of `docs/IMPLEMENTATION_PLAN.md`.
3. Inspect existing code before editing.
4. Implement only the requested task.
5. Preserve useful existing code.
6. Follow existing conventions where compatible with the design.
7. Add/update tests.
8. Run affected tests/build.
9. Report changed files.
10. Report tests and results.
11. Report assumptions.
12. Report unresolved issues.
13. Stop after the requested task.

Claude Code must NOT silently:

- Add another service
- Add another database
- Add infrastructure
- Change API contracts
- Change DB ownership
- Rename Kafka topics
- Redesign the architecture

---

# 3. Standard Claude Prompt Prefix

Use this before individual tasks:

```text
Read docs/TECHNICAL_DESIGN.md and docs/IMPLEMENTATION_PLAN.md first.

docs/TECHNICAL_DESIGN.md is the architectural source of truth.

Inspect existing code before editing.

Implement ONLY the requested task.

Do not redesign:
- service boundaries
- API contracts
- database ownership
- schemas outside this task
- Kafka topics
- infrastructure

Reuse existing conventions and dependencies where appropriate.

Add/update tests.

Run affected tests/build before finishing.

At the end report:
1. changed files
2. tests/build commands executed
3. results
4. assumptions
5. unresolved issues

Do not begin the next task.
```

---

# 4. Day 1 Goal

By the end of Day 1:

```text
Register/Login
      |
Initiate Upload
      |
Upload Parts
      |
Complete
      |
File appears
      |
Download file
```

Do NOT worry about advanced Kafka sophistication before this works.

---

# 5. FND-01 — Inspect Repository + Service Foundation ✅

Required services:

```text
Discovery Service (Eureka)
API Gateway
Account Service
Metadata Service
Upload Service
Download Service
Worker
```

## Acceptance Criteria

- Existing useful services/code preserved
- Missing modules/services added only if necessary
- Services independently runnable
- Environment-driven configuration
- Spring Boot Actuator
- Health endpoint
- Complete project builds

## First Claude Code Prompt

Use this as the **first prompt in Claude Code**:

```text
Read docs/TECHNICAL_DESIGN.md and docs/IMPLEMENTATION_PLAN.md.

These files are the source of truth for this Dropbox-Lite project.

Do NOT modify files yet.

First inspect the entire repository and report:

1. Existing modules/services.
2. Java version.
3. Spring Boot version.
4. Build system.
5. Important existing dependencies.
6. Existing Docker/infrastructure configuration.
7. Existing authentication/security code.
8. Existing PostgreSQL/Redis/Kafka/MinIO code.
9. Code that can be reused.
10. Conflicts between the current repository and docs/TECHNICAL_DESIGN.md.
11. Smallest changes required for FND-01.
12. Any blocker that could prevent the Day 1 vertical slice.

Do not redesign the architecture.

Return a concise implementation proposal for FND-01 only.

Do not modify files yet.
```

Review Claude's proposal first.

Then:

```text
Proceed with FND-01 exactly within the approved scope.

Do not begin FND-02.
Do not implement business functionality.

Run the complete affected build/tests.

At the end report:
- changed files
- build/test commands
- results
- assumptions
- unresolved issues
```

---

# 6. FND-02 — Local Infrastructure ✅

Required:

```text
PostgreSQL
Redis
Kafka
MinIO
```

Prefer:

```text
Docker Compose
```

for local development unless the repository already has a good equivalent.

## Acceptance Criteria

- All infrastructure starts reliably
- PostgreSQL accessible
- Redis accessible
- Kafka accessible
- MinIO accessible
- Environment-driven credentials
- No secrets committed
- Health checks where practical
- Startup instructions documented

---

# 7. ACC-01 — Registration ✅

Implement:

```text
users
storage_quotas
```

Endpoint:

```text
POST /api/v1/auth/register
```

## Acceptance Criteria

- Email validation
- Email uniqueness
- Password hashing
- User created
- Default storage quota created
- Correct validation errors
- Tests

---

# 8. ACC-02 — Login + JWT ✅

Endpoint:

```text
POST /api/v1/auth/login
```

## Acceptance Criteria

- Correct credentials return JWT
- Incorrect credentials rejected
- User identity represented consistently
- JWT validation works
- Tests

---

# 9. GWT-01 — Gateway Authentication ✅

Implement routing/security.

## Acceptance Criteria

Public:

```text
/auth/**
/public/**
```

Private:

```text
everything else
```

Also:

- Authenticated identity propagated safely
- Correlation/request ID established
- Invalid JWT rejected

---

# 10. META-01 — Metadata Schema ✅

Create:

```text
folders
files
file_versions
share_links
activities
outbox_events
processed_events
idempotency_keys
```

Follow `docs/TECHNICAL_DESIGN.md`.

## Acceptance Criteria

- Correct PKs
- Correct FKs inside Metadata DB
- Correct indexes
- Correct unique constraints
- No cross-service FKs
- Migration works from empty DB

---

# 11. UPL-01 — Upload Schema ✅

Create:

```text
upload_sessions
upload_parts
outbox_events
idempotency_keys
```

## Acceptance Criteria

Upload state model exists.

```text
INITIATED
UPLOADING
COMPLETING
STORAGE_COMPLETED
COMPLETED
FAILED
ABORTED
EXPIRED
```

Constraint:

```text
UNIQUE(upload_session_id, part_number)
```

Indexes match Technical Design.

---

# 12. UPL-02 — Multipart Upload Initiation ✅

Endpoint:

```text
POST /api/v1/uploads
```

## Responsibilities

- Derive user from authenticated principal
- Validate filename
- Validate total size
- Validate folder where appropriate
- Select chunk size
- Calculate number of parts
- Generate upload ID
- Initialize MinIO multipart upload
- Persist durable upload session
- Support `Idempotency-Key`

## Acceptance Criteria

Response contains:

```text
uploadId
chunkSize
totalParts
status
```

Duplicate request using same idempotency key returns the same logical result.

MinIO failure must not leave a falsely completed session.

Tests:

- Successful initiation
- Invalid request
- Invalid folder
- Duplicate idempotency key
- MinIO failure

## Claude Prompt

```text
TASK UPL-02

Implement only:

POST /api/v1/uploads

according to docs/TECHNICAL_DESIGN.md.

Requirements:

- derive user from authenticated principal
- validate input
- select chunk size server-side
- calculate total parts
- initialize MinIO multipart upload
- persist durable upload session
- implement durable Idempotency-Key handling
- follow existing exception conventions

Do NOT implement:
- part upload
- upload status
- resume
- completion

Tests required:
- success
- invalid request
- duplicate idempotency key
- MinIO failure

after impl stop after UPL-02.
```

---

# 13. UPL-03 — Upload Part ✅

Endpoint:

```text
PUT /api/v1/uploads/{uploadId}/parts/{partNumber}
```

## Responsibilities

```text
validate upload
validate owner
validate upload state
validate part number
stream part to MinIO
receive ETag
persist upload part
```

## Acceptance Criteria

- Binary data streamed
- No whole-file buffering
- ETag persisted
- Size/checksum persisted where selected
- Retrying same part is safe
- Invalid upload rejected
- Invalid state rejected
- Invalid part rejected

No unit Tests required.

---

# 14. UPL-04 — Upload Status / Resume ✅

Endpoint:

```text
GET /api/v1/uploads/{uploadId}
```

Response should provide enough information for client resume.

Example:

```json
{
  "uploadId": "uuid",
  "status": "UPLOADING",
  "totalParts": 10,
  "uploadedParts": [1, 2, 3]
}
```

## Acceptance Criteria

- Owner validation
- Durable state
- Uploaded parts returned
- Client can determine missing parts
- Works without Redis

---

# 15. UPL-05 — Complete Multipart Upload ✅

Endpoint:

```text
POST /api/v1/uploads/{uploadId}/complete
```

## Responsibilities

```text
validate owner
validate state
verify expected parts
transition COMPLETING
complete MinIO multipart
transition STORAGE_COMPLETED
persist final state
```

Initially connect enough metadata behavior to prove the vertical slice.

Full Outbox/Kafka hardening comes on Day 4.

## Acceptance Criteria

- All parts verified
- MinIO multipart completed
- Repeated completion safe
- Duplicate logical file/version not created
- MinIO-success/DB-failure can be recovered using `STORAGE_COMPLETED`

---

# 16. META-02 — Minimal File Listing ✅

Implement enough Metadata functionality for:

```text
GET /api/v1/files
GET /api/v1/files/{fileId}
```

## Acceptance Criteria

- Uploaded file becomes visible
- Owner-only access
- Basic pagination
- Metadata only
- No file bytes through Metadata Service

---

# 17. DNL-01 — Basic Download ✅

Endpoint:

```text
GET /api/v1/files/{fileId}/content
```

## Acceptance Criteria

- Owner authorization
- Current version resolved
- MinIO object streamed
- Correct content headers
- Complete file NOT loaded into memory

---

# 18. DAY 1 CHECKPOINT ✅

Stop adding features.

Test:

```text
Register
  |
Login
  |
Initiate upload
  |
Upload all parts
  |
Complete
  |
List files
  |
Download
  |
Checksum(original) == Checksum(download)
```

If this does not work reliably, fix it before Day 2.

---

# 19. Day 2 Goal

Make large-file transfer genuinely reliable.

Required:

```text
Resumable Upload
Resumable Download
Completion Lock
Abort
Cleanup
Failure Testing
```

---

# 20. UPL-06 — Distributed Completion Lock ✅

Lock:

```text
lock:upload:complete:{uploadId}
```

## Acceptance Criteria

Two Upload Service instances cannot simultaneously finalize the same upload.

Correctness layers:

```text
Redis lock
+
upload state machine
+
idempotency
+
DB constraints
```

If Redis lock infrastructure is unavailable:

```text
fail safely
```

rather than blindly finalize.

---

# 21. UPL-07 — Abort Upload ✅

Endpoint:

```text
DELETE /api/v1/uploads/{uploadId}
```

## Acceptance Criteria

- Owner validation
- MinIO multipart aborted
- Session becomes `ABORTED`
- Repeated abort is safe

---

# 22. UPL-08 — Expired Upload Cleanup ✅

Worker identifies:

```text
expires_at < now
AND
status unfinished
```

Then:

```text
abort MinIO multipart
mark EXPIRED
```

## Acceptance Criteria

- Safe when multiple workers exist
- Already-completed uploads untouched
- Failure logged/retried appropriately

---

# 23. DNL-02 — HTTP Range ✅

Extend download to support:

```http
Range: bytes=start-end
```

## Acceptance Criteria

Return:

```text
206 Partial Content
Content-Range
Accept-Ranges: bytes
Content-Length
```

Only requested bytes are read from MinIO.

Invalid ranges return an appropriate error.

---

# 24. REL-01 — Resumable Upload Test ✅

Test:

```text
1. initiate large upload
2. upload parts 1-4
3. terminate client
4. reconnect
5. GET upload status
6. receive [1,2,3,4]
7. upload remaining parts
8. complete
9. download
10. compare checksum
```

Must pass.

---

# 25. REL-02 — Resumable Download Test ✅

Test:

```text
1. begin large download
2. download initial range
3. interrupt
4. request remaining range
5. reconstruct file
6. compare checksum
```

Must pass.

---

# 26. DAY 2 CHECKPOINT

The following must now be stable:

- Multipart upload
- Duplicate part retry
- Interrupted upload
- Resumed upload
- Idempotent completion
- Concurrent completion protection
- HTTP Range
- Interrupted download
- Resumed download

This is the project's most important technical milestone.

---

# 27. Day 3 Goal

Finish the Dropbox product functionality.

---

# 28. META-03 — Folder APIs ✅

Implement:

```text
POST /folders

GET /folders/{id}

GET /folders/{id}/children

PATCH /folders/{id}

DELETE /folders/{id}

POST /folders/{id}/restore
```

## Acceptance Criteria

- Ownership
- Nested folders
- Rename
- Trash
- Restore
- Name collisions handled
- Pagination where necessary

---

# 29. META-04 — Dropbox Views ✅

Implement:

```text
My Files
Recent
Photos
Videos
Trash
```

Using:

```text
GET /files?...
```

## Acceptance Criteria

- Paginated
- Indexed queries
- User isolation
- No obvious N+1 behavior

---

# 30. META-05 — Rename / Move ✅

Implement:

```text
PATCH /files/{fileId}

POST /files/{fileId}/move
```

## Acceptance Criteria

- Ownership validation
- Destination validation
- Name collision handling
- Retry-safe behavior where needed

---

# 31. META-06 — Trash / Restore ✅

Normal delete:

```text
DELETE /files/{fileId}
```

must be soft delete.

Restore:

```text
POST /files/{fileId}/restore
```

## Acceptance Criteria

- MinIO bytes remain
- File disappears from normal view
- File appears in Trash
- Restore requires no re-upload

---

# 32. VER-01 — New Version Upload ✅

Endpoint:

```text
POST /files/{fileId}/uploads
```

Reuse multipart upload machinery.

Set:

```text
upload_type = NEW_VERSION
```

## Acceptance Criteria

- File ownership validated
- No second logical file created
- Same upload/resume capabilities

---

# 33. VER-02 — Version Finalization ✅

On completion:

```text
existing logical file
      |
new immutable file_versions row
      |
update current_version_id
```

## Acceptance Criteria

- Unique version number
- Immutable old versions
- Current version updated
- Concurrent version creation protected by:
    - distributed lock
    - DB unique constraint

Both layers are now in place: the DB unique constraint (`file_versions(file_id, version_number)`), plus a Redis lock (`lock:file:version:{fileId}`, metadata-service's own RedisLockService, same pattern as UPL-06) wrapping `FileMaterializationService.materializeVersion`.

---

# 34. VER-03 — Version APIs ✅

Implement:

```text
GET /files/{fileId}/versions

GET /files/{fileId}/versions/{versionId}/content

POST /files/{fileId}/versions/{versionId}/restore
```

## Acceptance Criteria

If:

```text
v1
v2
v3 current
```

restore `v1` creates:

```text
v4 current
```

Old versions remain unchanged.

Old-version download supports HTTP Range.

---

# 35. SHR-01 — Share Management ✅

Implement:

```text
POST /files/{fileId}/shares

GET /files/{fileId}/shares

DELETE /shares/{shareId}
```

## Acceptance Criteria

- High-entropy token
- Only token hash persisted
- Optional expiry
- Owner validation
- Revocation
- Retry-safe creation

---

# 36. SHR-02 — Public Share ✅

Implement:

```text
GET /public/shares/{token}

GET /public/shares/{token}/content
```

## Acceptance Criteria

- No JWT
- Hash token before lookup
- ACTIVE status
- Expiration checked
- Safe metadata only
- Download supports Range

---

# 37. DEL-01 — Permanent Delete ✅

Endpoint:

```text
DELETE /files/{fileId}/permanent
```

Do NOT synchronously delete all MinIO objects inside the HTTP request.

## Acceptance Criteria

- Metadata enters deletion lifecycle
- All version object keys are discoverable
- Async cleanup can be triggered
- User operation remains bounded

**Pending:** this task's own scope is complete - metadata lifecycle transition, discoverable version object keys (queryable by file_id via file_versions), bounded operation (status flip only, no synchronous MinIO calls). Actually executing async physical MinIO cleanup depends on WRK-02 (Permanent Storage Cleanup) plus the OBX-02/EVT-01 Kafka/Outbox pipeline it consumes from, none of which are implemented yet.

---

# 38. DAY 3 CHECKPOINT

Dropbox-Lite is now functionally complete.

Verify:

```text
folders
navigation
views
upload
resume upload
download
resume download
rename
move
trash
restore
versioning
share
revoke
```

---

# 39. Day 4 Goal

Add distributed-system maturity without breaking the product.

---

# 40. EVT-01 — Event Contracts ✅

Implement the event envelope:

```text
eventId
eventType
eventVersion
occurredAt
aggregateId
userId
data
```

Do not create a giant shared business-domain library.

Share only stable event-contract code if useful.

`EventEnvelope<T>` implemented as a plain record, duplicated per service (upload-service producer, metadata-service consumer) - no shared library, matching this project's existing convention for every other cross-service DTO.

---

# 41. OBX-01 — Upload Outbox ✅

Upload completion should become:

```text
Upload DB transaction
      |
update upload
      |
insert Outbox
      |
COMMIT
```

Publisher:

```text
PENDING
  |
Kafka
  |
PUBLISHED
```

Publish:

```text
UPLOAD_COMPLETED
```

to:

```text
storage.lifecycle.v1
```

## Acceptance Criteria

- DB + Outbox atomic
- Kafka unavailable does not lose event
- Publisher retries
- Duplicate publication tolerated

**Decision:** implemented as a dual-write, not a full cutover - `UploadCompletionService`'s existing synchronous HTTP materialization call to metadata-service is unchanged (still returns `fileId`/`versionId` immediately), and the same completion step now *also* durably enqueues this outbox event as a safety net for EVT-02's async consumer. See EVT-02's note below for why.

---

# 42. EVT-02 — Metadata Upload Consumer ✅

Consume:

```text
UPLOAD_COMPLETED
```

## Transaction

```text
BEGIN

check processed_events

create file/version

insert processed_events

insert Metadata Outbox

COMMIT
```

## Acceptance Criteria

Duplicate event:

```text
NO duplicate file
NO duplicate version
```

Consumer crash/redelivery is safe.

**Notes:**
- Dual-write, not full cutover (see OBX-01): the synchronous HTTP materialization path is unchanged; this consumer calls the exact same `FileMaterializationService.materialize()`/`materializeVersion()` methods as a redundant safety net, normally a harmless no-op replay via `sourceUploadId` idempotency.
- Deliberately split into two transactions rather than the diagram's one: `materialize()`/`materializeVersion()` run first unchanged (each already correctly self-contained via `FileVersionMaterializer`'s own `@Transactional` boundary, so a losing-race `DataIntegrityViolationException` only poisons that isolated transaction, not a broader one - the same Postgres "transaction aborted" trap `UploadCompletionService`/`UploadInitiationService` are already deliberately structured to avoid). A second small `@Transactional` step (`UploadEventBookkeepingWriter`) then inserts `processed_events` + this service's own `outbox_events` row. Still fully safe under redelivery: a crash between the two steps just makes `materialize()` replay idempotently on redelivery.
- `outbox_events`/`processed_events` entities didn't exist in metadata-service before this task despite META-01 listing them - created now (`OutboxEvent`/`OutboxEventStatus` mirror upload-service's exactly; `ProcessedEvent` uses a true JPA `@IdClass` composite PK matching the documented schema literally).
- The `outbox_events` rows this consumer writes (`FILE_CREATED`/`FILE_VERSION_CREATED`) are not yet published to `file.lifecycle.v1` - that's OBX-02, not implemented here. Custom retry/backoff/DLT beyond Spring Kafka's built-in defaults is EVT-03, also not implemented here.
- Verified: both services boot cleanly against real Postgres/Redis/Kafka/MinIO (docker-compose) with no bean-wiring errors - metadata-service's consumer subscribes and gets partitions assigned on `storage.lifecycle.v1`.

---

# 43. OBX-02 — Metadata Outbox ✅

Publish appropriate events:

```text
FILE_CREATED
FILE_VERSION_CREATED
FILE_TRASHED
FILE_RESTORED
FILE_PERMANENTLY_DELETED
FILE_SHARED
SHARE_REVOKED
```

`MetadataOutboxPublisher` mirrors upload-service's `OutboxPublisher` (OBX-01) exactly, publishing to `file.lifecycle.v1` keyed by `fileId`. `FILE_CREATED`/`FILE_VERSION_CREATED` were already enqueued by EVT-02; `FILE_TRASHED`/`FILE_RESTORED`/`FILE_PERMANENTLY_DELETED` (`FileService`) and `FILE_SHARED`/`SHARE_REVOKED` (`ShareService`) are newly wired via a shared `OutboxEventWriter`, called only on the actual-mutation path in each method (not the existing idempotent no-op-return paths). Since those methods are already fully `@Transactional` (unlike upload-service's per-step-checkpointed completion flow), the outbox write just joins the caller's ambient transaction - no `REQUIRES_NEW` bean needed here.

---

# 44. EVT-03 — Retry + DLT ✅

Implement bounded retry.

Example:

```text
2 sec
10 sec
30 sec
```

After retries:

```text
DLT
```

## Acceptance Criteria

- Transient error retries
- Permanent failure reaches DLT
- Original event identifiable
- Error reason visible
- One controlled demo failure works

Wired onto `UploadCompletedEventConsumer`'s container (`KafkaConsumerConfig`) via a `DefaultErrorHandler` using a small custom `FixedSequenceBackOff` (exactly 2s/10s/30s, not Spring's multiplicative `ExponentialBackOff`) plus `DeadLetterPublishingRecoverer`, whose default behavior already publishes to `<original-topic>.DLT` (`storage.lifecycle.v1.DLT`, matching the documented topic name) with original-topic/partition/offset and exception-message/stacktrace headers, and republishes the untouched original record - "identifiable"/"error reason visible" come from Spring Kafka's built-in behavior, not custom code. Manual demo recipe: publish (or let a real upload produce) an `UPLOAD_COMPLETED` event whose `data.folderId` points at a non-existent folder - `FileMaterializationService.materialize()` throws `ResourceNotFoundException`, which exercises the retry→DLT path end to end (~42s until the message lands on `storage.lifecycle.v1.DLT`).

---

# 45. WRK-01 — Activity Consumer ✅

Consume useful lifecycle/activity events.

Persist:

```text
activities
```

## Acceptance Criteria

- Idempotent
- Async
- Activity failure does not fail primary file operation

**Service placement note:** TECHNICAL_DESIGN.md §6.6 lists "Activity processing" under Async Worker, but `activities` is a metadata_db table (§14, owned by metadata-service per META-01) and §8 forbids cross-service table access - `async-worker` has no database at all, by design. Implemented instead as a second `@KafkaListener` inside metadata-service (`ActivityConsumer`, consumer group `metadata-service-activity`, alongside EVT-02/EVT-03's existing listener), consuming `file.lifecycle.v1`. Reuses the existing `processed_events` table for idempotency (a distinct `consumer_name`) and the existing `kafkaListenerContainerFactory` bean for retry/DLT, so no new infrastructure was needed - same principle already used for UPL-08's "Expired upload cleanup" placement.

---

# 46. WRK-02 — Permanent Storage Cleanup ✅

Consume permanent deletion workflow.

Delete:

```text
all MinIO objects belonging to file versions
```

## Acceptance Criteria

- Transient MinIO error retries
- Permanent failure reaches DLT
- Cleanup status observable
- Duplicate delete request safe

Implemented in `async-worker` (`PermanentDeletionConsumer`, matching §6.6 literally this time) - unlike WRK-01, this needs no service's database: just MinIO access (added `MinioConfig`/the `minio` dependency, mirroring upload-service's) plus one new internal read-only call to metadata-service (`GET /api/v1/internal/files/{fileId}/versions`, returning all version object keys regardless of file status, since by the time this runs the file is already DELETED) for the object keys to delete. Retry/DLT wiring mirrors EVT-03 exactly (own `FixedSequenceBackOff` + `DeadLetterPublishingRecoverer`, landing on `file.lifecycle.v1.DLT`). "Duplicate delete safe" comes free from MinIO's own `removeObjects` idempotency (same property `UploadTempPartsCleaner` already relies on); "cleanup status observable" is structured logging + the DLT topic itself - no new status table, keeping `async-worker` deliberately stateless.

---

# 47. RDS-01 — Metadata Cache ✅

Prioritize:

```text
file metadata
folder children
```

Then if time permits:

```text
versions
storage usage
```

## Acceptance Criteria

```text
cache hit -> Redis

cache miss -> PostgreSQL -> Redis
```

Mutations invalidate affected cache.

Redis outage falls back to DB.

Implemented via a new explicit `RedisCacheService` (not Spring's `@Cacheable` - its default error handling would propagate a cache-store exception and fail the request, which conflicts with "Redis outage falls back to DB"; this wrapper swallows Redis failures and logs instead, same style as the existing `RedisLockService`). Caches `file:meta:{fileId}` (`FileService.getFile`, evicted on rename/move/trash/restore/permanent-delete) and `folder:children:{userId}:{parentId}:{page}:{size}` (`FolderService.listChildren`, pattern-evicted on create/rename-move/trash/restore). "folder children" was scoped to `FolderService.listChildren` (sub-folder listings, matching the doc's own example key) rather than `FileService.listFiles`, which has too many filter dimensions to cache cleanly. **Security note:** `file:meta:{fileId}` has no `userId` in the key (matching the doc's example), so a cache hit re-verifies `ownerId` against the cached entity before returning it - otherwise a hit could leak one owner's file metadata to a different caller requesting the same fileId. Versions/storage-usage caching deferred per the doc's own "if time permits" prioritization.

---

# 48. RDS-02 — Share Cache ✅

Cache:

```text
share:{tokenHash}
```

## Acceptance Criteria

- Public lookup can hit Redis
- Revoke immediately invalidates
- Expiry/status still checked
- DB remains authoritative

Caches the `ShareLink` at `share:{tokenHash}` inside `ShareService.resolveActiveShare` (the shared helper both `resolvePublicShare` and `resolvePublicShareContent` already use). The ACTIVE-status and expiry checks run against whatever came back regardless of cache hit/miss, so a cache hit never skips them. `revokeShare` evicts the key immediately using the already-loaded `share.getTokenHash()`. Verified live end-to-end against real Postgres/Redis: cache populated on first public lookup, evicted immediately on revoke, and the next public lookup correctly 404s (hits DB fresh, sees REVOKED).

---

# 49. RDS-03 — Distributed Rate Limiting ✅

Priority:

1. Upload
2. Download
3. Public share
4. Authentication
5. Metadata

## Acceptance Criteria

- Limit shared across service replicas
- Redis-backed atomic implementation
- Returns `429`
- `Retry-After` where practical

**Implemented centrally at `api-gateway`**, not as a hand-rolled limiter duplicated across upload/download/metadata/account-service. Every one of the 5 priority targets is already a gateway route, and `api-gateway` already carried `spring-boot-starter-data-redis-reactive` as an unused dependency - a strong signal this was meant to use Spring Cloud Gateway's own built-in `RequestRateLimiter` filter + `RedisRateLimiter` (token bucket, atomic Lua script bundled with Gateway itself), which is exactly the "proven Redis-backed implementation" the doc allows as an alternative to a custom one. All 7 routes got a `RequestRateLimiter` filter added in `application.yaml` (predicates/uri/ordering untouched), keyed by a new `userKeyResolver` (reads the already-gateway-set `X-User-Id` header - upload/download/metadata) or `ipKeyResolver` (remote address - public share/auth, both pre-authentication). A new `RetryAfterHeaderFilter` global filter adds `Retry-After: 1` on `429`s via `response.beforeCommit(...)` - Gateway's bundled filter already sends `X-RateLimit-*` headers but not `Retry-After`.

**Verified live end-to-end**: booted discovery-service + account-service + api-gateway against real Redis, burst-tested the auth route (`replenishRate=3`, `burstCapacity=5`) with 5 rapid requests succeeding and the 6th correctly rejected with `429`, `Retry-After: 1`, and full `X-RateLimit-*` headers; confirmed the underlying `request_rate_limiter.{routeId.key}.*` keys in Redis. Two real bugs were caught and fixed during this live testing (not by code review alone): a `NoUniqueBeanDefinitionException` from having two `KeyResolver` beans with no `@Primary` (Gateway's filter factory needs an unambiguous default even though every route here sets `key-resolver` explicitly), and a filter-ordering bug where the original `Retry-After` implementation (`chain.filter(exchange).then(...)`) never ran on a rejected request because `RequestRateLimiter` short-circuits without calling `chain.filter()` further - fixed by switching to `response.beforeCommit(...)`, which is ordering-independent.

---

# 50. OBS-01 — Observability

Implement:

- Correlation ID
- Structured logs
- Upload ID
- File ID
- Event ID
- Health
- Readiness
- Kafka retry logging
- DLT logging

Do not spend large amounts of MVP time building a full observability platform.

---

# 51. REL-03 — Failure Matrix

Intentionally test:

```text
duplicate upload initiation

duplicate part

duplicate upload completion

two concurrent completion requests

duplicate Kafka event

Kafka consumer exception

Kafka retry

DLT

Redis cache unavailable

Redis lock unavailable

MinIO part failure

interrupted upload

interrupted download

concurrent version creation

expired share

revoked share

permanent MinIO deletion failure
```

Document results.

This is useful both technically and for the hackathon demo.

---

# 52. DAY 4 — FEATURE FREEZE

At the end of Day 4, the following must work:

- Authentication
- Files/folders
- Multipart upload
- Resumable upload
- HTTP Range download
- Resumable download
- Versioning
- Sharing
- Trash/restore
- Idempotency
- Outbox
- Kafka
- Idempotent consumers
- Retry/DLT
- Redis cache
- Distributed lock
- Distributed rate limiting
- Swagger/OpenAPI
- Main tests

After this:

> NO NEW CORE BACKEND FEATURES.

---

# 53. Day 5 — UI + Deployment

Focus:

```text
React
Docker
Kubernetes
GKE
```

UI:

- Login/Register
- My Files
- Folder navigation
- Photos
- Videos
- Recent
- Trash
- Upload
- Upload progress
- Resume
- File actions
- Versions
- Share

Deployment:

- Docker images
- Kubernetes Deployments
- Services
- ConfigMaps
- Secrets
- Probes
- Ingress
- GKE

Demonstrate Upload and Download as independently scalable deployments.

---

# 54. Day 6 — Stabilization

Do:

- End-to-end regression
- Deployment debugging
- CORS fixes
- Authentication fixes
- Network fixes
- Light load tests
- Large-file memory verification
- Query/index review
- Redis invalidation verification
- Kafka retry verification
- DLT verification
- Error-response cleanup
- Security review

Do not add random new features.

---

# 55. Day 7 — Submission

Prepare:

- README
- Architecture diagram
- ER diagram
- Swagger
- Screenshots
- Deployment link
- Design decisions
- Distributed-system explanation
- Demo video
- Final regression

---

# 56. Priority Cut Line

If schedule slips, cut in this order:

```text
1. AI features
2. Activity UI
3. Search
4. Storage dashboard polish
5. Broad caching
6. Extra activity events
```

Keep at least one meaningful Redis cache.

Do NOT cut:

```text
Multipart upload
Resumable upload
HTTP Range
Resumable download
Versioning
Sharing
Idempotency
Outbox
Kafka
Consumer idempotency
Retry/DLT
Redis cache
Distributed rate limiting
Distributed locking
```

---