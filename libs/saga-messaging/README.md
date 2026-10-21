# saga-messaging

The library behind the order, inventory and shipping choreography saga. It holds the event contract, a
transactional outbox with a relay to RabbitMQ, the broker topology, an idempotent consumer with retry and
dead-lettering, and the alerts and metrics around them. A service has two jobs: call `Outbox.append` inside its own
transaction, and register one `SagaHandlers` bean. Everything else is auto-configured once `saga.messaging.service`
is set.

This file is for working on the code. The root README has the overview, how to run the system and the table of
practices. Library classes are written `package/Class` and live under
`libs/saga-messaging/src/main/java/hr/bertlab/saga/messaging/`.

## Message path

```
 order-service ─────┐
 inventory-service ─┼─► Outbox ─► OutboxRelay ─► saga.events ──┬─► inventory.in ─► inventory.q.0 … q.N-1
 shipping-service ──┘                                          ├─► order.in     ─► order.q.0 … q.N-1
                                                               └─► shipping.in  ─► shipping.q.0 … q.N-1

 Outbox       one outbox_event table per service, in its own schema, with its own relay
 saga.events  topic exchange, routing key = event name
 <svc>.in     x-consistent-hash exchange, hashes the orderId header, one binding per partition queue
 <svc>.q.N    quorum queue, single active consumer, prefetch 1
 after the last retry:  <svc>.q.N ─► <svc>.dlx (fanout) ─► <svc>.dlq
```

- The routing key is the event's wire name, which is the payload record's simple name (`OrderCreated`).
  `topology/SagaTopology` binds `<svc>.in` to `saga.events` once for every event that service consumes, as listed in
  `EventType`.
- Every service declares the topology of all three services at startup. Declarations are idempotent, and an event
  published before its consumer has ever started is not dropped.
- Events of one order are handled in the order they were appended, because four things hold together: the hash
  exchange sends one `orderId` to one queue, that queue has one active consumer with prefetch 1, the relay publishes
  in outbox id order, and it stops the batch at the first failure. Nothing is ordered across orders, or across events
  of one order that come from different services.
- `saga.messaging.prefix` is prepended to every exchange and queue name by `topology/SagaNames`. It is empty in
  normal runs. Tests set it so each run gets its own broker objects.

## Where things are

- `events/`: the contract. `EventType` is the whole choreography, `SagaEvent` and its records are the payloads,
  `SagaMessage` is the wire format and `SagaEventCodec` the JSON.
- `publish/`: how an event leaves a service. `Outbox`, `OutboxRelay` and `OutboxTraceContext`;
  `OutboxConfiguration` schedules the relay.
- `topology/`: what exists in RabbitMQ and what it is called. `SagaTopology` and `SagaNames`.
- `consume/`: what happens when a message arrives. `SagaHandlers`, `SagaMessageDispatcher` and `Inbox`, plus retry and
  dead-lettering in `ConsumerConfiguration` and `SagaDeadLetterRecoverer`. `SagaConnectionFactoryConfigurer` turns on
  publisher confirms and returns and gives the connection a thread pool (`saga-rabbit-*`, partitions + 2 threads) that
  runs the handlers.
- `ops/`: alerts (`AlertCode`, `AlertService`), counters (`SagaMetrics`), gauges and the DLQ and outbox watcher
  (`SagaMonitor`), and dead-letter replay (`DlqReplayer`, `DlqReplayController`).
- `SagaMessagingAutoConfiguration`: switched on by `saga.messaging.service`. Imports the four package configurations
  and defines `SagaEventCodec` and `sagaTransactionTemplate`.
- `SagaMessagingProperties`: every `saga.messaging.*` key with its default (the `@DefaultValue`s).
- `SagaService`: `ORDER`, `INVENTORY`, `SHIPPING`. `key()` is the lower-case name used in broker names.
- `src/main/resources/db/saga-messaging/`: Flyway migrations for `outbox_event` and `processed_message`. Each service
  runs them against its own schema.
- `src/main/resources/saga-messaging-defaults.yaml`: tracing defaults, see "Tracing" below.
- `src/testFixtures/`: `SagaTestSupport`, `TestIsolationCleanup` and `TestLeftoverCleaner`, used by this library's
  tests, the service tests and `tests/saga-e2e`.
