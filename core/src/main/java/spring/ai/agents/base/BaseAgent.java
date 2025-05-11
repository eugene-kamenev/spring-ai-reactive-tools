package spring.ai.agents.base;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import spring.ai.agents.Agent;
import spring.ai.agents.ToolCallbackHandler;
import spring.ai.agents.Utils;
import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.agents.Agent.Event.Structured;
import spring.ai.agents.Agent.Event.Text;
import spring.ai.agents.AgentConfig.ToolCallConfig;
import spring.ai.parser.StreamingJsonParser;
import spring.ai.parser.ThinkParser;

public class BaseAgent implements Agent {

    private final ChatClient chatClient;
    private final ObjectMapper mapper;
    private final ThinkParser thinkParser = new ThinkParser();
    private final boolean injectSchema;
    private ToolCallbackHandler toolCallbackHandler;
    
    public BaseAgent(ChatClient client, ObjectMapper mapper, ToolCallConfig config) {
        this.chatClient = client;
        this.mapper = mapper;
        this.injectSchema = config.injectSchema();
        this.toolCallbackHandler = new BaseToolCallbackHandler(this, new BaseToolCallParser(mapper), mapper, config);
    }

    public BaseAgent(ChatClient client, ObjectMapper mapper) {
        this(client, mapper, ToolCallConfig.DEFAULT);
    }

    public ToolCallbackHandler getToolCallbackHandler() {
        return toolCallbackHandler;
    }

    public void setToolCallbackHandler(ToolCallbackHandler toolCallbackHandler) {
        this.toolCallbackHandler = toolCallbackHandler;
    }

    public List<Message> toMessages(List<? extends Payload> payloads, boolean includeThinking) {
        return PayloadAggregator.toMessages(payloads, toolCallbackHandler, includeThinking);
    }

    @Override
    public <T extends Payload> Flux<T> stream(Prompt prompt) {
        final Prompt toolPrompt;
        if (!(prompt instanceof PromptWithTools) && this.injectSchema) {
            toolPrompt = toolCallbackHandler.addToolsToPrompt(prompt);
        } else {
            toolPrompt = prompt;
        }
        Flux<T> stream = this.streamPayload(toolPrompt);
        return this.toolCallbackHandler.handle(stream)
            .concatMap(e -> this.handleFunctionResult(e, toolPrompt));
    }

    public <T> Flux<Structured<T>> parseJSON(Flux<? extends Payload> stream, TypeReference<T> type) {
        var parser = new StreamingJsonParser(mapper.getFactory(), mapper);
        return stream
            .filter(e -> e instanceof Text) // only text events, not thinking etc
            .handle((textEvent, sink) -> {
                parser.feedText(((Text) textEvent).text(), textEvent.isLast());
                parser.consume(node -> {
                    try {
                        sink.next(new Structured<>(mapper.treeToValue(node, type)));
                        sink.complete();
                    } catch (JsonProcessingException ignored) {}
                });
            });
    }

    @SuppressWarnings("unchecked")
    public <P extends Event.Payload> P toPayload(ChatResponse chatResponse) {
        if (chatResponse.hasToolCalls()) {
            var call = chatResponse.getResult().getOutput().getToolCalls().getFirst();
            return (P) new Event.FuncCall(call.id(), call.name(), Utils.read(mapper, call.arguments(), Utils.MAP_TYPE));
        } else {
            return (P) thinkParser.acceptChunk(Utils.getText(chatResponse), Utils.isLastChunk(chatResponse));
        }
    }

    private <T extends Payload> Flux<T> handleFunctionResult(T payload, Prompt prompt) {
        if (payload instanceof FuncResult funcResult) {
            var messages = new ArrayList<>(prompt.getInstructions());
            messages.addAll(toolCallbackHandler.getToolMessages(funcResult));
            Prompt nextPrompt = new PromptWithTools(messages, prompt.getOptions());
            return Flux.concat(Flux.just(payload), this.stream(nextPrompt));
        }
        return Flux.just(payload);
    }

    private <T extends Payload> Flux<T> streamPayload(Prompt prompt) {
        return this.chatClient
            .prompt(prompt)
            .tools(toolCallbackHandler.getToolCallbacks())
            .stream()
            .chatResponse()
            .map(this::toPayload);
    }
}
