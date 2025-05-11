package spring.ai.agents.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import spring.ai.agents.ToolCallParser;
import spring.ai.agents.ToolCallbackHandler;
import spring.ai.agents.Utils;
import spring.ai.agents.Agent.Event.FuncCall;
import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.agents.Agent.Event.Text;
import spring.ai.agents.AgentConfig.ToolCallConfig;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.tools.ReactiveMethodToolCallback;
import spring.ai.tools.ReactiveToolCallbacks;

public class BaseToolCallbackHandler implements ToolCallbackHandler {

    private static final ToolCallback[] EMPTY_TOOL_CALLBACKS = new ToolCallback[0];

    private final ReactiveMethodToolCallback[] toolCallbacks;
    private final String toolSchema;
    private final Map<String, ReactiveMethodToolCallback> toolCallbackMap = new HashMap<>();
    private final ObjectMapper mapper;
    private final ToolCallParser toolCallParser;
    private final ToolCallConfig config;

    public BaseToolCallbackHandler(Object toolService, ToolCallParser toolCallParser, ObjectMapper mapper, ToolCallConfig config) {
        this.config = config;
        this.mapper = mapper;
        this.toolCallParser = toolCallParser;
        this.toolCallbacks = ReactiveToolCallbacks.from(toolService);
        this.toolSchema = Utils.buildToolDefinitions(toolCallbacks, mapper, config.schemaFormat());
        for (var toolCallback : this.toolCallbacks) {
            this.toolCallbackMap.put(toolCallback.getToolDefinition().name(), toolCallback);
        }
    }

    public <T extends Payload> Flux<T> handle(Flux<T> stream) {
        if (toolCallbacks.length == 0) {
            return stream;
        }
        if (this.toolCallParser != null) {
            stream = this.toolCallParser.parse(stream, toolCallbackMap.keySet())
                .takeUntil(e -> e instanceof FuncCall); // stop on first tool call
        }
        return stream.expand(this::handleToolCalls);
    }

    @Override
	public List<Message> getToolMessages(FuncResult func) {
        return config.toolResponse(func, mapper);
	}

	@Override
	public Prompt addToolsToPrompt(Prompt prompt) {
        if (prompt instanceof PromptWithTools || config.isProviderEngine() || toolCallbacks.length == 0) {
            return prompt;
        }
        var messages = new ArrayList<>(prompt.getInstructions());
        var toolsMessage = config.toolsPrompt(toolSchema);
        
        int existingMessageIndex = -1;
        Message firstMessage = null;
        for (int i = 0; i < messages.size(); i++) {
            Message currentMessage = messages.get(i);
            if (currentMessage.getMessageType().equals(toolsMessage.getMessageType())) {
                firstMessage = currentMessage;
                existingMessageIndex = i;
                break;
            }
        }
        if (firstMessage != null) {
            messages.set(existingMessageIndex, Utils.join(toolsMessage, firstMessage));
        } else {
            messages.add(0, toolsMessage);
        }
		return new PromptWithTools(messages, prompt.getOptions());
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		return toolCallbacks.length > 0 && config.isProviderEngine() ? toolCallbacks : EMPTY_TOOL_CALLBACKS;
	}

	@SuppressWarnings("unchecked")
    protected <T extends Payload> Flux<T> toolNotFoundFallback(FuncCall toolWanted) {
        // write back as text message
        return (Flux<T>) Flux.just(new Text(Utils.write(mapper, toolWanted), true));
    }

    @SuppressWarnings("unchecked")
    private <T extends Payload> Flux<T> handleToolCalls(T payload) {
        if (payload instanceof FuncCall toolWanted) {
            var toolCallback = toolCallbackMap.get(toolWanted.name());
            if (toolCallback == null) {
                return toolNotFoundFallback(toolWanted);
            }
            return toolCallback.callNonBlocking(Utils.write(mapper, toolWanted.arguments()))
                .map(result -> (T) new FuncResult(toolWanted, result))
                .flux();
        }
        return Flux.empty();
    }

    
}
