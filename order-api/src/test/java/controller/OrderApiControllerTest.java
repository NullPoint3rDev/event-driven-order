package controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.CreateOrderRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderApiController.class)
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
class OrderApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void createOrder_sendsOrderCreatedToKafka() throws Exception {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setCustomerId("cust-1");
        request.setTotalAmount(9999L);

        mockMvc.perform(post("/api/v1/orderApi/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(kafkaTemplate, times(1)).send(eq("order.created"), anyString(), anyString());
    }
}
