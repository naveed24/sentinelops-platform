# SentinelOps Platform

SentinelOps is a production-style incident and observability platform built to model how backend, SRE, and platform engineering teams register services, track incidents, ingest operational events, evaluate alerts, and expose actionable health data.

## Tech stack

- Java 21
- Spring Boot 4
- Spring Web + Validation
- Spring Data JPA + PostgreSQL
- Flyway migrations
- Redis (prepared for caching/rate limiting)
- Kafka (planned event pipeline)
- Micrometer + Prometheus
- Docker / Docker Compose
- React dashboard (planned)
- GitHub Actions CI (planned)

## Day 1 scope

The initial foundation includes:

- Spring Boot backend skeleton
- Service registry domain and REST API
- PostgreSQL persistence with Flyway
- Global validation/error handling
- Actuator and Prometheus endpoints
- Dockerfile + Docker Compose
- H2-backed integration test profile
- Architecture documentation

## Run locally

### With Docker infrastructure

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

If Maven Wrapper has not yet been generated locally, use:

```bash
mvn spring-boot:run
```

Application: http://localhost:8080  
Health: http://localhost:8080/actuator/health  
Prometheus: http://localhost:8080/actuator/prometheus

### Create a service

```bash
curl -X POST http://localhost:8080/api/v1/services \
  -H "Content-Type: application/json" \
  -d '{
    "name": "orders-api",
    "description": "Handles order lifecycle",
    "ownerTeam": "commerce"
  }'
```

### List services

```bash
curl http://localhost:8080/api/v1/services
```

## Roadmap

1. Foundation, service registry, persistence, containers, observability
2. JWT/RBAC authentication, users and teams
3. Incident lifecycle, audit history and integration testing
4. Kafka event ingestion, Redis caching/rate limiting and resilience
5. Alert rules, notification abstraction and scheduled evaluation
6. React operations dashboard
7. CI, hardening, security/config cleanup, sample data and final documentation

See [docs/architecture.md](docs/architecture.md) for the evolving system design.
