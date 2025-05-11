package spring.ai.agents;

import java.util.Map;

import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import reactor.core.publisher.Flux;

public interface Agent {

    <T extends Event.Payload> Flux<T> stream(Prompt prompt);

    public record Event<T extends Event.Payload>(String id, Map<String, Object> metadata, T entity) {
        @JsonSubTypes({
                @JsonSubTypes.Type(value = Event.Text.class, name = "TXT"),
                @JsonSubTypes.Type(value = Event.UserText.class, name = "USR"),
                @JsonSubTypes.Type(value = Event.FuncCall.class, name = "FC"),
                @JsonSubTypes.Type(value = Event.FuncResult.class, name = "FR"),
                @JsonSubTypes.Type(value = Event.Thinking.class, name = "TH")
        })
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
        public interface Payload {
            default boolean isLast() {
                return true;
            }
        }

        public interface WithText extends Payload {
            String text();

            default boolean hasText() {
                return text() != null && !text().isEmpty();
            }
        }

        public record Text(String text, boolean isLast) implements WithText {
        }

        public record Thinking(String text, boolean isLast) implements WithText {
        }

        public record UserText(String text) implements WithText {
        }

        public record FuncCall(String id, String name, Map<String, Object> arguments) implements Payload {
        }

        public record FuncResult(FuncCall call, String result) implements Payload {
        }

        public record Structured<T>(T data) implements Payload {
        }
    }
}
