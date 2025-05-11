package spring.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import spring.ai.agents.Agent.Event;
import spring.ai.parser.ThinkParser;

import static org.junit.jupiter.api.Assertions.*;

public class ThinkParserTest {

    private ThinkParser parser;

    @BeforeEach
    void setUp() {
        parser = new ThinkParser();
    }

    @Test
    void testSimpleText() {
        Event.Payload payload = parser.acceptChunk("Hello, world!", false);
        assertInstanceOf(Event.Text.class, payload);
        assertEquals("Hello, world!", ((Event.Text) payload).text());
        assertFalse(payload.isLast());

        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload);
        assertNull(((Event.Text) finalPayload).text()); // Buffer was empty before flush
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testSimpleThinkBlock() {
        Event.Payload payload1 = parser.acceptChunk("<think>Thinking hard</think>", false);
        assertInstanceOf(Event.Thinking.class, payload1);
        assertEquals("Thinking hard", ((Event.Thinking) payload1).text());
        assertTrue(payload1.isLast()); // Close tag implies end of this thinking block

        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload); // Should be text as think block closed
        assertNull(((Event.Text) finalPayload).text());
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testTextBeforeThinkBlock() {
        Event.Payload payload1 = parser.acceptChunk("Hello ", false);
        assertInstanceOf(Event.Text.class, payload1);
        assertEquals("Hello ", ((Event.Text) payload1).text());
        assertFalse(payload1.isLast());

        Event.Payload payload2 = parser.acceptChunk("<think>now I think</think>", false);
        assertInstanceOf(Event.Thinking.class, payload2);
        assertEquals("now I think", ((Event.Thinking) payload2).text());
        assertTrue(payload2.isLast());

        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload);
        assertNull(((Event.Text) finalPayload).text());
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testTextAfterThinkBlock() {
        Event.Payload payload1 = parser.acceptChunk("<think>Done thinking</think> and now text.", false);
        assertInstanceOf(Event.Thinking.class, payload1);
        assertEquals("Done thinking", ((Event.Thinking) payload1).text());
        assertTrue(payload1.isLast());

        Event.Payload payload2 = parser.acceptChunk("", false);
        assertInstanceOf(Event.Text.class, payload2);
        assertEquals(" and now text.", ((Event.Text) payload2).text());
        assertFalse(payload2.isLast());

        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload);
        assertNull(((Event.Text) finalPayload).text());
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testTextBeforeDuringAndAfterThinkBlock() {
        Event.Payload p1 = parser.acceptChunk("Prefix text ", false);
        assertInstanceOf(Event.Text.class, p1);
        assertEquals("Prefix text ", ((Event.Text) p1).text());

        Event.Payload p2 = parser.acceptChunk("<think>This is a thought.</think>", false);
        assertInstanceOf(Event.Thinking.class, p2);
        assertEquals("This is a thought.", ((Event.Thinking) p2).text());
        assertTrue(p2.isLast());

        Event.Payload p3 = parser.acceptChunk(" Suffix text.", true);
        assertInstanceOf(Event.Text.class, p3);
        assertEquals(" Suffix text.", ((Event.Text) p3).text());
        assertTrue(p3.isLast());
    }

    @Test
    void testMultipleThinkBlocks() {
        Event.Payload p1 = parser.acceptChunk("Text1 <think>Thought1</think>", false);
        assertInstanceOf(Event.Text.class, p1); // "Text1 "
        assertEquals("Text1 ", ((Event.Text) p1).text());

        Event.Payload p2 = parser.acceptChunk("", false); // Process the rest of the previous chunk
        assertInstanceOf(Event.Thinking.class, p2); // "Thought1"
        assertEquals("Thought1", ((Event.Thinking) p2).text());
        assertTrue(p2.isLast());

        Event.Payload p3 = parser.acceptChunk("Text2 <think>Thought2</think>", false);
        assertInstanceOf(Event.Text.class, p3); // "Text2 "
        assertEquals("Text2 ", ((Event.Text) p3).text());

        Event.Payload p4 = parser.acceptChunk("", false); // Process the rest
        assertInstanceOf(Event.Thinking.class, p4); // "Thought2"
        assertEquals("Thought2", ((Event.Thinking) p4).text());
        assertTrue(p4.isLast());

        Event.Payload p5 = parser.flush();
        assertInstanceOf(Event.Text.class, p5);
        assertNull(((Event.Text) p5).text());
        assertTrue(p5.isLast());
    }

