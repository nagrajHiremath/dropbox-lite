# Dropbox Lite

A production-style, Dropbox-like file storage and sharing platform — Spring Boot microservices on the backend, React on the frontend. Built for a hackathon.

[![System Context](docs/buisness-flow-svg/L1_SYSTEM_CONTEXT.svg)](docs/ARCHITECTURE.md)

- **Live UI:** [http://dropbox.34-14-138-43.nip.io](http://dropbox.34-14-138-43.nip.io)
- **Swagger / OpenAPI:** [http://dropbox.34-14-138-43.nip.io/swagger-ui.html](http://dropbox.34-14-138-43.nip.io/swagger-ui.html) (aggregates all services)

## Stack

- **Frontend:** React, Vite, TypeScript
- **Backend:** Spring Boot microservices (Java 21), Spring Cloud Gateway
- **Data:** PostgreSQL, Redis, Kafka, MinIO
- **Infra:** Kubernetes (GKE), Docker, GitHub Actions CI/CD

## Services

| Service | Purpose |
|---|---|
| `dropbox-ui` | React frontend |
| `api-gateway` | Routing, JWT auth, rate limiting |
| `account-service` | Auth, users |
| `metadata-service` | File/folder metadata, sharing, versioning |
| `upload-service` | Multipart upload, quota |
| `download-service` | File download, range requests |
| `async-worker` | Kafka consumers — lifecycle cleanup, storage usage |

## Docs

| Doc                                                                     | What's in it |
|-------------------------------------------------------------------------|---|
| [Architecture & Buisness Flow](docs/ARCHITECTURE.md)                    | System context → containers → business flows → per-service ERDs (SVGs) |
| [Design Trade-offs](docs/DESIGN_TRADE_OFFS.md)                          | Scaling, failure handling, and consistency trade-offs |
| [Postman Collection](docs/postman/Dropbox-Lite.postman_collection.json) | Ready-to-import API requests |

## Run Locally

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
