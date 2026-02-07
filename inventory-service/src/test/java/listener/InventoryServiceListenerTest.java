package listener;

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
class InventoryServiceListenerTest {

    private static final String VALID_ORDER_VALIDATED = """
            {"eventType":"OrderValidated","orderId":"ord-123","correlationId":"corr-456","timestamp":"2026-02-06T12:00:00Z","payload":{"customerId":"cust-1","items":[],"totalAmount":9999}}
            """;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private ObjectMapper objectMapper;
    private InventoryServiceListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        listener = new InventoryServiceListener(kafkaTemplate, objectMapper, meterRegistry);
    }

    @Test
    void validOrder_publishesToOrderInventoryReserved() {
        listener.onOrderValidated(VALID_ORDER_VALIDATED);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.inventory-reserved"), eq("ord-123"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"InventoryReserved\"");
        assertThat(valueCaptor.getValue()).contains("\"orderId\":\"ord-123\"");
    }

    @Test
    void invalidEnvelope_publishesToOrderFailed() {
        listener.onOrderValidated("{\"eventType\":\"OrderValidated\",\"payload\":null}");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderInventoryFailed\"");
        assertThat(valueCaptor.getValue()).contains("INVENTORY_FAILED");
    }

    @Test
    void parseError_publishesToOrderFailed() {
        listener.onOrderValidated("not json");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderInventoryFailed\"");
        assertThat(valueCaptor.getValue()).contains("PARSE_ERROR");
    }
}
