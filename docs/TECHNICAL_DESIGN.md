# Dropbox-Lite — Technical Design

**Status:** Locked for MVP implementation  
**Purpose:** Architectural source of truth for Claude Code and contributors.

> Implementation agents must read this document before making architectural or cross-service changes.  
> Do not redesign service boundaries, APIs, schemas, event contracts, or infrastructure unless a genuine blocker is identified.

---

# 1. Product Goal

Build a mature Dropbox-like cloud storage MVP focused on:

- Reliable large-file storage
- Multipart/chunked upload
- Resumable upload and download
- File/folder management
- File versioning
- Secure sharing
- Horizontal scalability
- Distributed-system reliability patterns

The goal is not merely to reproduce Dropbox UI.

The project should demonstrate a realistic backend architecture using:

- java : v 21
- Spring Boot : v 4.1.0
- PostgreSQL : Latest
- MinIO : Latest
- Redis : Latest
- Kafka : Latest
- Transactional Outbox
- Idempotency
- Distributed locking
- Distributed rate limiting
- Retry/DLT
- Kubernetes/GKE

---

# 2. MVP Scope

## 2.1 P0 Features

### Authentication

- Register
- Login
- JWT authentication
- Current user/profile
- Storage quota/usage

### File Management

- My Files
- Folder navigation
- Create folder
- Rename file/folder
- Move file
- Trash
- Restore
- Permanent delete

### Views

- Recent
- Photos
- Videos
- Trash

### Upload

- Multipart/chunked upload
- Upload session
- Upload individual parts
- Upload progress/status
- Interrupted upload
- Resume upload
- Abort upload
- Upload expiration/cleanup
- Idempotent initiation
- Idempotent completion

### Download

- Normal download
- HTTP Range
- `206 Partial Content`
- Resumable download
- Old-version download
- Public-share download

### Versioning

- Multiple versions per logical file
- Version history
- Download previous version
- Restore previous version
- Restoring creates a new version

### Sharing

- Public share link
- Expiring links
- Revoke link
- Public metadata
- Public download

---

# 3. Non-Goals

Do NOT implement these before the MVP is stable:

- Desktop synchronization client
- Mobile application
- Collaborative editing
- Google Docs-like editor
- Team/organization ACL system
- Video transcoding
- Antivirus pipeline
- Full-text search
- Semantic/vector search
- AI assistant
- Recommendation engine
- Complex analytics
- Artificial Saga orchestration

These can be future extensions.

---

# 4. Architecture Principles

1. Split services by **workload and scaling characteristics**, not by CRUD entity.

2. Metadata is the **control plane**.

3. Upload and Download are the **data plane**.

4. PostgreSQL is the durable source of truth for relational state.

5. MinIO is the source of truth for file bytes.

6. Redis is acceleration and coordination infrastructure, never the sole durable source of truth.

7. Kafka uses at-least-once delivery semantics.

8. Kafka consumers must therefore be idempotent.

9. Reliable DB → Kafka publication uses the Transactional Outbox pattern.

10. Use local ACID transactions inside service boundaries.

11. Use eventual consistency across services.

12. Do not use distributed database transactions/2PC.

13. File bytes must never pass through Metadata Service.

14. Services must not directly query another service's database.

15. Upload and Download must be independently horizontally scalable.

---

# 5. High-Level Architecture

```text
                         React Client
                              |
                              v
                         API Gateway
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
     Account Service     Metadata Service     Transfer Plane
                                               /       \
                                              /         \
                                             v           v
                                      Upload Service  Download Service
                                             \           /
                                              \         /
                                                 MinIO


 Account DB              Metadata DB              Upload DB
 PostgreSQL              PostgreSQL               PostgreSQL

                             |
                           Redis
                 Cache / Rate Limit / Locks


 Service DB
     |
     v
 Transactional Outbox
     |
     v
   Kafka
     |
     v
 Consumers / Workers
     |
 Retry / DLT
```

## 5.1 Service Discovery

API Gateway, Account, Metadata, Upload, Download and Async Worker register
with and discover each other through a Eureka registry (`discovery-service`).

Eureka is platform infrastructure, not a business microservice, and is
intentionally omitted from the request-processing diagram above.

---

# 6. Service Responsibilities

## 6.1 API Gateway

Responsibilities:

