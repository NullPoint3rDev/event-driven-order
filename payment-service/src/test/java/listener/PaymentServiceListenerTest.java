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
class PaymentServiceListenerTest {

    private static final String VALID_ORDER_INVENTORY_RESERVED = """
            {"eventType":"InventoryReserved","orderId":"ord-123","correlationId":"corr-456","timestamp":"2026-02-06T12:00:00Z","payload":{"customerId":"cust-1","items":[],"totalAmount":9999}}
            """;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private ObjectMapper objectMapper;
    private PaymentServiceListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        listener = new PaymentServiceListener(kafkaTemplate, objectMapper, meterRegistry);
    }

    @Test
    void validOrder_publishesToOrderPaymentCompleted() {
        listener.onOrderPaid(VALID_ORDER_INVENTORY_RESERVED);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.payment-completed"), eq("ord-123"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"PaymentCompleted\"");
        assertThat(valueCaptor.getValue()).contains("\"orderId\":\"ord-123\"");
    }

    @Test
    void invalidEnvelope_publishesToOrderFailed() {
        listener.onOrderPaid("{\"eventType\":\"InventoryReserved\",\"orderId\":null,\"payload\":{}}");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderPaymentFailed\"");
        assertThat(valueCaptor.getValue()).contains("PAYMENT_FAILED");
    }

    @Test
    void parseError_publishesToOrderFailed() {
        listener.onOrderPaid("not json");

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order.failed"), eq("unknown"), valueCaptor.capture());

        assertThat(valueCaptor.getValue()).contains("\"eventType\":\"OrderPaymentFailed\"");
        assertThat(valueCaptor.getValue()).contains("PARSE_ERROR");
    }
}
