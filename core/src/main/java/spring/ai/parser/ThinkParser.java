package spring.ai.parser;

import spring.ai.agents.Agent.Event;

import static spring.ai.agents.Utils.*;

public class ThinkParser {

    private static final String OPEN_TAG = "<think>";
    private static final String CLOSE_TAG = "</think>";

    private final StringBuilder buffer = new StringBuilder(1024);
    private boolean inThinkMode = false;

    public Event.Payload acceptChunk(String chunk, boolean isLast) {
        if (chunk != null && !chunk.isEmpty()) {
            buffer.append(chunk);
        }
        if (isLast) {
            return this.flush();
        }
        // If chunk is null or empty, emit what is available (if any)
        return processBuffer();
    }

    private Event.Payload processBuffer() {
        if (!inThinkMode) {
            int openIndex = buffer.indexOf(OPEN_TAG);
            if (openIndex == -1) {
                // No open tag found, check for partial tag at the end
                int partialStart = findPartialTagStart(OPEN_TAG);
                int flushUpTo = (partialStart != -1) ? partialStart : buffer.length();
                if (flushUpTo > 0) {
                    String text = buffer.substring(0, flushUpTo);
                    buffer.delete(0, flushUpTo);
                    return newTextEvent(text, false);
                }
                // Nothing to emit
                return newTextEvent(null, false);
            } else {
                // Emit text before the open tag
                if (openIndex > 0) {
                    String text = buffer.substring(0, openIndex);
                    buffer.delete(0, openIndex);
                    return newTextEvent(text, false);
                }
                // Remove the open tag itself and enter think mode
                buffer.delete(0, OPEN_TAG.length());
                inThinkMode = true;
                // Immediately process thinking content
                return processBuffer();
            }
        } else { // inThinkMode
            int closeIndex = buffer.indexOf(CLOSE_TAG);
            if (closeIndex == -1) {
                // No close tag found, check for partial close tag at the end
                int partialStart = findPartialTagStart(CLOSE_TAG);
                int flushUpTo = (partialStart != -1) ? partialStart : buffer.length();
                if (flushUpTo > 0) {
                    String text = buffer.substring(0, flushUpTo);
                    buffer.delete(0, flushUpTo);
                    return newThinkingEvent(text, false);
                }
                // Nothing to emit
                return newThinkingEvent(null, false);
            } else {
                // Emit thinking content up to close tag
                String text = buffer.substring(0, closeIndex);
                buffer.delete(0, closeIndex + CLOSE_TAG.length());
                inThinkMode = false;
                return newThinkingEvent(text.length() > 0 ? text : null, true);
            }
        }
    }

    /**
     * Finds start index of a partial tag (open or close) at the end of the buffer.
     * Returns -1 if no partial tag is present.
     *
     * @param tag The tag string to search for (e.g., "<think>" or "</think>")
     */
    private int findPartialTagStart(String tag) {
        int tagLen = tag.length();
        int bufferLen = buffer.length();
        int start = Math.max(0, bufferLen - tagLen + 1);
        for (int i = start; i < bufferLen; i++) {
            if (tag.startsWith(buffer.substring(i))) {
                return i;
            }
        }
        return -1;
    }

    public Event.Payload flush() {
        Event.Payload result;
        if (inThinkMode) {
            result = newThinkingEvent(buffer.length() > 0 ? buffer.toString() : null, true);
        } else {
            result = newTextEvent(buffer.length() > 0 ? buffer.toString() : null, true);
        }
        buffer.setLength(0);
        inThinkMode = false;
        return result;
    }
}
