package spring.ai.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import spring.ai.agents.Agent.Event;
import spring.ai.schema.ObjectSchemaGenerator;
import spring.ai.schema.SchemaFormat;

public final class Utils {
    private Utils() {
        // Prevent instantiation
    }

    public static final Event.Text EMP_TEXT = new Event.Text(null, false);
    public static final Event.Thinking EMP_THINK = new Event.Thinking(null, false);
    public static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public static Event.Text newTextEvent(String text, boolean isLast) {
        if (!isLast && (text == null || text.isEmpty())) {
            return Utils.EMP_TEXT;
        }
        return new Event.Text(text, isLast);
    }

    public static Event.Thinking newThinkingEvent(String text, boolean isLast) {
        if (!isLast && (text == null || text.isEmpty())) {
            return Utils.EMP_THINK;
        }
        return new Event.Thinking(text, isLast);
    }

    public static boolean isToolResult(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
            .anyMatch(g -> ToolExecutionResult.FINISH_REASON.equals(g.getMetadata().getFinishReason()));
    }

    public static String getText(ChatResponse chatResponse) {
        return chatResponse.getResults().stream()
            .map(g -> g.getOutput().getText())
            .filter(Objects::nonNull)
            .collect(Collectors.joining());
    }

    public static boolean isLastChunk(ChatResponse chatResponse) {
        return StringUtils.hasText(chatResponse.getResults().getLast().getMetadata().getFinishReason()) ||
            chatResponse.getMetadata().getUsage().getTotalTokens() > 0;
    }

    public static String buildToolDefinition(ToolDefinition toolDefinition, ObjectMapper mapper, SchemaFormat format) {
        var inputSchema = toolDefinition.inputSchema();
        var formatted = " {\n   \"type\": \"function\",\n   \"name\": \"%s\",\n   \"description\": \"%s\",\n   \"parameters\": %s\n }";
        if (format == SchemaFormat.YAML) {
            formatted = "tool_name: %s\ndescription: %s\narguments:%s";
            inputSchema = ObjectSchemaGenerator.generateYamlSchema(readTree(mapper, inputSchema));
            inputSchema = inputSchema.substring(7, inputSchema.length() - 4).replaceAll("\n", "\n  ");
        } else if (format == SchemaFormat.JSON) {
            formatted = " {\n   tool_name: %s,\n   description: %s,\n   arguments: %s\n }";
            inputSchema = ObjectSchemaGenerator.generateSimpleJsonSchema(readTree(mapper, inputSchema));
            inputSchema = inputSchema.substring(7, inputSchema.length() - 4).trim().replaceAll("\n", "\n   ");
        } else {
            inputSchema = inputSchema.replaceAll("\n", "\n   ");
        }
        return String.format(formatted, toolDefinition.name(), toolDefinition.description(), inputSchema);
    }

    public static String buildToolDefinitions(ToolCallback[] callbacks, ObjectMapper mapper, SchemaFormat format) {
        var toFormat = format == null ? SchemaFormat.YAML : format;
        return Arrays.stream(callbacks)
            .map(t -> buildToolDefinition(t.getToolDefinition(), mapper, toFormat))
            .collect(toFormat.collector());
    }

    public static <T> T read(ObjectMapper mapper, String json, TypeReference<T> valueType) {
        try {
            return mapper.readValue(json, valueType);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    public static JsonNode readTree(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    public static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    public static final String DELIMITER = "\n---\n";

    public static Message join(Message lastMessage, Message nextMessage) {
        if (lastMessage instanceof AssistantMessage last && nextMessage instanceof AssistantMessage next) {
            return new AssistantMessage(String.join(DELIMITER, last.getText(), next.getText()), next.getMetadata(), next.getToolCalls());
        } else if (lastMessage instanceof ToolResponseMessage last && nextMessage instanceof ToolResponseMessage next) {
            var toolResponses = new ArrayList<ToolResponse>(last.getResponses());
            toolResponses.addAll(next.getResponses());
            return new ToolResponseMessage(toolResponses, next.getMetadata());
        } else if (lastMessage instanceof SystemMessage last && nextMessage instanceof SystemMessage next) {
            return new SystemMessage(String.join(DELIMITER, last.getText(), next.getText()));
        } else if (lastMessage instanceof UserMessage last && nextMessage instanceof UserMessage next) {
            var media = new ArrayList<Media>(last.getMedia());
            media.addAll(next.getMedia());
            return new UserMessage(String.join(DELIMITER, last.getText(), next.getText()), media, next.getMetadata());
        } else {
            throw new IllegalArgumentException("Cannot merge messages of type " + lastMessage.getClass().getName() + " and "+ nextMessage.getClass().getName());
        }
    }
    
}