- External API routing
- Authentication boundary where appropriate
- JWT validation
- Request/correlation ID propagation
- Common request concerns
- Coarse platform-level rate limiting if useful

The gateway must NOT contain file-management business logic.

---

## 6.2 Account Service

Responsibilities:

- User registration
- Login
- Password hashing
- JWT issuance
- User profile
- Account status
- Storage quota
- Storage usage

Owns:

```text
users
storage_quotas
```

---

## 6.3 Metadata Service

This is the logical filesystem/control plane.

Responsibilities:

- Files
- Folders
- File metadata
- File versions
- Current version
- Rename
- Move
- Recent
- Photos
- Videos
- Trash
- Restore
- Share links
- Activity metadata
- Metadata caching
- Metadata-domain Outbox

Metadata Service does NOT transfer large file bytes.

---

## 6.4 Upload Service

This is the write side of the file data plane.

Responsibilities:

- Upload session creation
- Multipart upload initialization
- Chunk/part upload
- Upload status
- Resume upload
- Abort upload
- Upload completion
- New-version upload
- MinIO multipart integration
- Upload idempotency
- Upload rate limiting
- Distributed completion lock
- Upload-domain Outbox

Upload Service must be independently scalable.

---

## 6.5 Download Service

This is the read side of the file data plane.

Responsibilities:

- Authorized downloads
- HTTP Range
- Partial-content responses
- Resumable download
- Version download
- Public share download
- Streaming from MinIO
- Download rate limiting

The service must stream data and avoid loading complete files into JVM memory.

---

## 6.6 Async Worker

One Worker deployment is enough for the MVP.

Responsibilities can include:

- Activity processing
- Storage usage updates
- Expired upload cleanup
- Permanent MinIO object deletion
- Retry/DLT-related processing

Future workers could perform:

- Thumbnail generation
- Antivirus scanning
- Document extraction
- Search indexing
- AI indexing

Do NOT split these into separate services during the MVP unless required.

---

## 6.7 Discovery Service (Eureka) — Platform Infrastructure

Responsibilities:

- Service registration
- Service discovery for gateway routing and inter-service calls

Not a business microservice: no domain data, no owned database, no
externally exposed API beyond the Eureka registry endpoints.

---

# 7. Scaling Model

| Workload | Characteristics | Service |
|---|---|---|
| Authentication | Small payload, security-sensitive | Account |
| Metadata/navigation | Many small JSON requests | Metadata |
| Upload | Network ingress, long-lived requests | Upload |
| Download | Network egress, streaming | Download |
| Background work | Async/bursty | Worker |

This separation allows:

```text
Upload Service
replicas: 8

Download Service
replicas: 15

Metadata Service
replicas: 3

Account Service
replicas: 2
```

without scaling the whole backend together.

---

# 8. Database Ownership

For the hackathon, multiple logical databases may run on the same PostgreSQL instance.

However:

> Service ownership boundaries remain strict.

No service should directly access another service's tables.

---

# 9. Account Database

## 9.1 users

```text
id              UUID PK
email           VARCHAR UNIQUE NOT NULL
password_hash   VARCHAR NOT NULL
display_name    VARCHAR
status          VARCHAR NOT NULL
created_at      TIMESTAMPTZ
updated_at      TIMESTAMPTZ
```

---

## 9.2 storage_quotas

```text
user_id         UUID PK
max_bytes       BIGINT NOT NULL
used_bytes      BIGINT NOT NULL
updated_at      TIMESTAMPTZ
```

`used_bytes` is intentionally denormalized for fast quota checks.

---

# 10. Metadata Database

## 10.1 folders

```text
id              UUID PK
owner_id        UUID NOT NULL
parent_id       UUID NULL FK -> folders.id
name            VARCHAR NOT NULL
status          VARCHAR NOT NULL
created_at      TIMESTAMPTZ
updated_at      TIMESTAMPTZ
deleted_at      TIMESTAMPTZ NULL
```

Indexes:

```text
(owner_id, parent_id)
(owner_id, status)
```

Active folder-name uniqueness per parent should be enforced using an appropriate unique/partial index.

---

# 11. files

Represents a **logical file**, not physical bytes.

```text
id                  UUID PK
owner_id            UUID NOT NULL
folder_id           UUID NULL FK -> folders.id
name                VARCHAR NOT NULL
mime_type           VARCHAR
current_version_id  UUID
status              VARCHAR NOT NULL
created_at          TIMESTAMPTZ
updated_at          TIMESTAMPTZ
deleted_at          TIMESTAMPTZ NULL
```

