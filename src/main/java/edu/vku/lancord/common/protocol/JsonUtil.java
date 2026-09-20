package edu.vku.lancord.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String serialize(Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    public static Message deserialize(String json) throws JsonProcessingException {
        return mapper.readValue(json, Message.class);
    }

    public static JsonNode valueToTree(Object obj) {
        return mapper.valueToTree(obj);
    }

    public static <T> T treeToValue(JsonNode node, Class<T> clazz) throws JsonProcessingException {
        return mapper.treeToValue(node, clazz);
    }

    public static <T> T treeToValue(JsonNode node, TypeReference<T> typeRef) {
        return mapper.convertValue(node, typeRef);
    }
}
