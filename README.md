## 🧊 Dropbox Lite

A production-style, Dropbox-like file storage and sharing platform — Spring Boot microservices on the backend, React on the frontend. Built for a hackathon.

[![System Context](docs/buisness-flow-svg/L1_SYSTEM_CONTEXT.svg)](docs/ARCHITECTURE.md)

### 🔴 Live

| Service | URL |
|---|---|
| UI | http://dropbox.34-14-138-43.nip.io |
| Swagger / OpenAPI | http://dropbox.34-14-138-43.nip.io/swagger-ui.html (aggregates all services) |

### Overview

Six independently deployable services, each owning its own PostgreSQL database and talking over REST and Kafka. File bytes live in MinIO; Redis backs caching, locks, and rate-limiting.

- [![account-service](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-account-service.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-account-service.yaml)
- [![api-gateway](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-api-gateway.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-api-gateway.yaml)
- [![metadata-service](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-metadata-service.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-metadata-service.yaml)
- [![upload-service](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-upload-service.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-upload-service.yaml)
- [![download-service](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-download-service.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-download-service.yaml)
- [![async-worker](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-async-worker.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-async-worker.yaml)
- [![dropbox-ui](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-ui.yaml/badge.svg)](https://github.com/nagrajHiremath/dropbox-lite/actions/workflows/deploy-dropbox-ui.yaml)


**Stack:** Java 21 · Spring Boot · Spring Cloud Gateway · PostgreSQL · Redis · Kafka · MinIO · Docker · Kubernetes (GKE) · React + Vite + TypeScript

**CI/CD:** per-service [GitHub Actions](.github/workflows/) pipelines (path-filtered — only the changed service rebuilds) deploy to Google Cloud (GKE) on merge to `main`.

Applies idempotency, an outbox pattern, retry/DLT, distributed locking, caching, and rate limiting where relevant to each service — see [Design Trade-offs](docs/DESIGN_TRADE_OFFS.md) for specifics.

### Architecture & Docs

| Doc | What's in it |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | System context → containers → business flows → per-service ERDs (SVGs) |
| [Design Trade-offs](docs/DESIGN_TRADE_OFFS.md) | Scaling, failure handling, and consistency trade-offs |
| [Technical Design](docs/claude/TECHNICAL_DESIGN.md) | Full architecture, APIs, DB schema, Kafka/Outbox design |
| [Postman Collection](docs/postman/Dropbox-Lite.postman_collection.json) | Ready-to-import API requests |

### Run Locally

```bash
cp .env.example .env
docker compose up -d          # postgres, redis, kafka, minio

# each service (separate terminals), from its own directory:
./mvnw spring-boot:run         # account-service   -> :8081
./mvnw spring-boot:run         # metadata-service  -> :8082
./mvnw spring-boot:run         # upload-service    -> :8083
./mvnw spring-boot:run         # download-service  -> :8084
./mvnw spring-boot:run         # api-gateway       -> :8080
./mvnw spring-boot:run         # async-worker      -> :8085

cd dropbox-ui && npm install && npm run dev   # UI -> http://localhost:5173
```

Gateway on `:8080` fronts everything — API at `/api/**`, Swagger at `/swagger-ui.html`. `discovery-service` (Eureka) is platform infra, not required for services to talk to each other locally.
