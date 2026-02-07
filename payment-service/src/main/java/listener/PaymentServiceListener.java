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
public class PaymentServiceListener {

    private static final String TOPIC_PAYMENT_COMPLETED = "order.payment-completed";
    private static final String TOPIC_ORDER_FAILED = "order.failed";
    private static final String EVENT_TYPE_FAILED = "OrderPaymentFailed";

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceListener.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ordersPaidTotal;
    private final Counter ordersPaymentFailedTotal;

    public PaymentServiceListener(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersPaidTotal = registry.counter("orders_paid_total");
        this.ordersPaymentFailedTotal = registry.counter("orders_payment_failed_total");
    }

    @KafkaListener(topics = "order.inventory-reserved", groupId = "payment-completed")
    public void onOrderPaid(String payload) {
        OrderEventEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(payload, OrderEventEnvelope.class);
            if (!isValid(envelope)) {
                ordersPaymentFailedTotal.increment();
                publishFailed(envelope, "PAYMENT_FAILED: orderId or payload missing");
                return;
            }
            OrderEventEnvelope paid = new OrderEventEnvelope();
            paid.setEventType("PaymentCompleted");
            paid.setOrderId(envelope.getOrderId());
            paid.setCorrelationId(envelope.getCorrelationId());
            paid.setTimestamp(Instant.now().toString());
            paid.setPayload(envelope.getPayload());

            String json = objectMapper.writeValueAsString(paid);
            kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, paid.getOrderId(), json);
            ordersPaidTotal.increment();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse order.inventory-reserved message: {}", e.getMessage());
            ordersPaymentFailedTotal.increment();
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
