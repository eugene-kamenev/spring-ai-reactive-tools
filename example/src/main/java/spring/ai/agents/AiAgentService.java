package spring.ai.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.DisposableBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spring.ai.agents.Agent.Event;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.agents.Agent.Event.UserText;
import spring.ai.agents.base.BaseAgent;
import spring.ai.agents.base.PayloadAggregator;
import spring.ai.events.EventService;
import spring.ai.events.Events;
import spring.ai.tools.terminal.TerminalService;

public class AiAgentService implements DisposableBean {

    private final BaseAgent chatAgent;
    private final BaseAgent terminalAgent;
    private final TerminalService terminalService;
    private final Disposable subscription;
    private final EventService eventService;
    private final Map<String, List<Message>> history = new HashMap<>();

    public AiAgentService(ObjectMapper objectMapper, AgentConfig promptConfig,
                          ChatClient.Builder builder, EventService eventService) {
        this.eventService = eventService;
        this.terminalService = new TerminalService(eventService);
        this.chatAgent = new BaseAgent(builder.build(), objectMapper);
        this.terminalAgent = new ToolsAgent(builder.build(), eventService, objectMapper, terminalService, promptConfig);
        this.subscription = eventService.inputStream()
            .flatMap(this::onEvent)
            .subscribe();
    }

    private <T extends Payload> Mono<Void> onEvent(Event<?> event) {
        var responseId = UUID.randomUUID().toString();
        var payloads = new ArrayList<T>();
        if (event.entity() instanceof UserText u) {
            var agent = (String) event.metadata().get("agent");
            var historyList = history.computeIfAbsent(agent, k -> new ArrayList<>());
            PayloadAggregator.addMessage(historyList, new UserMessage(u.text()));
            var prompt = new Prompt(historyList);
            Flux<T> events = null;
            switch(agent) {
                case "terminal-agent":
                    events = terminalAgent.stream(prompt);
                    break;
                default:
                    events = chatAgent.stream(prompt);
                    break;
            }
            return PayloadAggregator.aggregatePayloads(events, payloads::addAll)
                .flatMap(payload -> this.sendEvent(responseId, payload))
                .doOnComplete(() -> {
                    historyList.addAll(terminalAgent.toMessages(payloads, false));
                })
                .then();
        } else if (event.entity() instanceof Events.Terminal t) {
            if (t.columns() != null && t.rows() != null) {
                terminalService.onTerminalReady(t.columns(), t.rows());
            } else if (t.output() != null) {
                return terminalService.onCommand(t.output());
            }
        }
        return Mono.empty();
    }

    private Mono<Void> sendEvent(String responseId, Payload event) {
        return eventService.toOutput(new Event<>(responseId, Map.of(), event))
            .then();
    }

    @Override
    public void destroy() throws Exception {
        if (subscription != null) {
            subscription.dispose();
        }
        if (terminalService != null) {
            terminalService.destroy();
        }
    }
}
