package listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import events.OrderEventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class InventoryServiceListener {

    private static final String TOPIC_INVENTORY_RESERVED = "order.inventory-reserved";
    private static final String TOPIC_ORDER_FAILED = "order.failed";
    private static final String EVENT_TYPE_FAILED = "OrderInventoryFailed";

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceListener.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ordersReservedTotal;
    private final Counter ordersInventoryFailedTotal;

    public InventoryServiceListener(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersReservedTotal = registry.counter("orders_reserved_total");
        this.ordersInventoryFailedTotal = registry.counter("orders_inventory_failed_total");
    }

    @KafkaListener(topics = "order.validated", groupId = "inventory-reserved")
    public void onOrderValidated(String payload) {
        OrderEventEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(payload, OrderEventEnvelope.class);
            if (!isValid(envelope)) {
                ordersInventoryFailedTotal.increment();
                publishFailed(envelope, "INVENTORY_FAILED: orderId or payload missing");
                return;
            }
            OrderEventEnvelope reserved = new OrderEventEnvelope();
            reserved.setEventType("InventoryReserved");
            reserved.setOrderId(envelope.getOrderId());
            reserved.setCorrelationId(envelope.getCorrelationId());
            reserved.setTimestamp(Instant.now().toString());
            reserved.setPayload(envelope.getPayload());

            String json = objectMapper.writeValueAsString(reserved);
            kafkaTemplate.send(TOPIC_INVENTORY_RESERVED, envelope.getOrderId(), json);
            ordersReservedTotal.increment();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse order.validated message: {}", e.getMessage());
            ordersInventoryFailedTotal.increment();
            publishFailed(envelope, "PARSE_ERROR: " + e.getMessage());
        }
    }

    private void publishFailed(OrderEventEnvelope envelope, String failureReason) {
        try {
            OrderEventEnvelope failed = new OrderEventEnvelope();
            failed.setEventType(EVENT_TYPE_FAILED);
            String orderId = envelope != null ? envelope.getOrderId() : "unknown";
            String correlationId = envelope != null ? envelope.getCorrelationId() : "unknown";
            failed.setOrderId(orderId);
            failed.setCorrelationId(correlationId);
            failed.setTimestamp(Instant.now().toString());
            failed.setPayload(Map.of(
                    "originalPayload", envelope != null ? envelope.getPayload() : "n/a",
                    "failureReason", failureReason
            ));
            String json = objectMapper.writeValueAsString(failed);
            kafkaTemplate.send(TOPIC_ORDER_FAILED, orderId, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to publish failure event: {}", e.getMessage());
        }
    }

    private boolean isValid(OrderEventEnvelope envelope) {
        return envelope.getOrderId() != null && envelope.getPayload() != null;
    }
}
