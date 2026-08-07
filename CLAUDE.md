# Dropbox Lite — Claude Code Instructions

## Goal

Build a production-style Dropbox-like file storage and sharing SaaS for the
Spring Boot hackathon.

The project should demonstrate practical backend/distributed-system engineering
while remaining simple enough to complete, deploy, and demo within the
hackathon timeline.

## Source of Truth

Before making architectural or implementation decisions, use:

- `docs/TECHNICAL_DESIGN.md` — architecture, services, database, APIs,
  Kafka, Outbox, Redis, MinIO, consistency and failure handling.
- `docs/IMPLEMENTATION_PLAN.md` — implementation phases, priorities,
  dependencies and scope.

Do not change documented architecture or service boundaries without explicit
approval.

## Current Stack

- Java 21
- Spring Boot 4.1.x
- Spring Cloud
- Maven
- PostgreSQL
- MinIO
- Kafka
- Redis
- Eureka
- Docker
- Kubernetes / GKE

Eureka is platform infrastructure, not a business service.

## Engineering Rules

- Reuse the existing Spring Boot service structure.
- Do not regenerate services.
- Keep service data ownership isolated.
- PostgreSQL is the durable source of truth for metadata/state.
- MinIO stores file objects.
- Redis is cache/coordination, not durable truth.
- Use Kafka only where defined by the technical design.
- Important DB + event operations use the Outbox pattern.
- Kafka consumers must be idempotent.
- Prefer simple production-quality implementation over over-engineering.
- Do not introduce new services/dependencies/patterns unless required.

## Working Rules

For each task:

1. Read only the relevant sections/files.
2. Inspect only the affected modules unless broader inspection is necessary.
3. Implement only the requested scope.
4. Do not automatically implement future phases.
5. Do not silently change APIs, schemas, architecture or service boundaries.
6. Keep changes minimal and focused.
7. Ensure affected services compile.
8. Give a concise summary of changes and any blockers.

If implementation conflicts with the technical design, explain the conflict
before changing the architecture.

## MVP Testing Rule

During initial MVP development:

- Do not create unit tests unless explicitly requested.
- Prioritize implementation and successful compilation.
- Use integration/API testing as features become functional.
- Critical reliability tests can be added after the core MVP works.

## Context / Token Efficiency

Claude Code usage is limited.

- Do not scan the entire repository for every task.
- Do not repeatedly reread all documentation.
- Read only documentation relevant to the current task.
- Keep plans concise.
- Keep final summaries concise.
- Do not generate extra documentation unless requested.

## Priority

Working → integrated → deployable → reliable → polished.

Do not sacrifice the core MVP for optional features.