- `src/test/`: `*Test` classes need no infrastructure. `*IT` classes extend `AbstractSagaMessagingIT` and use the test
  app in `testapp/`.

Skip `ops/` on a first read. `consume` only reports into it.

## Follow one order through the code

Open these in order. Each step is one file.

1. `OrderService.place` (order-service) saves the PENDING order and calls `Outbox.append` in the same transaction.
2. `publish/Outbox.append` inserts a row into `outbox_event`: event id, `aggregate_id` (the order id), event type,
   JSON payload and the current trace context. It throws `IllegalTransactionStateException` when no transaction is
   active. Nothing has touched RabbitMQ yet.
3. `publish/OutboxRelay.publishPending` runs every 500 ms (`OutboxConfiguration`). It takes a Postgres advisory lock
   (`Outbox.tryLock`), so only one instance of a service relays at a time. It reads unpublished rows in id order,
   sends each to `saga.events` with the event name as routing key, waits for the broker's confirm and marks the row
   published. A nack, an unroutable return, a timeout or an unknown event type stops the batch, and the row is tried
   again on the next poll.
4. `events/SagaMessage.build` is the wire format: the JSON body plus the headers `eventId`, `eventType`, `orderId`
   and `occurredAt`.
5. `events/EventType` says `OrderCreated` is consumed by `INVENTORY`.
6. `topology/SagaTopology` turns that into a binding from `saga.events` to `inventory.in` with routing key
   `OrderCreated`. The hash exchange sends the `orderId` header to one of the `inventory.q.N` queues.
7. `consume/ConsumerConfiguration` starts one `DirectMessageListenerContainer` over all partition queues of the
   service: one consumer per queue, prefetch 1, with the retry interceptor in front of the dispatcher.
8. `consume/SagaMessageDispatcher.onMessage` checks the headers, finds the `EventType`, decodes the body with
   `events/SagaEventCodec` and opens a transaction.
9. `consume/Inbox.tryRecord` inserts the event id into `processed_message`. A duplicate is skipped and counted.
10. `consume/SagaHandlers.handle` calls what the service registered. `InventorySagaHandlers` (inventory-service) maps
    `OrderCreated` to `InventoryService.reserve`.
11. `InventoryService.reserve` reserves stock and calls `Outbox.append` with `InventoryReserved` or
    `InventoryReservationFailed`. That is step 2 again, now in the inventory service. Shipping and order continue the
    same way (`ShippingService.schedule`, `OrderSagaHandlers`).

## Writing a handler

```java
@Bean
SagaHandlers inventoryHandlers(InventoryService inventory) {
    return SagaHandlers.of(SagaService.INVENTORY)
            .on(OrderCreated.class, inventory::reserve)
            .on(ShippingFailed.class, inventory::release);
}
```

- The handler runs in the same transaction as the inbox insert. Domain writes and `Outbox.append` calls made there
  commit together, and a thrown exception rolls all of it back, including the inbox row. `@Transactional` service
  methods join that transaction.
- Publish a business failure as an event instead of throwing. "Not enough stock" and "no carrier" are
  `InventoryReservationFailed` and `ShippingFailed` (`InventoryService.reserve`, `ShippingService.schedule`). A throw
  is attempted `saga.messaging.retry.max-attempts` times in total (5 by default) and then dead-lettered, and while it
  retries the queue is blocked: every order hashed to that partition waits.
- Throw for failures that a retry can fix or that you want an alert for. `InventoryService.release` throws when the
  reservation does not exist, and the message ends up in the DLQ with a CRITICAL alert.
- The inbox deduplicates by event id only. Two events with different ids that mean the same thing both run, so guard
  the state in the domain. `InventoryService.reserve` checks `existsById`, and `release` takes a row lock and checks
  the reservation status. `InventorySagaIT.secondShippingFailedWithADifferentEventIdDoesNotReleaseTwice` covers it.
- Every event carries `orderId()`. It is the aggregate id, the partition key and a string header, and the codec rejects
  a payload without it.

## When a handler throws

