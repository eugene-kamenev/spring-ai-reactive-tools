package spring.ai.agents;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import reactor.core.publisher.Flux;
import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.agents.Agent.Event.Payload;

public interface ToolCallbackHandler {
    /**
     * Detects tool calls in the stream and handles them.
     */
    <T extends Payload> Flux<T> handle(Flux<T> stream);
    /**
     * Returns the tool callbacks that are used to handle tool calls.
     */
    ToolCallback[] getToolCallbacks();
    /**
     * Returns tool messages which are appended to the prompt after tool call
     */
    List<Message> getToolMessages(FuncResult func);
    /**
     * Adds tool schema to the prompt
     */
    Prompt addToolsToPrompt(Prompt prompt);
}
