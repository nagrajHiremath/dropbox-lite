# Dropbox Lite

A production-style, Dropbox-like file storage and sharing platform — Spring Boot microservices on the backend, React on the frontend. Built for a hackathon.

## Live

| | |
|---|---|
| UI | http://dropbox.34-14-138-43.nip.io |
| API Gateway | http://dropbox.34-14-138-43.nip.io/api |
| Swagger / OpenAPI | http://dropbox.34-14-138-43.nip.io/swagger-ui.html (aggregates all services) |

## Overview

Six independently deployable services — **Account, Metadata, Upload, Download, API Gateway, Async Worker** — each owning its own PostgreSQL database, talking over REST and Kafka. File bytes live in MinIO, Redis backs caching/locks/rate-limiting.

**Stack:** Java 21 · Spring Boot · Spring Cloud Gateway · PostgreSQL · Redis · Kafka · MinIO · Docker · Kubernetes (GKE) · React + Vite + TypeScript

## Architecture & Docs

[![System Context](docs/buisness-flow-svg/L1_SYSTEM_CONTEXT.svg)](docs/ARCHITECTURE.md)

| Doc | What's in it |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | System context → containers → business flows → per-service ERDs (SVGs) |
| [Design Trade-offs](docs/DESIGN_TRADE_OFFS.md) | Scaling, failure handling, and consistency trade-offs |
| [Technical Design](docs/claude/TECHNICAL_DESIGN.md) | Full architecture, APIs, DB schema, Kafka/Outbox design |
| [Postman Collection](docs/postman/Dropbox-Lite.postman_collection.json) | Ready-to-import API requests |