Indexes:

```text
(owner_id, folder_id, status)
(owner_id, mime_type, status)
(owner_id, updated_at DESC)
```

`current_version_id` is intentional denormalization to make normal file reads fast.

---

# 12. file_versions

File versions are immutable.

```text
id               UUID PK
file_id          UUID NOT NULL FK -> files.id
version_number   INTEGER NOT NULL
object_key       VARCHAR NOT NULL
size_bytes       BIGINT NOT NULL
checksum         VARCHAR
etag             VARCHAR
created_by       UUID NOT NULL
created_at       TIMESTAMPTZ
```

Constraints:

```text
UNIQUE(file_id, version_number)
```

Index:

```text
(file_id, version_number DESC)
```

Example:

```text
report.pdf

v1 -> MinIO object A
v2 -> MinIO object B
v3 -> MinIO object C
```

If the user restores `v1` while `v3` is current:

```text
v1
v2
v3
v4 <- restored content of v1
```

History is never rewritten.

---

# 13. share_links

```text
id              UUID PK
file_id         UUID NOT NULL FK -> files.id
token_hash      VARCHAR UNIQUE NOT NULL
permission      VARCHAR NOT NULL
status          VARCHAR NOT NULL
expires_at      TIMESTAMPTZ NULL
created_by      UUID NOT NULL
created_at      TIMESTAMPTZ
```

Indexes:

```text
token_hash
(file_id, status)
```

The raw public token must NOT be stored.

Generate a high-entropy random token and persist only its hash.

---

# 14. activities

```text
id              UUID PK
user_id         UUID NOT NULL
file_id         UUID NULL
action          VARCHAR NOT NULL
metadata        JSONB
created_at      TIMESTAMPTZ
```

Index:

```text
(user_id, created_at DESC)
```

Activity is secondary data and should not block primary file operations.

---

# 15. outbox_events

Each event-producing service owns an Outbox.

```text
id               UUID PK
aggregate_type   VARCHAR NOT NULL
aggregate_id     UUID NOT NULL
event_type       VARCHAR NOT NULL
payload          JSONB NOT NULL
status           VARCHAR NOT NULL
retry_count      INTEGER DEFAULT 0
created_at       TIMESTAMPTZ
published_at     TIMESTAMPTZ NULL
```

Index:

```text
(status, created_at)
```

---

# 16. processed_events

Used for Kafka consumer idempotency.

```text
event_id         UUID NOT NULL
consumer_name    VARCHAR NOT NULL
processed_at     TIMESTAMPTZ

PRIMARY KEY(event_id, consumer_name)
```

---

# 17. idempotency_keys

```text
id                UUID PK
user_id           UUID NOT NULL
idempotency_key   VARCHAR NOT NULL
operation         VARCHAR NOT NULL
request_hash      VARCHAR
resource_id       UUID
status            VARCHAR NOT NULL
response_status   INTEGER
response_body     JSONB
created_at        TIMESTAMPTZ
expires_at        TIMESTAMPTZ
```

Constraint:

```text
UNIQUE(user_id, operation, idempotency_key)
```

PostgreSQL provides durable idempotency.

Redis may later accelerate lookups.

---

# 18. Upload Database

## 18.1 upload_sessions

```text
id                 UUID PK
user_id            UUID NOT NULL
file_id            UUID NULL
folder_id          UUID NULL
upload_type        VARCHAR NOT NULL
file_name          VARCHAR NOT NULL
mime_type          VARCHAR
total_size         BIGINT NOT NULL
chunk_size         BIGINT NOT NULL
total_parts        INTEGER NOT NULL
object_key         VARCHAR NOT NULL
minio_upload_id    VARCHAR
status             VARCHAR NOT NULL
created_at         TIMESTAMPTZ
updated_at         TIMESTAMPTZ
expires_at         TIMESTAMPTZ
```

Upload types:

```text
NEW_FILE
NEW_VERSION
```

State model:

```text
INITIATED
    |
    v
UPLOADING
    |
    v
COMPLETING
    |
    v
STORAGE_COMPLETED
    |
    v
COMPLETED
```

Other states:

