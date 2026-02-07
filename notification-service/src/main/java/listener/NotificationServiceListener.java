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
public class NotificationServiceListener {

    private static final String TOPIC_ORDER_COMPLETED = "order.completed";
    private static final String TOPIC_ORDER_FAILED = "order.failed";
    private static final String EVENT_TYPE_FAILED = "OrderNotificationFailed";

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceListener.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ordersCompletedTotal;
    private final Counter ordersNotificationFailedTotal;

    public NotificationServiceListener(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersCompletedTotal = registry.counter("orders_completed_total");
        this.ordersNotificationFailedTotal = registry.counter("orders_notification_failed_total");
    }

    @KafkaListener(topics = "order.payment-completed", groupId = "notification-service")
    public void onOrderCompleted(String payload) {
        OrderEventEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(payload, OrderEventEnvelope.class);
            if (!isValid(envelope)) {
                ordersNotificationFailedTotal.increment();
                publishFailed(envelope, "NOTIFICATION_FAILED: orderId or payload missing");
                return;
            }
            OrderEventEnvelope completed = new OrderEventEnvelope();
            completed.setEventType("OrderCompleted");
            completed.setOrderId(envelope.getOrderId());
            completed.setCorrelationId(envelope.getCorrelationId());
            completed.setTimestamp(Instant.now().toString());
            completed.setPayload(envelope.getPayload());

            String json = objectMapper.writeValueAsString(completed);
            kafkaTemplate.send(TOPIC_ORDER_COMPLETED, completed.getOrderId(), json);
            ordersCompletedTotal.increment();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse order.payment-completed message: {}", e.getMessage());
            ordersNotificationFailedTotal.increment();
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
