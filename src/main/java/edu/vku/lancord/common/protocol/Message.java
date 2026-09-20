package edu.vku.lancord.common.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public class Message {
    private MessageType type;
    private JsonNode payload; // Can be parsed into specific DTOs using Jackson

    public Message() {}

    public Message(MessageType type, JsonNode payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public JsonNode getPayload() { return payload; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
}
