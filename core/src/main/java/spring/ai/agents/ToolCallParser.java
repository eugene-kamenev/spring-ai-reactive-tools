package spring.ai.agents;

import java.util.Set;

import reactor.core.publisher.Flux;
import spring.ai.agents.Agent.Event.Payload;

public interface ToolCallParser {
    public <T extends Payload> Flux<T> parse(Flux<T> stream, Set<String> validToolNames);
}
