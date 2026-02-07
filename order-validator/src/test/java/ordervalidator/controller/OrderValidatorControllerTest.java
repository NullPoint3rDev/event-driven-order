package ordervalidator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderValidatorControllerTest {

    private static final String VALID_ORDER_CREATED = """
            {"eventType":"OrderCreated","orderId":"ord-123","correlationId":"corr-456","timestamp":"2026-02-06T12:00:00Z","payload":{"customerId":"cust-1","items":[],"totalAmount":9999}}
            """;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private ObjectMapper objectMapper;
    private OrderValidatorController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        controller = new OrderValidatorController(kafkaTemplate, objectMapper, meterRegistry);
    }

    @Test
    void validOrder_publishesToOrderValidated() {
        controller.onOrderCreated(VALID_ORDER_CREATED);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.validated"), eq("ord-123"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderValidated\"");
        assertThat(valueCaptor.getValue()).contains("\"orderId\":\"ord-123\"");
    }

    @Test
    void invalidEnvelope_missingOrderId_publishesToOrderFailed() {
        String json = "{\"eventType\":\"OrderCreated\",\"payload\":{\"customerId\":\"c1\"}}";

        controller.onOrderCreated(json);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderValidationFailed\"");
        assertThat(valueCaptor.getValue()).contains("failureReason");
        assertThat(valueCaptor.getValue()).contains("VALIDATION_FAILED");
    }

    @Test
    void parseError_publishesToOrderFailed() {
        controller.onOrderCreated("not valid json");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderValidationFailed\"");
        assertThat(valueCaptor.getValue()).contains("PARSE_ERROR");
    }
}
