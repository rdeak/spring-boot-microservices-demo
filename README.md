# micro-services-example

An order, inventory and shipping workflow built as a choreography saga on RabbitMQ with Spring Boot 4.1 and
Java 21. The messaging plumbing lives in one shared library, so the three services contain only business logic.

Class names below are unique in the repo, so a name search finds them. Library classes are written
`package/Class` and live in `libs/saga-messaging/src/main/java/hr/bertlab/saga/messaging/`. Service classes carry the
service name.

## Services and events

| Service           | Port | Postgres schema | Consumes                                                            | Publishes                                         |
|-------------------|------|-----------------|---------------------------------------------------------------------|---------------------------------------------------|
| api-gateway       | 8080 | none            | HTTP                                                                | HTTP routes to the three services below           |
| order-service     | 8081 | `orders`        | `InventoryReservationFailed`, `ShippingScheduled`, `ShippingFailed` | `OrderCreated`                                    |
| inventory-service | 8082 | `inventory`     | `OrderCreated`, `ShippingFailed`                                    | `InventoryReserved`, `InventoryReservationFailed` |
| shipping-service  | 8083 | `shipping`      | `InventoryReserved`                                                 | `ShippingScheduled`, `ShippingFailed`             |

```
happy path    order ─OrderCreated─► inventory ─InventoryReserved─► shipping ─ShippingScheduled─► order: CONFIRMED

compensation  inventory ─InventoryReservationFailed─► order: FAILED
              shipping ─ShippingFailed─► order: FAILED
                                      └► inventory: releases the reserved stock
```

Try it with the SKUs and countries seeded by the migrations. `SKU-LOW` has one unit in stock and country `AQ` has
no carrier, which gives the two failure paths.

## What is implemented

### Messaging and consistency

| Practice                        | How                                                                                                                                                                                                                              | Where                                                               |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| Choreography saga               | No orchestrator. Each service reacts to an event and emits the next one. Who consumes what is one enum.                                                                                                                          | `events/EventType`, `*SagaHandlers` in each service                 |
| Compensation                    | `ShippingFailed` releases the reservation and fails the order. The release runs once, guarded by a row lock and a reservation status check.                                                                                      | `InventoryService.release` (inventory-service)                      |
| Business failures as events     | No stock and no carrier are published events, not exceptions, so they are never retried.                                                                                                                                         | `InventoryService.reserve`, `ShippingService.schedule`              |
| Transactional outbox            | `Outbox.append` writes the event row in the caller's transaction and throws when none is active.                                                                                                                                 | `publish/Outbox`, `OrderService.place` (order-service)              |
| Outbox relay                    | Polls every 500 ms, up to 100 rows in id order, publishes with publisher confirms and the mandatory flag, then marks the row published. The first failure stops the batch to keep order. Published rows are purged after 7 days. | `publish/OutboxRelay`                                               |
| One relay per service           | A Postgres advisory lock (`pg_try_advisory_xact_lock`) lets only one instance relay at a time.                                                                                                                                   | `Outbox.tryLock`                                                    |
| Idempotent consumer (inbox)     | `processed_message` insert with `on conflict do nothing`, in the handler's transaction, so a failed attempt rolls the record back and the retry is not mistaken for a duplicate.                                                 | `consume/Inbox`, `consume/SagaMessageDispatcher`                    |
| State guards                    | A second line of defence behind the inbox: an order leaves `PENDING` once, a reservation is released once.                                                                                                                       | `Order.confirm`, `Order.fail`, `Reservation.release`                |
| Per-order ordering              | Consistent-hash exchange on the `orderId` header spreads events over 4 quorum queues per service (`saga.messaging.partitions`), each with single-active-consumer and prefetch 1.                                                 | `topology/SagaTopology`                                             |
| Retry with backoff              | Blocking retry in place, so a later event of the same order cannot overtake: 5 attempts, 500 ms doubling to 8 s.                                                                                                                 | `consume/ConsumerConfiguration`                                     |
| Dead-letter queue               | After the last attempt the message is rejected to `<svc>.dlx` and lands in `<svc>.dlq`. Poison messages (bad headers, unknown type, undecodable body) skip the retries.                                                          | `consume/SagaDeadLetterRecoverer`, `consume/PoisonMessageException` |
| DLQ replay                      | `POST /api/<svc>/admin/dlq/replay?max=N` republishes with the original headers to the same partition and acks from the DLQ only after the broker confirms.                                                                       | `ops/DlqReplayer`, `ops/DlqReplayController`                        |
| Topology owned by every service | Each service declares the whole topology at startup, so a message is never routed to only some subscribers because another service has not started yet.                                                                          | `topology/SagaTopology`                                             |
| Event-carried state transfer    | Events carry what the next step needs (items, `country`), and no service calls another over HTTP.                                                                                                                                | `events/`                                                           |

