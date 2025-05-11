package spring.ai.agents.base;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import reactor.core.publisher.Flux;
import spring.ai.agents.ToolCallbackHandler;
import spring.ai.agents.Utils;
import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.agents.Agent.Event.Payload;
import spring.ai.agents.Agent.Event.Structured;
import spring.ai.agents.Agent.Event.Text;
import spring.ai.agents.Agent.Event.Thinking;
import spring.ai.agents.Agent.Event.WithText;

public final class PayloadAggregator {

    private PayloadAggregator() {
        // Prevent instantiation
    }

    public static <T extends Payload> Flux<T> aggregatePayloads(Flux<T> stream, Consumer<List<T>> consumer) {
        var messageTextContentRef = new AtomicReference<StringBuilder>(new StringBuilder());
        var aggregatePayloadsRef = new AtomicReference<List<T>>(new ArrayList<>());
        var lastPayloadRef = new AtomicReference<T>(null);
        return stream.doOnNext(payload -> {
            if (payload instanceof WithText textPayload && !textPayload.hasText()) {
                return;
            }
            var lastEvent = lastPayloadRef.get();
            var builder = messageTextContentRef.get();
            var payloads = aggregatePayloadsRef.get();
            if (payload instanceof WithText textPayload) {
                var text = textPayload.text();
                if (lastEvent instanceof WithText && lastEvent.getClass().equals(payload.getClass())) {
                    appendText(text, builder);
                } else {
                    if (lastEvent instanceof WithText && builder.length() > 0) {
                        addAggregate(payloads, (WithText) lastEvent, builder);
                        builder.setLength(0);
                    }
                    appendText(text, builder);
                }
            } else {
                if (lastEvent instanceof WithText && builder.length() > 0) {
                    addAggregate(payloads, (WithText) lastEvent, builder);
                    builder.setLength(0);
                }
                payloads.add(payload);
            }
            lastPayloadRef.set(payload);
        }).doOnComplete(() -> {
            var lastEvent = lastPayloadRef.get();
            var payloads = aggregatePayloadsRef.get();
            var builder = messageTextContentRef.get();
            if (lastEvent != null && lastEvent instanceof WithText && builder.length() > 0) {
                addAggregate(payloads, (WithText) lastEvent, builder);
            }
            consumer.accept(payloads);
            if (messageTextContentRef.get() != null) messageTextContentRef.get().setLength(0);
            if (payloads != null) payloads.clear();
            lastPayloadRef.set(null);
        });
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

    public static List<Message> toMessages(List<? extends Payload> payloads, ToolCallbackHandler handler, boolean includeThinking) {
        List<Message> messages = new ArrayList<>();
        var buffer = new StringBuffer();
        
        for (Payload payload : payloads) {
            if (includeThinking && payload instanceof Thinking thinking) {
                buffer.append("<think>\n" + thinking.text() + "\n</think>\n\n");
            } else if (payload instanceof Structured<?> struct) {
                // TODO: what about structured payloads?
            } else if (payload instanceof Text text) {
                buffer.append(text.text() + "\n");
                addMessage(messages, new AssistantMessage(buffer.toString()));
                buffer.setLength(0);
            } else if (payload instanceof FuncResult func) {
                buffer.setLength(0);
                for (var message : handler.getToolMessages(func)) {
                    addMessage(messages, message);
                }
            }
        }
        if (buffer.length() > 0) {
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