    @Test
    void testPartialOpenTag() {
        Event.Payload p1 = parser.acceptChunk("Some text <th", false);
        assertInstanceOf(Event.Text.class, p1);
        assertEquals("Some text ", ((Event.Text) p1).text()); // "Some text " should be flushed

        Event.Payload p2 = parser.acceptChunk("ink>Thought</think>", false);
        assertInstanceOf(Event.Thinking.class, p2);
        assertEquals("Thought", ((Event.Thinking) p2).text());
        assertTrue(p2.isLast());

        Event.Payload p3 = parser.flush();
        assertInstanceOf(Event.Text.class, p3);
        assertNull(((Event.Text) p3).text());
        assertTrue(p3.isLast());
    }

    @Test
    void testPartialCloseTag() {
        Event.Payload p1 = parser.acceptChunk("<think>Thinking part 1</th", false);
        assertInstanceOf(Event.Thinking.class, p1);
        assertEquals("Thinking part 1", ((Event.Thinking) p1).text()); // Flushes up to partial tag
        assertFalse(p1.isLast());

        Event.Payload p2 = parser.acceptChunk("ink>", false);
        assertInstanceOf(Event.Thinking.class, p2); // The rest of the partial tag
        assertNull(((Event.Thinking) p2).text()); // No new text content from this chunk for thinking
        assertTrue(p2.isLast()); // Close tag completes it

        Event.Payload p3 = parser.flush();
        assertInstanceOf(Event.Text.class, p3);
        assertNull(((Event.Text) p3).text());
        assertTrue(p3.isLast());
    }

    @Test
    void testChunkingWithinThinkBlock() {
        Event.Payload p1 = parser.acceptChunk("<think>Initial thought. ", false);
        assertInstanceOf(Event.Thinking.class, p1);
        assertEquals("Initial thought. ", ((Event.Thinking) p1).text());
        assertFalse(p1.isLast());

        Event.Payload p2 = parser.acceptChunk("More thinking. ", false);
        assertInstanceOf(Event.Thinking.class, p2);
        assertEquals("More thinking. ", ((Event.Thinking) p2).text());
        assertFalse(p2.isLast());

        Event.Payload p3 = parser.acceptChunk("Final part.</think>Afterthought.", false);
        assertInstanceOf(Event.Thinking.class, p3);
        assertEquals("Final part.", ((Event.Thinking) p3).text());
        assertTrue(p3.isLast());

        Event.Payload p4 = parser.acceptChunk("", false);
        assertInstanceOf(Event.Text.class, p4);
        assertEquals("Afterthought.", ((Event.Text) p4).text());
        assertFalse(p4.isLast());

        Event.Payload p5 = parser.flush();
        assertInstanceOf(Event.Text.class, p5);
        assertNull(((Event.Text) p5).text());
        assertTrue(p5.isLast());
    }

    @Test
    void testChunkingOfText() {
        Event.Payload p1 = parser.acceptChunk("This is ", false);
        assertInstanceOf(Event.Text.class, p1);
        assertEquals("This is ", ((Event.Text) p1).text());
        assertFalse(p1.isLast());

        Event.Payload p2 = parser.acceptChunk("some text.", false);
        assertInstanceOf(Event.Text.class, p2);
        assertEquals("some text.", ((Event.Text) p2).text());
        assertFalse(p2.isLast());

        Event.Payload p3 = parser.flush();
        assertInstanceOf(Event.Text.class, p3);
        assertNull(((Event.Text) p3).text());
        assertTrue(p3.isLast());
    }

    @Test
    void testEmptyInput() {
        Event.Payload p1 = parser.acceptChunk("", false);
        assertInstanceOf(Event.Text.class, p1);
        assertNull(((Event.Text) p1).text());
        assertFalse(p1.isLast());

        Event.Payload p2 = parser.flush();
        assertInstanceOf(Event.Text.class, p2);
        assertNull(((Event.Text) p2).text());
        assertTrue(p2.isLast());
    }

