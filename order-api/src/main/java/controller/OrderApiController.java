package controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.CreateOrderRequestDTO;
import events.OrderEventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orderApi")
public class OrderApiController {

    private static final String TOPIC_ORDER_CREATED = "order.created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter ordersCreatedTotal;

    public OrderApiController(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersCreatedTotal = registry.counter("orders_created_total");
    }

    @PostMapping("/orders")
    public void createOrder(@RequestBody CreateOrderRequestDTO request) throws JsonProcessingException {
        String orderId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();

        OrderEventEnvelope envelope = new OrderEventEnvelope();
        envelope.setEventType("OrderCreated");
        envelope.setOrderId(orderId);
        envelope.setCorrelationId(correlationId);
        envelope.setTimestamp(timestamp);
        envelope.setPayload(request);

        String json = objectMapper.writeValueAsString(envelope);
        kafkaTemplate.send(TOPIC_ORDER_CREATED, orderId, json);
        ordersCreatedTotal.increment();
    }
}
