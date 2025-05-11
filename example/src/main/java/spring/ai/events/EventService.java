package spring.ai.events;

import org.springframework.beans.factory.DisposableBean;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spring.ai.agents.Agent.Event;

public interface EventService extends DisposableBean {
    <T> T read(String event);
    String write(Event<?> event);
    Mono<Void> toInput(Event<?> event);
    Mono<Void> toOutput(Event<?> event);
    Flux<Event<?>> outputStream();
    Flux<Event<?>> inputStream();
}