### Services and data

| Practice            | How                                                                                                                                                                                             | Where                                                             |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| Schema per service  | One Postgres instance, one schema per service, each with its own Flyway history and `ddl-auto: validate`. These are separate schemas, not separate databases.                                   | `application.yaml` and `db/migration/*` in each service           |
| Shared saga tables  | The library ships the outbox and inbox migrations as an extra Flyway location, applied into each service's schema.                                                                              | `libs/saga-messaging/src/main/resources/db/saga-messaging/`       |
| Concurrency control | Optimistic `@Version` on orders. Pessimistic row locks on stock, taken in SKU order so concurrent reservations cannot deadlock.                                                                 | `Order`, `StockRepository.lockAll` (inventory-service)            |
| Service chassis     | `SagaMessagingAutoConfiguration` wires everything. A service sets `saga.messaging.service` and registers one `SagaHandlers` bean. Startup fails if it lacks a handler for an event it consumes. | `SagaMessagingAutoConfiguration`, `consume/SagaMessageDispatcher` |
| Externalised config | `.env` plus environment variables, with local defaults.                                                                                                                                         | `.env`, `application.yaml`                                        |
| Input limits        | Order validation returns a 400 problem detail. List endpoints clamp `limit` to 200.                                                                                                             | `Order.place`, `OrderController` (order-service)                  |

### Edge

| Practice    | How                                                                                                                     | Where                                                      |
|-------------|-------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| API gateway | Spring Cloud Gateway (WebFlux), path routes `/api/orders`, `/api/inventory`, `/api/shipping` to fixed `localhost` URIs. | `services/api-gateway/src/main/resources/application.yaml` |
| Frontend    | React console for orders, inventory and shipments. The Vite dev server proxies `/api` to the gateway.                   | `frontend/`                                                |

### Observability and operations

| Practice                | How                                                                                                                                                                                                                                                                                                 | Where                                     |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|
| Distributed tracing     | W3C trace context, OTLP export to Jaeger. The trace context is stored in the outbox row and restored as a `saga.outbox.publish` span in the relay, so one trace covers gateway, order, inventory, shipping and back, including DLQ replays. The library depends on the Micrometer Tracing API only. | `publish/OutboxTraceContext`, `TracingIT` |
| Metrics                 | Micrometer, read from `/actuator/metrics/{name}` on each service: `saga.events.processed`, `saga.dlq.messages`, `saga.dlq.depth`, `saga.outbox.pending`, `saga.outbox.oldest.pending.age`, `saga.alerts`.                                                                                           | `ops/SagaMetrics`, `ops/SagaMonitor`      |
| Alerting                | Alert codes with severities, written to log category `saga.alerts` and counted in `saga.alerts{severity,code}`. `CRITICAL` is a failed compensation. `AlertService` is the single seam for a webhook later.                                                                                         | `ops/AlertCode`, `ops/AlertService`       |
| Broker and outbox watch | Polls DLQ depth and outbox lag every 30 s and alerts when they grow.                                                                                                                                                                                                                                | `ops/SagaMonitor`                         |
| Stuck saga detection    | Orders still `PENDING` after 5 minutes raise one `SAGA_STUCK` alert each, claimed with a conditional update so it fires once.                                                                                                                                                                       | `StuckOrderDetector` (order-service)      |
| Health                  | Actuator health on the three services. Compose health checks for Postgres and RabbitMQ.                                                                                                                                                                                                             | `docker-compose.yaml`                     |

