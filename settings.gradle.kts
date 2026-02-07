rootProject.name = "event-driven-order"

include(
    "order-events",
    "order-api",
    "order-validator",
    "inventory-service",
    "payment-service",
    "notification-service"
)