    @Test
    void testNullInput() {
        Event.Payload p1 = parser.acceptChunk(null, false);
        assertInstanceOf(Event.Text.class, p1);
        assertNull(((Event.Text) p1).text());
        assertFalse(p1.isLast()); // isLast is false from the call

        Event.Payload p2 = parser.acceptChunk(null, true);
        assertInstanceOf(Event.Text.class, p2);
        assertNull(((Event.Text) p2).text());
        assertTrue(p2.isLast()); // isLast is true from the call
    }

    @Test
    void testFlushWhenInThinkMode() {
        parser.acceptChunk("<think>Incomplete thought", false); // Enters think mode
        Event.Payload p1 = parser.flush();
        assertInstanceOf(Event.Thinking.class, p1);
        assertNull(((Event.Thinking) p1).text());
        assertTrue(p1.isLast());
    }

    @Test
    void testFlushWhenNotInThinkModeWithContent() {
        parser.acceptChunk("Some pending text", false);
        Event.Payload p1 = parser.flush();
        assertInstanceOf(Event.Text.class, p1);
        assertNull(((Event.Text) p1).text());
        assertTrue(p1.isLast());
    }

    @Test
    void testFlushWithEmptyBuffer() {
        Event.Payload p1 = parser.flush();
        assertInstanceOf(Event.Text.class, p1);
        assertNull(((Event.Text) p1).text());
        assertTrue(p1.isLast());
    }

    @Test
    void testOpenTagSplitAcrossChunks() {
        Event.Payload p1 = parser.acceptChunk("Text before <", false);
        assertInstanceOf(Event.Text.class, p1);
        assertEquals("Text before ", ((Event.Text) p1).text());

        Event.Payload p2 = parser.acceptChunk("think>Thought inside</think>", false);
        assertInstanceOf(Event.Thinking.class, p2);
        assertEquals("Thought inside", ((Event.Thinking) p2).text());
        assertTrue(p2.isLast());

        Event.Payload p3 = parser.flush();
        assertInstanceOf(Event.Text.class, p3);
        assertNull(((Event.Text) p3).text());
        assertTrue(p3.isLast());
    }

    @Test
    void testCloseTagSplitAcrossChunks() {
        Event.Payload p1 = parser.acceptChunk("<think>Thinking content part 1</", false);
        assertInstanceOf(Event.Thinking.class, p1);
        assertEquals("Thinking content part 1", ((Event.Thinking) p1).text());
        assertFalse(p1.isLast());

        Event.Payload p2 = parser.acceptChunk("think>Text after", false);
        assertInstanceOf(Event.Thinking.class, p2); // This chunk completes the </think> tag
        assertNull(((Event.Thinking) p2).text()); // No new thinking text from this chunk
        assertTrue(p2.isLast()); // The thinking block is now closed

        Event.Payload p3 = parser.acceptChunk("", false);
        assertInstanceOf(Event.Text.class, p3);
        assertEquals("Text after", ((Event.Text) p3).text());
        assertFalse(p3.isLast());

        Event.Payload p4 = parser.flush(); // Should flush empty
        assertInstanceOf(Event.Text.class, p4);
        assertNull(((Event.Text) p4).text());
        assertTrue(p4.isLast());
    }

    @Test
    void testIsLastTrueMidStream() {
        Event.Payload payload = parser.acceptChunk("Hello", true); // isLast = true
        assertInstanceOf(Event.Text.class, payload);
        assertEquals("Hello", ((Event.Text) payload).text());
        assertTrue(payload.isLast()); // Should be true as passed

        // Flushing after an isLast=true chunk should yield an empty final event
        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload);
        assertNull(((Event.Text) finalPayload).text());
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testEmptyThinkBlock() {
        Event.Payload payload = parser.acceptChunk("<think></think>Some text", false);
        assertInstanceOf(Event.Thinking.class, payload);
        assertNull(((Event.Thinking) payload).text());
        assertTrue(payload.isLast());

        Event.Payload payload2 = parser.acceptChunk("", false);
        assertInstanceOf(Event.Text.class, payload2);
        assertEquals("Some text", ((Event.Text) payload2).text());
        assertFalse(payload2.isLast());

        Event.Payload finalPayload = parser.flush();
        assertInstanceOf(Event.Text.class, finalPayload);
        assertNull(((Event.Text) finalPayload).text());
        assertTrue(finalPayload.isLast());
    }

