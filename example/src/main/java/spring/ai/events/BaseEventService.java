package spring.ai.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import spring.ai.agents.Agent.Event;

public class BaseEventService implements EventService {

    private final Sinks.Many<Event<?>> inputSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<Event<?>> inputEventStream = inputSink.asFlux().share();
    private final Sinks.Many<Event<?>> outputSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<Event<?>> outputEventStream = outputSink.asFlux().share();

    private final ObjectReader eventReader;
    private final ObjectWriter eventWriter;

    public BaseEventService(ObjectMapper objectMapper) {
        this.eventReader = objectMapper.readerFor(Event.class);
        this.eventWriter = objectMapper.writerFor(Event.class);
    }

    @Override
    public <T> T read(String event) {
        try {
            return eventReader.readValue(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read event", e);
        }
    }

    @Override
    public String write(Event<?> event) {
        try {
            return eventWriter.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write event", e);
        }
    }

    @Override
    public Flux<Event<?>> inputStream() {
        return inputEventStream;
    }

    @Override
    public Flux<Event<?>> outputStream() {
        return outputEventStream;
    }

    @Override
    public Mono<Void> toInput(Event<?> event) {
        return Mono.fromRunnable(() -> inputSink.tryEmitNext(event).orThrow())
            .then();
    }

    @Override
    public Mono<Void> toOutput(Event<?> event) {
        return Mono.fromRunnable(() -> outputSink.tryEmitNext(event).orThrow())
            .then();
    }

    @Override
    public void destroy() throws Exception {
        inputSink.tryEmitComplete();
        outputSink.tryEmitComplete();
    }
}
