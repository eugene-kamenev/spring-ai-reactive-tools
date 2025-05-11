package spring.ai.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteBufferFeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * A streaming JSON handler that processes JSON data in a non-blocking manner.
 * It uses Jackson's streaming API to parse JSON data and allows custom
 * processing of found JSON nodes.
 * It was designed to parse valid JSON structures out of a stream of free text.
 * The idea is that it will start parsing when it finds a '{' or '[' and stop
 * when it finds the matching '}' or ']'.
 *
 * NOTE: Performance and memory usage has not been analyzed. Use requiresOutput
 * = false,
 * if text output is not required, to avoid buffering.
 */
public class StreamingJsonParser {

    private static final char[] empty = new char[0];
    private static final String fenceBlock = "```json";

    private final char[] charInputBuffer = new char[1];
    private final char[] charOutputBuffer = new char[1];
    private final StringBuffer outBuffer;
    private final StringBuffer feedBuffer;
    private final JsonFactory jsonFactory;
    private final ObjectMapper objectMapper;
    private final boolean requiresOutput;
    private final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
    private final CharBuffer reusableCharBuffer = CharBuffer.wrap(charInputBuffer); // TODO: maybe we can buffer more,
                                                                                    // not sure.
    private final ByteBuffer reusableByteBuffer = ByteBuffer.allocateDirect(4); // Max bytes for a UTF-8 char
    private JsonParser parser;
    private boolean buffering = false;
    private boolean shouldStop = false;
    private Consumer<JsonNode> jsonConsumer;
    private ByteBufferFeeder inputFeeder;
    private TokenBuffer tokenBuffer;
    private int depth = 0;
    private int inFenceBlock = 0;

    public StreamingJsonParser(JsonFactory factory, ObjectMapper mapper, boolean requiresOutput) {
        this.jsonFactory = factory;
        this.objectMapper = mapper;
        this.requiresOutput = requiresOutput;
        this.outBuffer = new StringBuffer();
        this.feedBuffer = new StringBuffer();
        resetParser();
    }

    public StreamingJsonParser(JsonFactory factory, ObjectMapper mapper) {
        this(factory, mapper, true);
    }

    public void consume(Consumer<JsonNode> jsonConsumer) {
        this.jsonConsumer = jsonConsumer;
    }

    public void shouldStop() {
        this.shouldStop = true;
    }

    public boolean isStopped() {
        return this.shouldStop;
    }

    public void close() {
        try {
            if (parser != null) {
                this.shouldStop = false;
                this.inputFeeder.endOfInput();
                this.parser.close();
            }
        } catch (IOException ignored) {
        }
    }

    public String feedText(String chunk) {
        return feedText(chunk, false);
    }

