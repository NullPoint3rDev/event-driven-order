package events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Common envelope for all order events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEventEnvelope {

    private String eventType;
    private String orderId;
    private String correlationId;
    private String timestamp;
    private Object payload;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
