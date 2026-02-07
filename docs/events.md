# Event-Driven Order Workflow — Contracts

This document defines events, Kafka topics, and message formats for the order workflow.

---

## Events Overview

| Event             | Published by | Consumed by              | Purpose                                      |
|-------------------|-------------|--------------------------|----------------------------------------------|
| **OrderCreated**   | order-api   | validator                | Order created; needs validation               |
| **OrderValidated**| validator   | inventory                | Order validated; can reserve stock            |
| **InventoryReserved** | inventory | payment              | Stock reserved; can charge customer           |
| **PaymentCompleted**  | payment   | notification          | Payment successful; send notification          |
| **OrderCompleted**    | notification | order-api / analytics | Order fully completed                         |
| **OrderValidationFailed** | validator | (DLQ / monitoring) | Validation failed; order rejected |
| **OrderInventoryFailed**  | inventory  | (DLQ / monitoring) | Reserve failed |
| **OrderPaymentFailed**    | payment    | (DLQ / monitoring) | Payment failed |
| **OrderNotificationFailed** | notification | (DLQ / monitoring) | Notification failed |

---

## Kafka Topics

| Event               | Topic                    |
|---------------------|--------------------------|
| OrderCreated        | `order.created`          |
| OrderValidated      | `order.validated`        |
| InventoryReserved   | `order.inventory-reserved` |
| PaymentCompleted    | `order.payment-completed` |
| OrderCompleted      | `order.completed`        |
| All failures        | `order.failed`           |

---

## Message Format (Body Contract)

Every event has the same envelope: `eventType`, `orderId`, `correlationId`, `timestamp`, and `payload`. The payload can be extended by each service (e.g. add `reservationId` in InventoryReserved, `paymentId` in PaymentCompleted). Below is the **minimum** contract; services may add fields for their consumers.

### OrderCreated

```json
{
  "eventType": "OrderCreated",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "customerId": "cust-1",
    "items": [{"sku": "ITEM-A", "quantity": 2}],
    "totalAmount": 99.98
  }
}
```

### OrderValidated

```json
{
  "eventType": "OrderValidated",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "customerId": "cust-1",
    "items": [{"sku": "ITEM-A", "quantity": 2}],
    "totalAmount": 99.98
  }
}
```

### InventoryReserved

```json
{
  "eventType": "InventoryReserved",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "customerId": "cust-1",
    "items": [{"sku": "ITEM-A", "quantity": 2}],
    "totalAmount": 99.98
  }
}
```

### PaymentCompleted

```json
{
  "eventType": "PaymentCompleted",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "customerId": "cust-1",
    "items": [{"sku": "ITEM-A", "quantity": 2}],
    "totalAmount": 99.98
  }
}
```

### OrderCompleted

```json
{
  "eventType": "OrderCompleted",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "customerId": "cust-1",
    "items": [{"sku": "ITEM-A", "quantity": 2}],
    "totalAmount": 99.98
  }
}
```

---

## Failure Events (order.failed)

When validation fails or an exception occurs, the service publishes to **order.failed** with the same envelope. `eventType` is one of: `OrderValidationFailed`, `OrderInventoryFailed`, `OrderPaymentFailed`, `OrderNotificationFailed`. `payload` should include the original payload (if any) and a `failureReason` string for debugging.

```json
{
  "eventType": "OrderValidationFailed",
  "orderId": "ord-123",
  "correlationId": "corr-456",
  "timestamp": "2026-02-06T12:00:00Z",
  "payload": {
    "originalPayload": { ... },
    "failureReason": "VALIDATION_FAILED: missing customerId"
  }
}
```

---

## Idempotency

Consumers should process by `orderId`: store or update state keyed by `orderId` so that reprocessing the same event (e.g. after consumer restart) does not change the outcome.