The retry interceptor (`ConsumerConfiguration.sagaRetryInterceptor`) retries in the listener thread with backoff.
After the last attempt, `SagaDeadLetterRecoverer` counts the failure, raises an alert and rejects the message without
requeue, and the queue's dead-letter exchange moves it to `<svc>.dlq`. The alert code depends on the event:

| Case                                              | Alert code              | Severity |
|---------------------------------------------------|-------------------------|----------|
| `PoisonMessageException`, which skips the retries | `POISON_MESSAGE`        | ERROR    |
| An event type with the `compensation` flag        | `COMPENSATION_FAILED`   | CRITICAL |
| Anything else                                     | `MESSAGE_DEAD_LETTERED` | ERROR    |

The dispatcher throws `PoisonMessageException` for a message that can never succeed: a missing or malformed header, an
event type this service has no handler for, or a body that does not decode.

After the cause is fixed, `POST <admin-path>/dlq/replay?max=N` calls `ops/DlqReplayer`. It republishes dead letters to
the service's own `<svc>.in` with the original headers, so they land in the same partition. It moves only the messages
that were in the DLQ when the call started. The endpoint exists only in servlet applications that set
`saga.messaging.admin-path`.

## Common tasks

### Add an event

1. Add a record in `events/` that implements `SagaEvent` with `UUID orderId` as a component, and add it to the
   `permits` list of `SagaEvent`.
2. Add a constant to `EventType`: the record class, the services that consume it, and the `compensation` flag.
   For a `ShippingDelayed` event consumed by the order service the constant would be
   `SHIPPING_DELAYED(ShippingDelayed.class, Set.of(ORDER), false)`. The wire name is the record's simple name, so there
   is no mapping to write.
3. In the publishing service, inject `Outbox` and call `outbox.append(new ShippingDelayed(...))` in the same
   `@Transactional` method as the domain write (see `ShippingService.schedule`).
4. In each consuming service, add `.on(ShippingDelayed.class, ...)` to its `SagaHandlers` bean. The service refuses to
   start without a handler for every event `EventType` lists for it, and `on` rejects an event the service is not
   listed for.
5. There is no topology code to change. `SagaTopology` derives the bindings from `EventType.consumedBy`.
6. Update the tests that pin the contract: `EventTypeTest` (wire names, consumers, `consumedBy`, the compensation
   flag) and `SagaTopologyTest` when `INVENTORY` consumes the event. The library's test app runs as `INVENTORY`, so
   `testapp/RecordingHandlers` needs an `.on(...)` for any new event `INVENTORY` consumes. Without it every `*IT` in
   the library fails at context start.
7. Add a service test for the new reaction (see "Testing") and update the services and events table in the root
   README.

Things that break an event that already exists:

- Renaming a record changes its wire name. Outbox rows not yet published under the old name make
  `OutboxRelay` fail on every poll, which stalls that service's relay, and queued messages with the old name become
  poison. Removing an `EventType` does the same. Rename only when no rows or messages are in flight.
- Events are shared Java records with no versioning, so a payload change goes to every service at once.

### Let another service react to an existing event

Add the service to the event's consumer set in `EventType`, add `.on(...)` to that service's `SagaHandlers` bean, and
update `EventTypeTest` and `SagaTopologyTest`. When a consumer is removed again, its old binding stays on a broker that
has been running, because declarations only add. The event keeps arriving at that service, the dispatcher has no
handler for it, and every message is dead-lettered as `POISON_MESSAGE`. Delete the binding in the RabbitMQ management
UI.

### Add a compensation

1. The service that cannot continue publishes a failure event as a normal outcome and does not throw
   (`ShippingFailed` from `ShippingService.schedule`).
2. In `EventType` set the `compensation` flag to `true` and list every service that must undo earlier work, plus
   `ORDER` if the order has to fail. `ShippingFailed` goes to `ORDER` and `INVENTORY`. The flag makes a dead-lettered
   compensation a CRITICAL alert, because data stays inconsistent until someone replays it.
3. Write the compensating handler so a second delivery is harmless: lock the row, check the status, do nothing if the
   work is already undone (`InventoryService.release`).
4. Test the forward path, the compensation and a duplicate (`InventorySagaIT`), and add the failing path to
   `SagaEndToEndTest` when it crosses services.

