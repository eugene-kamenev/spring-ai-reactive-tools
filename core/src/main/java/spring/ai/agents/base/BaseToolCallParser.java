package spring.ai.agents.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import spring.ai.agents.ToolCallParser;
import spring.ai.agents.Utils;
import spring.ai.agents.Agent.Event.FuncCall;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.agents.Agent.Event.Text;
import spring.ai.parser.StreamingJsonParser;;

public class BaseToolCallParser implements ToolCallParser {

    private final ObjectMapper mapper;

    public BaseToolCallParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Payload> Flux<T> parse(Flux<T> stream, Set<String> validToolNames) {
        var toolCalls = new ArrayList<FuncCall>(1);
        var parser = new StreamingJsonParser(mapper.getFactory(), mapper);
        parser.consume(node -> parseToolCall(parser, node, toolCalls, validToolNames));
        return (Flux<T>) stream.map(payload -> {
            if (payload instanceof Text text) {
                var outText = parser.feedText(text.text() == null ? "" : text.text(), text.isLast());
                return toolCalls.size() > 0 ? toolCalls.getFirst() : Utils.newTextEvent(outText, text.isLast());
            }
            return payload;
        });
    }

    private void parseToolCall(StreamingJsonParser handler, JsonNode node, List<FuncCall> calls,
            Set<String> validFunctionNames) {
        if (node.isArray())
            node.forEach(n -> parseToolCall(handler, n, calls, validFunctionNames));
        else if (isFunctionCall(node, validFunctionNames) && !handler.isStopped()) {
            try {
                calls.add(new FuncCall(
                        UUID.randomUUID().toString(),
                        node.get("name").asText(),
                        Utils.read(mapper, node.get("arguments").toString(), Utils.MAP_TYPE)
                    )
                );
                handler.shouldStop();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isFunctionCall(JsonNode node, Set<String> validFunctionNames) {
        return node.has("name") && node.has("arguments") && validFunctionNames.contains(node.get("name").asText());
    }
}