```text
FAILED
ABORTED
EXPIRED
```

Indexes:

```text
(user_id, status)
(expires_at, status)
file_id
```

---

# 19. upload_parts

```text
id                  UUID PK
upload_session_id   UUID NOT NULL
part_number         INTEGER NOT NULL
etag                VARCHAR
checksum            VARCHAR
size_bytes          BIGINT
status              VARCHAR
created_at          TIMESTAMPTZ
```

Constraint:

```text
UNIQUE(upload_session_id, part_number)
```

Index:

```text
upload_session_id
```

This uniqueness is important for retry safety.

---

# 20. Cross-Service Database Rule

Cross-service IDs are allowed.

Example:

```text
Upload Service

file_id = 123
user_id = 456
```

But:

```text
Upload Service -> SELECT FROM metadata.files
```

is NOT allowed.

There should be:

- API communication
- Kafka event communication

instead of cross-database queries.

---

# 21. MinIO Object Model

Actual file bytes live in MinIO.

Conceptual key:

```text
dropbox-files/{userId}/{fileId}/{versionId}
```

Example:

```text
dropbox-files/
    user-123/
        file-456/
            version-1
            version-2
            version-3
```

Every `file_versions` record references one immutable object key.

MinIO is responsible for bytes.

PostgreSQL is responsible for metadata.

---

# 22. API Design

External prefix:

```text
/api/v1
```

---

# 23. Authentication APIs

```text
POST /api/v1/auth/register
POST /api/v1/auth/login

GET  /api/v1/users/me
GET  /api/v1/users/me/storage
```

---

# 24. Upload APIs

## Initiate

```text
POST /api/v1/uploads
```

Requires:

```text
Idempotency-Key
```

Conceptual request:

```json
{
  "fileName": "movie.mp4",
  "folderId": "uuid",
  "size": 1073741824,
  "mimeType": "video/mp4"
}
```

Response:

```json
{
  "uploadId": "uuid",
  "chunkSize": 8388608,
  "totalParts": 128,
  "status": "INITIATED"
}
```

Chunk size is selected by the server.

---

## Upload Part

```text
PUT /api/v1/uploads/{uploadId}/parts/{partNumber}
```

Request body:

```text
binary
```

The service:

```text
validate
 -> stream to MinIO
 -> receive ETag
 -> persist upload_parts
```

Retrying the same part must be safe.

---

## Upload Status

```text
GET /api/v1/uploads/{uploadId}
```

Example:

```json
{
  "uploadId": "uuid",
  "status": "UPLOADING",
  "totalParts": 10,
  "uploadedParts": [1, 2, 3, 4]
}
```

Client resumes by uploading:

```text
5, 6, 7, 8, 9, 10
```

---

## Complete Upload

```text
POST /api/v1/uploads/{uploadId}/complete
```

Must be idempotent.

Completion may temporarily return:

```json
{
  "uploadId": "uuid",
  "status": "PROCESSING"
}
```

because Metadata Service may materialize the logical file asynchronously.

---

## Abort Upload

```text
DELETE /api/v1/uploads/{uploadId}
```

---

## Upload New Version

```text
POST /api/v1/files/{fileId}/uploads
```

This uses the same multipart machinery with:

```text
upload_type = NEW_VERSION
```

---

# 25. File APIs

```text
GET    /api/v1/files/{fileId}

GET    /api/v1/files?folderId={folderId}
GET    /api/v1/files?view=recent
GET    /api/v1/files?type=image
GET    /api/v1/files?type=video
GET    /api/v1/files?status=trashed

PATCH  /api/v1/files/{fileId}

POST   /api/v1/files/{fileId}/move

DELETE /api/v1/files/{fileId}

POST   /api/v1/files/{fileId}/restore

DELETE /api/v1/files/{fileId}/permanent
```

Normal `DELETE` is soft delete.

Permanent deletion triggers asynchronous physical storage cleanup.

All collection APIs must be paginated.

MVP can use:

```text
page
size
```

Cursor/keyset pagination can be a future optimization.

---

# 26. Folder APIs

```text
POST   /api/v1/folders

GET    /api/v1/folders/{id}

GET    /api/v1/folders/{id}/children

PATCH  /api/v1/folders/{id}

DELETE /api/v1/folders/{id}

POST   /api/v1/folders/{id}/restore
```

---

# 27. Download APIs