### Add a service

1. Add a constant to `SagaService`. All services declare the complete topology, so every running service creates the
   new exchanges and queues from this constant. `SagaTopologyTest` lists the exchanges exactly and
   `EventTypeTest.everyServiceConsumesSomething` requires a consumer, so both need updating.
2. Name the service as a consumer in the `EventType` entries it handles.
3. Add the module to `settings.gradle` and copy `services/inventory-service/build.gradle`. The relevant lines are
   `implementation project(':libs:saga-messaging')` and
   `testImplementation testFixtures(project(':libs:saga-messaging'))`.
4. In `application.yaml` (copy the inventory service's) set:
   - `saga.messaging.service`, and `saga.messaging.admin-path` if it should expose the replay endpoint.
   - `spring.flyway.locations` with `classpath:db/saga-messaging` plus the service's own migrations, and the Flyway
     and Hikari schema.
   - `spring.task.scheduling.pool.size`. All three services use 4, so the relay, the monitor and the service's own
     scheduled jobs do not wait for each other. Boot's default is one thread.
   - Leave `saga.messaging.partitions` alone. It must be the same in every service, because each one declares the
     whole topology. A mismatch leaves hash buckets that no consumer reads.
5. Number the service's own migrations from `V2`. The library owns `V1` and `V1_1`.
6. Register one `SagaHandlers` bean for the service.
7. Add a test base class that calls `SagaTestSupport.registerIsolation` and a row in the root README table. Add the
   service to `tests/saga-e2e` if it takes part in the flow.

### Change retry, relay or monitor settings

The keys and defaults are in `SagaMessagingProperties`. The relay settings are read in `OutboxConfiguration`, the
retry settings in `ConsumerConfiguration.sagaRetryInterceptor` and the monitor settings in `MonitoringConfiguration`.
Never lower `partitions` on a broker that has been running without first deleting the extra `<svc>.q.N` queues. They
stay bound to the hash exchange with no consumer, and messages hashed to them are never read.

### Change the outbox or inbox tables

Add a migration to `src/main/resources/db/saga-messaging/`. Every service applies it to its own schema. The library
uses `V1` and `V1_1` and the services start at `V2`, so `V1_1` was numbered to run between them (see
`docs/superpowers/specs/2026-10-19-request-tracing-design.md`). Keep the change additive and nullable, because rows
written before it exist (`Outbox.fetchPending` treats a missing `trace_context` as empty). The library's tests always
run on a fresh schema and will not notice a numbering problem. A long-lived dev schema that already applied a service's
`V2` can reject a lower-numbered migration as out of order, which is Flyway's default behavior. Reset that schema or
enable out-of-order migrations locally.

### Add an alert or a metric

- Alert: add a constant with a severity to `AlertCode` and call `AlertService.raise(code, details)`. It writes a log
  line on the `saga.alerts` logger with the details as key-values and increments `saga.alerts`. Services inject it too:
  `StuckOrderDetector` (order-service) raises `SAGA_STUCK` for orders that stay PENDING.
- Metric: counters go through `SagaMetrics`, gauges are registered and sampled in `SagaMonitor`.

| Meter                                                       | Kind    | Tags                                                        |
|-------------------------------------------------------------|---------|-------------------------------------------------------------|
| `saga.events.processed`                                     | counter | `eventType`, `outcome` (`processed`, `duplicate`, `failed`) |
| `saga.dlq.messages`                                         | counter | `queue`, `eventType`                                        |
| `saga.alerts`                                               | counter | `severity`, `code`                                          |
| `saga.dlq.depth`                                            | gauge   | `queue`                                                     |
| `saga.outbox.pending`, `saga.outbox.oldest.pending.age` (s) | gauge   | none                                                        |

## Testing

- `*Test` classes need no infrastructure. `*IT` classes use the real Postgres and RabbitMQ from the root
  `docker-compose.yaml`, which Spring Boot starts for them, so Docker must be running.
- Library ITs extend `AbstractSagaMessagingIT`, which shares one cached context: the test app in `testapp/`, running
  as `INVENTORY`. `RecordingHandlers` records handled events and can be told to fail with `failTimes` and
  `failAlways`.
- Service ITs extend a base class whose `@DynamicPropertySource` calls
  `SagaTestSupport.registerIsolation(registry, "<schema base>")` (`AbstractInventoryServiceIT`). That gives the run a
  unique schema and broker prefix, shortens the saga timers, and `TestIsolationCleanup` removes both when the context
  closes.
- To drive a service, publish the upstream event the way the other service's relay would:
  `SagaTestSupport.publish(rabbit, names, codec, event)`. Pass an event id as the last argument to simulate a
  redelivery. Then wait with Awaitility on `allProcessed`, `outboxEventTypes` or `counter` (`InventorySagaIT`), or on
  `queueDepth` (`ConsumerIT`).
- Counters live in the cached context and are shared by every test in it. Compare against a value read before the
  test, or wait on `allProcessed`, and avoid exact totals.
- `OrderOutboxAtomicityIT` shows how to prove that a rollback leaves neither the domain row nor the outbox row.
  `tests/saga-e2e` starts all three services in one JVM and runs the happy path and both compensations.

```bash
./gradlew :libs:saga-messaging:test
./gradlew :libs:saga-messaging:test --tests 'hr.bertlab.saga.messaging.consume.ConsumerIT'
./gradlew build                                     # every module plus tests/saga-e2e
./gradlew :libs:saga-messaging:cleanTestLeftovers   # drop schemas and queues left by killed runs
```

`cleanTestLeftovers` keeps a run while any of its schemas is less than 30 minutes old. `-PstaleAfterMinutes=0`
removes those too, so use it only when no tests are running.

## Debugging a stuck order

Work through these in order. `<schema>` is `orders`, `inventory` or `shipping`.

1. Is the event published? In the producing service's schema:
   `select id, event_id, event_type, created_at, published_at from <schema>.outbox_event where aggregate_id = '<orderId>' order by id;`
   A row with `published_at` null and a growing age means the relay is failing. Look for "Cannot publish outbox
   row", "Broker nacked", "was unroutable" and "failed, will retry" in that service's log. Unroutable means a binding
   is missing, and "failed, will retry" is what a broker outage or a confirm timeout logs. The
   `saga.outbox.pending` gauge and the `OUTBOX_LAG` alert report the same thing.
2. Is it queued? In the RabbitMQ management UI (http://localhost:15672), check the depth of the consumer's
   `<svc>.q.N` queues and its `<svc>.dlq`, and the bindings of `<svc>.in`. Names start with the prefix when
   `saga.messaging.prefix` is set.
3. Was it handled? In the consuming service's schema:
   `select * from <schema>.processed_message where event_id = '<eventId>';` takes the event id from step 1. A row
   means the handler committed. No row with a message in the DLQ means the handler failed. The `saga.alerts` log line
   gives the queue, event type, event id, order id and root cause.
4. Where did the order go across services? Open the trace in Jaeger (http://localhost:16686). The trace context is
   stored with the outbox row, so the relay's `saga.outbox.publish` span and the handlers in the other services belong
   to the trace that was active when the event was appended, normally the incoming HTTP request.
5. After fixing the cause, replay with `POST /api/{orders|inventory|shipping}/admin/dlq/replay?max=100`.

An order that stays PENDING without any of this is caught by `StuckOrderDetector`, which raises `SAGA_STUCK` after
`app.stuck-saga.threshold` (5 minutes by default).

## Tracing

`Outbox.append` stores the current trace context in `outbox_event.trace_context` (`OutboxTraceContext.capture`). The
relay restores it as the parent of a `saga.outbox.publish` span (`OutboxTraceContext.restore`). The `RabbitTemplate` and
the listener container have observation enabled, so the handler continues the same trace (`TracingIT`). A row with a
missing or unreadable context is published as a new trace.

The sampling probability and the OTLP endpoint are defaults shipped in `saga-messaging-defaults.yaml`.
`SagaMessagingDefaults`, registered in `META-INF/spring.factories`, adds them to every application that has this jar,
at the lowest precedence, so a service's own configuration wins. The services and this library's tests also import
`saga-messaging-tracing.yaml` through `spring.config.import`, which has the same values.
