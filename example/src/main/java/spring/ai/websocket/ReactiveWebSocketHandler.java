package spring.ai.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.core.util.StringUtil;
import reactor.core.publisher.Mono;
import spring.ai.agents.AgentConfig;
import spring.ai.agents.AiAgentService;
import spring.ai.agents.Agent.Event;
import spring.ai.agents.Agent.Event.WithText;
import spring.ai.events.BaseEventService;
import spring.ai.events.EventService;

public class ReactiveWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ReactiveWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final AgentConfig agentConfig;
    private final ChatClient.Builder chatClientBuilder;

    public ReactiveWebSocketHandler(ObjectMapper objectMapper, AgentConfig agentConfig,
            ChatClient.Builder chatClientBuilder, OpenAiChatOptions openAiChatOptions) {
        this.objectMapper = objectMapper;
        this.agentConfig = agentConfig;
        this.chatClientBuilder = chatClientBuilder.defaultOptions(openAiChatOptions);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Create per-session instances
        EventService eventService = new BaseEventService(objectMapper);
        AiAgentService agentService = new AiAgentService(objectMapper, agentConfig, chatClientBuilder, eventService);

        // Handle incoming messages
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> eventService.toInput(eventService.read(payload)))
                .then();

        // Handle outgoing messages
        Mono<Void> output = session.send(
                eventService.outputStream()
                        .filter(evt -> {
                            if (evt.entity() instanceof WithText e && (!e.isLast() && (e.text() == null || e.text().isEmpty()))) {
                                return false;
                            }
                            return true;
                        })
                        .map(event -> session.textMessage(eventService.write(event))));

        // Combine input and output handling, ensuring cleanup
        return Mono.zip(input, output)
                .doFinally(signalType -> {
                    log.info("WebSocket session {} closed ({})", session.getId(), signalType);
                    try {
                        // Clean up
                        agentService.destroy();
                        eventService.destroy();
                    } catch (Exception e) {
                        log.error("Error during resource cleanup for session {}", session.getId(), e);
                    }
                })
                .then();
    }
}