### Quality and delivery

| Practice                                 | How                                                                                                                           | Where                            |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| Integration tests on real infrastructure | Real Postgres and RabbitMQ from `docker-compose.yaml`, no mocked broker. Each run gets its own schema and broker name prefix. | `SagaTestSupport`, `*IT` classes |
| End-to-end test                          | All three services start in one JVM and run the happy path and both compensations.                                            | `tests/saga-e2e`                 |
| Static analysis                          | Error Prone and NullAway fail the build. Spotless formats Java, Gradle and YAML files.                                        | `build.gradle`                   |
| CI                                       | GitHub Actions starts Postgres and RabbitMQ and runs `./gradlew build`.                                                       | `.github/workflows/build.yml`    |

## Not implemented

Looking for these? They are not here.

- Service discovery and load balancing. Gateway routes are fixed URIs.
- Authentication and authorisation. The DLQ replay endpoints are open and reachable through the gateway's `/api/**`
  route, so block them before exposing the gateway.
- Rate limiting, circuit breakers, bulkheads.
- Idempotency key on `POST /api/orders`. A client retry creates a second order.
- Event versioning, schema registry, consumer contract tests. Events are Java records in the shared library, so
  the services upgrade together.
- Container images, Kubernetes manifests. The services run with `bootRun`, only the infrastructure is in Docker.
- Prometheus, Alertmanager, log aggregation. Alerts are a log line and a counter.
- CQRS read models, event sourcing, a separate database per service, a config server.

## Run

Needs Docker and JDK 21. The first service you start brings up Postgres, RabbitMQ and Jaeger from
`docker-compose.yaml`. They keep running after the apps stop (`lifecycle-management: start-only`).

```bash
./gradlew :services:inventory-service:bootRun
./gradlew :services:shipping-service:bootRun
./gradlew :services:order-service:bootRun
./gradlew :services:api-gateway:bootRun
cd frontend && pnpm install && pnpm dev     # optional, Node 24.22 or newer, http://localhost:5173
```

RabbitMQ needs the consistent-hash plugin from `infra/rabbitmq/enabled_plugins`. If your RabbitMQ container was
created before that file existed, recreate it once:
`docker compose up -d --force-recreate rabbitmq`.

| UI                  | URL                                    |
|---------------------|----------------------------------------|
| Jaeger              | http://localhost:16686                 |
| RabbitMQ management | http://localhost:15672 (`dev` / `dev`) |

## Try it

```bash
# Happy path: CONFIRMED
curl -s -XPOST localhost:8080/api/orders -H 'content-type: application/json' \
  -d '{"country":"HR","items":[{"sku":"SKU-1","quantity":2}]}'

# Compensation: no carrier for AQ, stock released, order FAILED
curl -s -XPOST localhost:8080/api/orders -H 'content-type: application/json' \
  -d '{"country":"AQ","items":[{"sku":"SKU-1","quantity":2}]}'

# Insufficient stock: order FAILED
curl -s -XPOST localhost:8080/api/orders -H 'content-type: application/json' \
  -d '{"country":"HR","items":[{"sku":"SKU-LOW","quantity":5}]}'

curl -s localhost:8080/api/orders/<id>
curl -s localhost:8080/api/inventory/SKU-1
curl -s localhost:8080/api/shipping/<id>
```

## Operate

- Metrics and health: `GET /actuator/metrics/{name}` and `/actuator/health` on ports 8081 to 8083. The gateway has no
  actuator.
- Replay dead letters after fixing the cause: `POST /api/{orders|inventory|shipping}/admin/dlq/replay?max=100`.

## Test

```bash
./gradlew build                                     # unit, integration and end-to-end tests, Docker required
./gradlew :libs:saga-messaging:cleanTestLeftovers   # drop schemas and queues left by crashed test runs
cd frontend && pnpm check                           # typecheck, lint, format and tests
```