    @Test
    void testOnlyOpenTagThenFlush() {
        parser.acceptChunk("<think>", false);
        Event.Payload payload = parser.flush();
        assertInstanceOf(Event.Thinking.class, payload);
        assertNull(((Event.Thinking) payload).text());
        assertTrue(payload.isLast());
    }

    @Test
    void testOnlyOpenTagPartialThenFlush() {
        parser.acceptChunk("<thi", false);
        Event.Payload payload = parser.flush();
        assertInstanceOf(Event.Text.class, payload); // Not enough for a tag, treated as text
        assertEquals("<thi", ((Event.Text) payload).text());
        assertTrue(payload.isLast());
    }

    @Test
    void testThinkTagInsideTextThenFlush() {
        Event.Payload p1 = parser.acceptChunk("Text <think> thought", false);
        assertInstanceOf(Event.Text.class, p1);
        assertEquals("Text ", ((Event.Text)p1).text());

        Event.Payload p2 = parser.acceptChunk("", false); // Force processing
        assertInstanceOf(Event.Thinking.class, p2);
        assertEquals(" thought", ((Event.Thinking)p2).text());
        assertFalse(p2.isLast());

        Event.Payload p3 = parser.flush();
        assertInstanceOf(Event.Thinking.class, p3);
        assertTrue(p3.isLast());
        assertNull(((Event.Thinking)p3).text());
    }

    @Test
    void testStreamByOneSymbol() {
        // Example: "A<think>B</think>C"
        String input = "A<think>B</think>C";
        boolean[] isLastFlags = new boolean[input.length()];
        isLastFlags[input.length() - 1] = true; // Only last char isLast=true

        // We'll collect the outputs for inspection
        java.util.List<Event.Payload> outputs = new java.util.ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            String chunk = String.valueOf(input.charAt(i));
            outputs.add(parser.acceptChunk(chunk, isLastFlags[i]));
        }
        // Flush at the end to ensure all is processed
        outputs.add(parser.flush());

        // Now, check the sequence of outputs
        // 1. First char "A" -> Text
        assertInstanceOf(Event.Text.class, outputs.get(0));
        assertEquals("A", ((Event.Text)outputs.get(0)).text());

        // 2. Next chars "<think>" (positions 1-6) should not emit text, but after ">", the parser may emit a Thinking event with null or empty text
        // 3. The "B" (position 7) should be Thinking event with "B"
        // 4. "</think>" (positions 8-14) should close the thinking block, possibly emitting a Thinking event with null text and isLast true
        // 5. "C" (position 15) should be Text event with "C" and isLast true

        // Let's check the main expected outputs:
        // outputs.get(0): Text "A"
        // outputs.get(8): Thinking "B"
        // outputs.get(16): Thinking null, isLast true (end of think block)
        // outputs.get(17): Text "C", isLast true
        // outputs.get(18): Text null, isLast true (flush)

        assertInstanceOf(Event.Thinking.class, outputs.get(8));
        assertEquals("B", ((Event.Thinking)outputs.get(8)).text());
        assertFalse(outputs.get(8).isLast());

        assertInstanceOf(Event.Thinking.class, outputs.get(16));
        assertNull(((Event.Thinking)outputs.get(16)).text());
        assertTrue(outputs.get(16).isLast());

        assertInstanceOf(Event.Text.class, outputs.get(17));
        assertEquals("C", ((Event.Text)outputs.get(17)).text());
        assertTrue(outputs.get(17).isLast());

        assertInstanceOf(Event.Text.class, outputs.get(18));
        assertNull(((Event.Text)outputs.get(18)).text());
        assertTrue(outputs.get(18).isLast());
    }
}