```text
GET /api/v1/files/{fileId}/content
```

Old version:

```text
GET /api/v1/files/{fileId}/versions/{versionId}/content
```

Public share:

```text
GET /api/v1/public/shares/{token}/content
```

---

# 28. Resumable Download

Do NOT invent a custom chunk-download API.

Use standard HTTP Range.

Client:

```http
Range: bytes=10485760-20971519
```

Server:

```http
HTTP/1.1 206 Partial Content

Accept-Ranges: bytes
Content-Range: bytes 10485760-20971519/1073741824
Content-Length: 10485760
```

This naturally enables resumable downloads.

---

# 29. Version APIs

```text
GET /api/v1/files/{fileId}/versions

GET /api/v1/files/{fileId}/versions/{versionId}/content

POST /api/v1/files/{fileId}/versions/{versionId}/restore
```

Restore always creates a new version.

---

# 30. Sharing APIs

```text
POST /api/v1/files/{fileId}/shares

GET /api/v1/files/{fileId}/shares

DELETE /api/v1/shares/{shareId}
```

Public:

```text
GET /api/v1/public/shares/{token}

GET /api/v1/public/shares/{token}/content
```

Public endpoints do not require JWT.

They must validate:

```text
token hash
status == ACTIVE
expires_at
```

---

# 31. HTTP Idempotency

Use:

```http
Idempotency-Key: <client-generated-key>
```

especially for:

- Upload initiation
- Upload completion
- Share creation
- Version restore
- Retry-prone mutations

Correctness must survive:

```text
client request
    |
server completes operation
    |
response lost
    |
client retries
```

The retry must not create duplicate domain state.

---

# 32. Kafka Topics

Use three main topics.

```text
storage.lifecycle.v1
file.lifecycle.v1
file.activity.v1
```

Important DLTs:

```text
storage.lifecycle.v1.DLT
file.lifecycle.v1.DLT
```

Avoid creating one Kafka topic per event.

---

# 33. Event Envelope

All events use a consistent envelope.

```json
{
  "eventId": "uuid",
  "eventType": "UPLOAD_COMPLETED",
  "eventVersion": 1,
  "occurredAt": "2026-08-06T12:30:00Z",
  "aggregateId": "uuid",
  "userId": "uuid",
  "data": {}
}
```

---

# 34. Storage Lifecycle Events

Topic:

```text
storage.lifecycle.v1
```

Events:

```text
UPLOAD_COMPLETED
UPLOAD_ABORTED
UPLOAD_EXPIRED

OBJECT_DELETE_REQUESTED
OBJECT_DELETED
OBJECT_DELETE_FAILED
```

---

# 35. File Lifecycle Events

Topic:

```text
file.lifecycle.v1
```

Events:

```text
FILE_CREATED
FILE_VERSION_CREATED
FILE_TRASHED
FILE_RESTORED
FILE_PERMANENTLY_DELETED
FILE_SHARED
SHARE_REVOKED
```

---

# 36. Activity Events

Topic:

```text
file.activity.v1
```

Possible events:

```text
FILE_DOWNLOADED
FILE_RENAMED
FILE_MOVED
VERSION_RESTORED
```

Activity events are secondary and should not block core user operations.

---

# 37. Kafka Partition Keys

For file lifecycle:

```text
key = fileId
```

For upload lifecycle before a file exists:

```text
key = uploadId
```

This preserves ordering for the same aggregate while allowing unrelated files/uploads to process concurrently.

---

# 38. Kafka Consumer Idempotency

Kafka is treated as:

```text
at-least-once
```

Therefore duplicate events are expected.

Critical consumer pattern:

```text
Receive event
      |
      v
Check processed_events
      |
      +---- exists ---> SKIP
      |
      v
BEGIN TRANSACTION
      |
perform business operation
      |
insert processed_events
      |
insert Outbox if necessary
      |
COMMIT
      |
ACK Kafka
```

The database transaction must include both:

```text
business state change
+
processed_events insert
```

---

# 39. Transactional Outbox

Do NOT do this:

```text
save database
     |
commit
     |
kafkaTemplate.send()
```

because the process can crash between the DB commit and Kafka send.

Use:

```text
BEGIN

business mutation

INSERT outbox_event

COMMIT
```

Then:

```text
Outbox Publisher
      |
query PENDING events
      |
publish Kafka
      |
mark PUBLISHED
```

