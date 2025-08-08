package spring.ai.agents.base;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spring.ai.agents.ToolCallbackHandler;
import spring.ai.agents.Utils;
import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.agents.Agent.Event.Structured;
import spring.ai.agents.Agent.Event.Text;
import spring.ai.agents.Agent.Event.Thinking;
import spring.ai.agents.Agent.Event.WithText;

public class PayloadAggregator<T extends Payload> implements Consumer<T>, Supplier<List<T>> {
    private final StringBuilder builder = new StringBuilder();
    private final List<T> payloads = new ArrayList<>();
    private T lastEvent = null;
    private boolean consumed = false;

    @Override
    public void accept(T payload) {
        if (payload instanceof WithText textPayload && !textPayload.hasText()) {
            return;
        }
        if (payload instanceof WithText textPayload) {
            var text = textPayload.text();
            if (lastEvent instanceof WithText && lastEvent.getClass().equals(payload.getClass())) {
                appendText(text, builder);
            } else {
                if (lastEvent instanceof WithText && !builder.isEmpty()) {
                    addAggregate(payloads, (WithText) lastEvent, builder);
                    builder.setLength(0);
                }
                appendText(text, builder);
            }
        } else {
            if (lastEvent instanceof WithText && !builder.isEmpty()) {
                addAggregate(payloads, (WithText) lastEvent, builder);
                builder.setLength(0);
            }
            payloads.add(payload);
        }
        lastEvent = payload;
    }

    public List<T> get() {
        if (!consumed) {
            if (lastEvent != null && lastEvent instanceof WithText && !builder.isEmpty()) {
                addAggregate(payloads, (WithText) lastEvent, builder);
            }
            builder.setLength(0);
            lastEvent = null;
            consumed = true;
        }
        return payloads;
    }

    public List<Message> getMessages(ToolCallbackHandler handler, boolean includeThinking) {
        return PayloadAggregator.toMessages(this.get(), handler, includeThinking);
    }

    public static <T extends Payload> Mono<List<Message>> aggregatePayloads(Flux<T> stream, ToolCallbackHandler handler,
                                                                            boolean includeThinking) {
        var payloads = new ArrayList<Payload>();
        return aggregatePayloads(stream, payloads::addAll)
                .then(Mono.defer(() -> Mono.just(toMessages(payloads, handler, includeThinking))));
    }

    public static <T extends Payload> Flux<T> aggregatePayloads(Flux<T> stream, Consumer<List<T>> consumer) {
        var aggregator = new PayloadAggregator<T>();
        return stream
                .doOnNext(aggregator)
                .doOnComplete(() -> consumer.accept(aggregator.get()));
    }

    private static void appendText(String text, StringBuilder builder) {
        if (text != null && !text.isEmpty()) {
            builder.append(text);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Payload> void addAggregate(List<T> payloads, WithText payload, StringBuilder aggregate) {
        var text = aggregate.toString();
        if (text.isBlank()) {
            return;
        }
        if (payload instanceof Text) {
            payloads.add((T) new Text(aggregate.toString(), true));
        } else if (payload instanceof Thinking) {
            payloads.add((T) new Thinking(aggregate.toString(), true));
        } else {
            throw new IllegalArgumentException("Unsupported payload type: " + payload.getClass());
        }
    }

    public static List<Message> toMessages(List<? extends Payload> payloads, ToolCallbackHandler handler,
                                           boolean includeThinking) {
        List<Message> messages = new ArrayList<>();
        var buffer = new StringBuilder();

        for (Payload payload : payloads) {
            if (includeThinking && payload instanceof Thinking thinking) {
                buffer.append("<think>\n").append(thinking.text()).append("\n</think>\n\n");
            } else if (payload instanceof Structured<?> struct) {
                // TODO: what about structured payloads?
            } else if (payload instanceof Text text) {
                buffer.append(text.text()).append("\n");
                addMessage(messages, new AssistantMessage(buffer.toString()));
                buffer.setLength(0);
            } else if (payload instanceof FuncResult func) {
                buffer.setLength(0);
                for (var message : handler.getToolMessages(func)) {
                    addMessage(messages, message);
                }
            }
        }
        if (!buffer.isEmpty()) {
            addMessage(messages, new AssistantMessage(buffer.toString()));
        }
        return messages;
    }

    public static void addMessage(List<Message> existing, Message message) {
        if (message == null) {
            return;
        }
        Message last = null;
        if (!existing.isEmpty()) {
            last = existing.getLast();
        }
        if (last != null && last.getMessageType() == message.getMessageType()) {
            last = existing.removeLast();
            message = Utils.join(last, message);
        }
        existing.add(message);
    }
}
