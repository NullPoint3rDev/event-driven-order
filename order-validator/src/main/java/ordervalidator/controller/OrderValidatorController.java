package ordervalidator.controller;

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
public class OrderValidatorController {

    private static final String TOPIC_ORDER_VALIDATED = "order.validated";
    private static final String TOPIC_ORDER_FAILED = "order.failed";
    private static final String EVENT_TYPE_FAILED = "OrderValidationFailed";

    private static final Logger log = LoggerFactory.getLogger(OrderValidatorController.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ordersValidatedTotal;
    private final Counter ordersValidationFailedTotal;

    public OrderValidatorController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersValidatedTotal = registry.counter("orders_validated_total");
        this.ordersValidationFailedTotal = registry.counter("orders_validation_failed_total");
    }

    @KafkaListener(topics = "order.created", groupId = "order-validator")
    public void onOrderCreated(String payload) {
        OrderEventEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(payload, OrderEventEnvelope.class);
            if (!isValid(envelope)) {
                ordersValidationFailedTotal.increment();
                publishFailed(envelope, "VALIDATION_FAILED: orderId or payload missing");
                return;
            }
            OrderEventEnvelope validated = new OrderEventEnvelope();
            validated.setEventType("OrderValidated");
            validated.setOrderId(envelope.getOrderId());
            validated.setCorrelationId(envelope.getCorrelationId());
            validated.setTimestamp(Instant.now().toString());
            validated.setPayload(envelope.getPayload());

            String json = objectMapper.writeValueAsString(validated);
            kafkaTemplate.send(TOPIC_ORDER_VALIDATED, envelope.getOrderId(), json);
            ordersValidatedTotal.increment();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse order.created message: {}", e.getMessage());
            ordersValidationFailedTotal.increment();
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
        if (envelope.getOrderId() == null || envelope.getPayload() == null) {
            return false;
        }
        // extend: check payload.customerId, items, totalAmount
        return true;
    }
}