---

# 40. Outbox Duplicate Scenario

Possible:

```text
publish Kafka succeeds
      |
application crashes
      |
outbox still PENDING
      |
publisher publishes again
```

This is acceptable.

Kafka consumers use `eventId` + `processed_events` to make duplicate delivery harmless.

---

# 41. Retry and DLT

Transient Kafka consumer failure:

```text
event
 |
consumer
 |
failure
 |
retry
 |
retry
 |
retry
 |
DLT
```

Example backoff:

```text
2 seconds
10 seconds
30 seconds
```

After the retry budget:

```text
*.DLT
```

Preserve enough original event and failure context for debugging/replay.

---

# 42. Redis — Cache Aside

Redis is used for high-value reads.

Example keys:

```text
file:meta:{fileId}

folder:children:{userId}:{folderId}:...

file:versions:{fileId}

share:{tokenHash}

user:storage:{userId}
```

Read flow:

```text
Client
  |
Redis
  |
 HIT ----------------> response
  |
 MISS
  |
PostgreSQL
  |
Redis SET
  |
response
```

PostgreSQL remains authoritative.

---

# 43. Cache Invalidation

Mutation:

```text
DB transaction commits
      |
      v
evict/update affected Redis keys
```

Example:

```text
rename file

UPDATE files
DELETE file:meta:{fileId}
DELETE relevant folder listing cache
```

Never make successful Redis invalidation part of the database correctness guarantee.

---

# 44. Distributed Rate Limiting

Redis-backed distributed rate limiting is natural because services have multiple replicas.

Keys:

```text
rl:upload:{userId}

rl:download:{userId}

rl:metadata:{userId}

rl:public-share:{identifier}
```

Use:

- Atomic Redis operations
- Token bucket
- Sliding window

or a proven Redis-backed implementation.

When exceeded:

```http
HTTP/1.1 429 Too Many Requests
```

Include `Retry-After` where practical.

---

# 45. Distributed Locks

Important locks:

```text
lock:upload:complete:{uploadId}

lock:file:version:{fileId}
```

Use cases:

### Upload completion

Two requests may hit different Upload pods:

```text
Pod A ----\
           -> same upload
Pod B ----/
```

Only one should execute MinIO completion.

### Version creation

Concurrent version uploads must not both select the same version number.

Redis coordinates execution.

Database constraints/state remain the final correctness layer.

---

# 46. Redis Failure Rule

For normal cache:

```text
Redis unavailable
     |
fall back to PostgreSQL
```

For critical distributed lock:

```text
Redis unavailable
     |
cannot safely coordinate
     |
fail temporarily
```

Do not blindly execute a critical concurrent operation when coordination cannot be guaranteed.

---

# 47. Core Upload Flow

```text
Client
  |
POST /uploads
  |
Upload Service
  |
Create durable upload session
  |
Initialize MinIO multipart upload
```

Parts:

```text
Client
  |
PUT part 1
PUT part 2
PUT part 3
  |
Upload Service
  |
MinIO
  |
persist part ETags/status
```

Interruption:

```text
Client crashes
     |
later
     |
GET /uploads/{id}
     |
uploaded parts = [1,2,3]
     |
continue [4,5,6...]
```

Completion:

```text
POST /uploads/{id}/complete
        |
Redis distributed lock
        |
validate upload state
        |
COMPLETING
        |
verify parts
        |
MinIO CompleteMultipartUpload
        |
STORAGE_COMPLETED
        |
DB transaction
        |
Outbox
        |
PROCESSING / completion response
```

Async:

```text
Outbox Publisher
      |
storage.lifecycle.v1
      |
UPLOAD_COMPLETED
      |
Metadata Consumer
      |
BEGIN
      |
create file/version
      |
insert processed_event
      |
insert Metadata Outbox
      |
COMMIT
      |
file.lifecycle.v1
```

---

# 48. Failure Model