    public String feedText(String chunk, boolean isLast) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }
        var last = chunk.length() - 1;
        feedBuffer.setLength(0);
        for (int i = 0; i <= last; i++) {
            if (shouldStop) {
                break;
            }
            var feedResult = feedChar(chunk.charAt(i), i == last && isLast);
            if (feedResult.length > 0) {
                feedBuffer.append(feedResult);
            }
        }
        return feedBuffer.toString();
    }

    private char[] feedChar(char ch, boolean isLast) {
        try {
            if (!buffering) {
                if (isLast && requiresOutput) {
                    outBuffer.append(ch);
                    return returnRemainingBuffer();
                } else if (ch == '{' || ch == '[') {
                    buffering = true;
                    inFenceBlock = 0;
                    feedCharToParser(ch);
                    return empty;
                } else if (inFenceBlock == 0 && ch == '`') {
                    inFenceBlock++;
                    outBuffer.append(ch);
                    return empty;
                } else if (inFenceBlock > 0 && !buffering) {
                    outBuffer.append(ch);
                    String currentOutBufferContent = outBuffer.toString();

                    if (currentOutBufferContent.equals(fenceBlock)) {
                        buffering = true;
                        inFenceBlock = 0; // Reset fence block detection state
                        return empty; // Char consumed as part of the fence block
                    } else if (fenceBlock.startsWith(currentOutBufferContent)) {
                        // Partial fence block, continue accumulating
                        return empty;
                    } else {
                        // Not a fence block (e.g., "```x")
                        // The content of outBuffer should be returned.
                        inFenceBlock = 0;
                        return returnRemainingBuffer(); // This will return currentOutBufferContent and clear outBuffer
                    }
                } else if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                    // Output whitespace immediately if not in fence block
                    if (requiresOutput) {
                        charOutputBuffer[0] = ch;
                        return charOutputBuffer;
                    }
                    return empty;
                } else {
                    // Output any buffered text (should only be from fence block logic)
                    if (outBuffer.length() > 0) {
                        return returnRemainingBuffer();
                    }
                    // Output non-JSON, non-fence, non-whitespace character immediately
                    if (requiresOutput) {
                        charOutputBuffer[0] = ch;
                        return charOutputBuffer;
                    }
                    return empty;
                }
            } else {
                feedCharToParser(ch);
            }
        } catch (IOException e) {
            // Save the current buffer before reset
            String remaining = outBuffer.toString();
            resetParser();
            return processRemainingBufferAfterError(remaining, isLast);
        }

        // Only flush outBuffer at the end if not buffering and there's something left
        if ((isLast || !buffering) && requiresOutput && outBuffer.length() > 0) {
            return returnRemainingBuffer();
        }
        return empty;
    }

    private char[] returnRemainingBuffer() {
        if (outBuffer.length() > 0) {
            var chars = outBuffer.toString().toCharArray();
            outBuffer.setLength(0);
            return chars;
        }
        return empty;
    }

    /**
     * TODO: any better approach?
     *
     * @param ch
     * @throws IOException
     */
    private void feedCharToParser(char ch) throws IOException {
        if (requiresOutput) {
            outBuffer.append(ch);
        }
        charInputBuffer[0] = ch;
        reusableCharBuffer.rewind(); // Prepare CharBuffer for reading
        reusableByteBuffer.clear(); // Prepare ByteBuffer for writing

        var result = encoder.encode(reusableCharBuffer, reusableByteBuffer, true);
        if (result.isError()) {
            result.throwException();
        }

        if (reusableByteBuffer.position() > 0) {
            reusableByteBuffer.flip();
            inputFeeder.feedInput(reusableByteBuffer);
            parseAvailable();
        }

        encoder.reset();
    }

    private void parseAvailable() throws IOException {
        var token = parser.nextToken();
        while (token != null && token != JsonToken.NOT_AVAILABLE) {
            tokenBuffer.copyCurrentEvent(parser);
            switch (token) {
                case START_OBJECT:
                case START_ARRAY:
                    depth++;
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    depth--;
                    if (depth == 0) {
                        if (jsonConsumer != null) {
                            try {
                                jsonConsumer.accept(objectMapper.readTree(tokenBuffer.asParser()));
                            } catch (JsonProcessingException e) {
                                // Skip malformed JSON
                            }
                        }
                        resetParser();
                        return;
                    }
                    break;
                default:
                    break;
            }
            token = parser.nextToken();
        }
    }

    private boolean findPartialFenceBlockStart() {
        int tagLen = fenceBlock.length();
        int bufferLen = outBuffer.length();
        int fenceIndex = outBuffer.indexOf("`", 0);
        int end = Math.min(bufferLen, tagLen);
        return fenceBlock.startsWith(outBuffer.substring(fenceIndex, end));
    }

    private char[] processRemainingBufferAfterError(String remaining, boolean isLast) {
        int nextObj = remaining.indexOf('{', 1);
        int nextArr = remaining.indexOf('[', 1);
        int nextJsonStart = (nextObj >= 0 && nextArr >= 0) ? Math.min(nextObj, nextArr)
                : (nextObj >= 0 ? nextObj : nextArr);

        if (nextJsonStart >= 0 && nextJsonStart < remaining.length()) {
            outBuffer.setLength(0); // Clear output buffer

            var prefixStr = (requiresOutput && nextJsonStart > 0)
                    ? remaining.substring(0, nextJsonStart)
                    : "";
            var restStr = remaining.substring(nextJsonStart);

            var resultBuilder = new StringBuilder();
            for (int i = 0; i < restStr.length(); i++) {
                boolean lastChar = isLast && i == restStr.length() - 1;
                char[] processed = feedChar(restStr.charAt(i), lastChar);
                if (processed.length > 0) {
                    resultBuilder.append(processed);
                }
            }

            return (prefixStr + resultBuilder).toCharArray();
        }

        if (requiresOutput && !remaining.isEmpty()) {
            outBuffer.setLength(0);
            return remaining.toCharArray();
        }

        outBuffer.setLength(0);
        return empty;
    }

    private void resetParser() {
        buffering = false;
        depth = 0;
        tokenBuffer = new TokenBuffer(objectMapper, false);
        inFenceBlock = 0;
        try {
            if (parser != null) {
                // TODO: any better approach to avoid recreating the parser?
                inputFeeder.endOfInput();
                parser.close();
            }
            parser = jsonFactory.createNonBlockingByteBufferParser();
            inputFeeder = (ByteBufferFeeder) parser.getNonBlockingInputFeeder();
        } catch (IOException e) {
            throw new RuntimeException("Failed to reset parser", e);
        }
    }
}
