# Online Booking System — Microservices Application

ITRI623 Project 2026 — Phase 1

## Architecture

| Service | Port | Owns | Purpose |
|---|---|---|---|
| Frontend | 3000 | — | Browser client — sign in, browse resources, create/cancel bookings |
| Eureka Server | 8761 | — | Service registry / discovery |
| API Gateway | 8090 (host) → 8080 (container) | — | Single entry point, routing, JWT validation, request logging |
| User Service | 8081 | `userdb` | Registration, login, JWT issuance |
| Resource Service | 8082 | `resourcedb` | Bookable resources (rooms/equipment) & availability |
| Booking Service | 8083 | `bookingdb` | Create/manage bookings; calls Resource Service with circuit breaker + retry |

All client traffic goes through the **API Gateway** at `http://localhost:8090`. Direct
service ports are exposed too, for debugging and demonstrating service discovery.

> **Note:** the gateway's host port is mapped to `8090` instead of the default `8080`
> in `docker-compose.yml`, since `8080` was already in use on the host machine during
> local testing. The container's internal port is still `8080` — only the host-side
> mapping changed. If `8080` is free on your machine, you can change it back.

## Patterns implemented

1. **Database per service** — each service has its own PostgreSQL container/schema; no service accesses another's database directly.
2. **Centralised logging** — structured log lines (`event=...`) at the gateway and in every service for requests, auth failures, business events, and inter-service calls. Ready to ship to ELK/Grafana Loki.
3. **Application metrics** — Spring Boot Actuator exposes `/actuator/metrics` and `/actuator/health` on every service; Prometheus-ready.
4. **JWT authentication** — User Service issues tokens on login; the Gateway validates tokens on protected routes before forwarding.
5. **Health checks** — every service exposes `/actuator/health` including DB connectivity.
6. **Circuit breaker + retry** — Booking Service wraps calls to Resource Service in Resilience4j retry (3 attempts) + circuit breaker (opens after 50% failure rate), with a fail-safe fallback.

## Running locally

```bash
docker compose up -d --build
```

This starts: the frontend, Eureka, API Gateway, all 3 domain services, and one Postgres container per service.

Open **http://localhost:3000** — register an account, sign in, add a resource, and book
it, all from the browser. This is the easiest way to demonstrate the full workflow.

Check registration: open `http://localhost:8761` — you should see `API-GATEWAY`,
`USER-SERVICE`, `RESOURCE-SERVICE`, and `BOOKING-SERVICE` registered.

## Demo workflow via curl (alternative to the browser UI)

```bash
# 1. Register a user
curl -X POST http://localhost:8090/api/users/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dr.smith@uni.ac.za","password":"password123","fullName":"Dr Smith"}'

# 2. Log in and capture the JWT
curl -X POST http://localhost:8090/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dr.smith@uni.ac.za","password":"password123"}'
# -> {"token": "...", "email": "...", "role": "USER"}

# 3. Create a bookable resource
curl -X POST http://localhost:8090/api/resources \
  -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" \
  -d '{"name":"Meeting Room A","type":"ROOM","location":"Building 4","capacity":8}'

# 4. Create a booking (Booking Service checks availability via Resource Service)
curl -X POST http://localhost:8090/api/bookings \
  -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN>" \
  -d '{"userId":1,"resourceId":1,"startTime":"2026-09-05T10:00:00","endTime":"2026-09-05T11:00:00"}'

# 5. Observe logs/metrics
docker compose logs -f api-gateway booking-service
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/circuitbreakers
```

To demonstrate the circuit breaker: stop `resource-service` (`docker compose stop
resource-service`) and repeat step 4 — bookings will be rejected via the fallback
instead of hanging, and `event=dependency_failure` will appear in booking-service logs.

## Preparation for the future SOC layer

Every service emits structured events (`event=...`) covering: authentication
successes/failures, request received/completed, business events (booking
confirmed/rejected), and inter-service dependency failures — the raw material for the
future SOC monitoring and Neo4j knowledge-graph phase.