| Failure | Required behavior |
|---|---|
| Same part retried | Safe |
| Upload initiation retried | Same logical result |
| Completion retried | No duplicate file/version |
| Two completion requests | Lock + state/idempotency |
| MinIO completes, DB update fails | Recover through `STORAGE_COMPLETED` |
| DB commits, Kafka unavailable | Outbox retries |
| Kafka sends duplicate | Consumer skips duplicate |
| Consumer crashes before commit | Rollback + redelivery |
| Consumer commits then crashes before ACK | Redelivery skipped |
| Concurrent version creation | Lock + DB unique constraint |
| Redis cache unavailable | PostgreSQL fallback |
| Redis lock unavailable | Safe temporary failure |
| MinIO part failure | Part can be retried |
| Upload interrupted | Resume missing parts |
| Download interrupted | Resume with Range |
| Upload abandoned | Expiration cleanup |
| Normal delete | Soft delete |
| Permanent delete | Async MinIO cleanup |
| MinIO deletion failure | Retry then DLT |
| Share revoked | DB update + cache eviction |
| Share expired | DB status/expiry validation |

---

# 49. Consistency Model

## Strong consistency

Inside a service's local PostgreSQL transaction.

Example:

```text
create file version
+
update current_version_id
+
insert processed_event
+
insert Outbox
```

can be one transaction.

## Eventual consistency

Across services.

Example:

```text
Upload completed
      |
Kafka
      |
Metadata file appears shortly afterward
```

The UI may briefly display:

```text
Processing...
```

This is acceptable.

---

# 50. Security

Required:

- Secure password hashing
- JWT
- Ownership authorization
- Never trust `ownerId` supplied by client
- Derive identity from authenticated principal
- High-entropy share tokens
- Store share-token hash only
- Validate share status and expiration
- File-size validation
- Storage quota validation
- Filename/MIME validation appropriate for MVP
- Rate limiting
- No MinIO administrative credentials in browser
- Secrets through environment variables/Kubernetes Secrets
- No secrets committed to Git

---

# 51. Observability

Minimum MVP:

- Correlation/request ID
- Structured logs
- Request ID
- Upload ID
- File ID
- Event ID
- Kafka consumer errors
- Retry logs
- DLT logs
- Upload state transitions
- Spring Boot Actuator
- Health endpoints
- Readiness/liveness

If time permits:

- Request latency
- Request count
- Upload failure count
- Kafka failure count
- DLT count

A full distributed tracing stack is NOT required for the MVP.

---

# 52. Deployment

Target:

```text
Docker
Kubernetes
GKE
```

Deployments:

```text
discovery-service
api-gateway
account-service
metadata-service
upload-service
download-service
worker
frontend
```

Infrastructure:

```text
PostgreSQL
Redis
Kafka
MinIO
```

Required:

- ConfigMaps
- Secrets
- Health probes
- Readiness probes
- Ingress/reverse proxy
- Independent Upload/Download replica scaling

Do not spend hackathon time creating production-grade HA infrastructure for every dependency.

Document how production could later use managed equivalents.

---

# 53. Architecture Guardrails for Claude Code

Claude Code MUST NOT:

- Create Folder Service
- Create Version Service
- Create Share Service
- Create Trash Service
- Introduce new infrastructure without approval
- Route file bytes through Metadata Service
- Access another service's DB
- Make Redis authoritative
- Put synchronous navigation through Kafka
- Force Saga into simple workflows
- Change API contracts casually
- Change schema ownership casually
- Rename Kafka topics casually
- Replace useful existing code without inspecting it first

Prefer:

```text
working vertical slice
```

over:

```text
maximum architectural sophistication
```

---

# 54. MVP Definition of Done

A user must be able to:

1. Register/login.
2. Create folders.
3. Navigate files/folders.
4. Upload a large file using multipart upload.
5. Interrupt the upload.
6. Resume only missing chunks.
7. Download the completed file.
8. Interrupt the download.
9. Resume using HTTP Range.
10. Rename/move the file.
11. Trash/restore the file.
12. Upload a new version.
13. View version history.
14. Download an old version.
15. Restore an old version as a new current version.
16. Create a public share link.
17. Download through the public link.
18. Revoke/expire the link.
19. Permanently delete a file.
20. Trigger asynchronous storage cleanup.

The architecture must naturally demonstrate:

- Idempotency
- Transactional Outbox
- Kafka
- Consumer idempotency
- Retry
- DLT
- Redis caching
- Distributed rate limiting
- Distributed locking
- Horizontal scalability

---

# 55. Implementation Priority

The implementation order is defined in:

```text
IMPLEMENTATION_PLAN.md
```

Do not attempt to implement the entire architecture at once.

Build and verify vertical slices.