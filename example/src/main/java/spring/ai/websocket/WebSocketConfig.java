package spring.ai.websocket;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import spring.ai.agents.AgentConfig;

@EnableWebFlux
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping handlerMapping(WebSocketHandler reactiveWebSocketHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws", reactiveWebSocketHandler);
        var mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public WebSocketHandler reactiveWebSocketHandler(ObjectMapper objectMapper, AgentConfig agentConfig,
                ChatClient.Builder chatClientBuilder, OpenAiChatOptions openAiChatOptions) {
        return new ReactiveWebSocketHandler(objectMapper, agentConfig, chatClientBuilder, openAiChatOptions);
    }
}
