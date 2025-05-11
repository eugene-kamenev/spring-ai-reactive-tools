package spring.ai.events;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import spring.ai.agents.Agent.Event;
import spring.ai.agents.Agent.Event.Payload;

public final class Events {

    public record UserAnswer(String text) implements Payload {}

    public record Terminal(String output, Integer rows, Integer columns) implements Payload {
        public Terminal(String output) {
            this(output, null, null);
        }
    }

    public static void register(ObjectMapper mapper) {
        mapper.registerSubtypes(
            new NamedType(Terminal.class, "TERMINAL"),
            new NamedType(UserAnswer.class, "USER_CHOICE")
        );
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        register(mapper);
        Event<Event.Text> event = new Event<>("id", Map.of(), new Event.Text("Hello", false));
        String json = mapper.writeValueAsString(event);
        System.out.println("Serialized: " + json);

        Event<?> deserializedEvent = mapper.readValue(json, Event.class);
        System.out.println("Deserialized: " + deserializedEvent);
        System.out.println("Entity type: " + deserializedEvent.entity().getClass());
    }
    
    private Events() {}
}
