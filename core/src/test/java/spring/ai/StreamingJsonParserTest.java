package spring.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import spring.ai.parser.StreamingJsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StreamingJsonParserTest {

	private JsonFactory jsonFactory;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jsonFactory = new JsonFactory();
		objectMapper = new ObjectMapper();
	}

	@Test
	void testSimpleJsonObject() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "{\"key\":\"value\"}";
		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertEquals("value", results.get(0).get("key").asText());
		assertEquals(input, output); // Since requiresOutput is true
	}

	@Test
	void testSimpleJsonArray() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "[1,2,3]";
		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertTrue(results.get(0).isArray());
		assertEquals(3, results.get(0).size());
		assertEquals(input, output);
	}

	@Test
	void testTextBeforeAndAfterJson() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String prefix = "Some text before ";
		String json = "{\"id\":123}";
		String suffix = " and some text after.";
		String input = prefix + json + suffix;

		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertEquals(123, results.get(0).get("id").asInt());
		assertEquals(input, output);
	}

	@Test
	void testJsonInMarkdownFenceBlock() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String prefix = "```json\n";
		String json = "{\"tool\":\"calculator\",\"args\":{\"query\":\"2+2\"}}\n";
		String suffix = "```";
		String input = prefix + json + suffix;

		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertEquals("calculator", results.get(0).get("tool").asText());
		assertEquals("2+2", results.get(0).get("args").get("query").asText());
		// The parser should output the entire block including fences when requiresOutput is true
		// and the JSON is correctly parsed from within the fence.
		assertEquals(input, output);
	}

	@Test
	void testMultipleJsonObjects() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "{\"a\":1}{\"b\":2}";
		String output = parser.feedText(input, true);

		assertEquals(2, results.size());
		assertEquals(1, results.get(0).get("a").asInt());
		assertEquals(2, results.get(1).get("b").asInt());
		assertEquals(input, output);
	}

	@Test
	void testShouldStop() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String part1 = "{\"key1\":\"val1\"} some text ";
		String part2 = "{\"key2\":\"val2\"}";

		String output1 = parser.feedText(part1);
		assertEquals(1, results.size());
		assertEquals("val1", results.get(0).get("key1").asText());
		assertEquals(part1, output1);

		parser.shouldStop();
		assertTrue(parser.isStopped());

		String output2 = parser.feedText(part2, true);
		assertEquals(1, results.size()); // No new JSON should be processed
		assertEquals("", output2); // Should return empty as it stopped
	}

	@Test
	void testRequiresOutputFalse() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, false);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "Text before {\"data\":\"test\"} text after.";
		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertEquals("test", results.get(0).get("data").asText());
		assertEquals("", output); // Output should be empty
	}

	@Test
	void testFeedCharByChar() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String json = "{\"char\":\"bychar\"}";
		StringBuilder fedText = new StringBuilder();

		for (int i = 0; i < json.length(); i++) {
			fedText.append(parser.feedText(String.valueOf(json.charAt(i)), i == json.length() - 1));
		}

		assertEquals(1, results.size());
		assertEquals("bychar", results.get(0).get("char").asText());
		assertEquals(json, fedText.toString());
	}

	@Test
	void testFeedInChunks() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String part1 = "Some text {\"chunk\":\"one\",";
		String part2 = "\"status\":\"pending\"} and more text ";
		String part3 = "[{\"item\":1}]";

		StringBuilder fedText = new StringBuilder();
		fedText.append(parser.feedText(part1));
		fedText.append(parser.feedText(part2));
		fedText.append(parser.feedText(part3, true));

		assertEquals(2, results.size());
		assertEquals("one", results.get(0).get("chunk").asText());
		assertEquals("pending", results.get(0).get("status").asText());
		assertTrue(results.get(1).isArray());
		assertEquals(1, results.get(1).get(0).get("item").asInt());
		assertEquals(part1 + part2 + part3, fedText.toString());
	}

	@Test
	void testLeadingAndTrailingWhitespace() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "   \n\t  {\"whitespace\":\"test\"}   ";
		String output = parser.feedText(input, true);

		assertEquals(1, results.size());
		assertEquals("test", results.get(0).get("whitespace").asText());
		assertEquals(input, output);
	}

	@Test
	void testMalformedJsonSkipped() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		// Malformed JSON (missing closing quote) followed by valid JSON
		String input = "Text {\"malformed\": \"value} then {\"good\":\"json\"}";
		String output = parser.feedText(input, true);

		assertEquals(1, results.size()); // Only the good JSON should be parsed
		assertEquals("json", results.get(0).get("good").asText());
		assertEquals(input, output);
	}

	@Test
	void testJsonSplitAcrossFeeds() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		StringBuilder fedText = new StringBuilder();

		fedText.append(parser.feedText("{\"key\":"));
		assertEquals(0, results.size());

		fedText.append(parser.feedText("\"value\""));
		assertEquals(0, results.size());

		fedText.append(parser.feedText("}"));
		assertEquals(1, results.size());
		assertEquals("value", results.get(0).get("key").asText());
		assertEquals("{\"key\":\"value\"}", fedText.toString());
	}

	@Test
	void testNonJsonTextReturned() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String input = "This is just plain text.";
		String output = parser.feedText(input, true);

		assertEquals(0, results.size());
		assertEquals(input, output);
	}

	@Test
	void testEmptyInput() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		String output = parser.feedText("", true);
		assertEquals("", output);
		output = parser.feedText(null, true);
		assertNull(output);
	}

	@Test
	void testFenceBlockWithNoFollowingJson() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);
		String input = "```json\nThis is not json\n```";
		String output = parser.feedText(input, true);
		assertEquals(0, results.size());
		assertEquals(input, output);
	}

	@Test
	void testPartialFenceBlockThenJson() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);
		String input = "```jso{\"key\":\"value\"}"; // Incomplete fence, then JSON
		String output = parser.feedText(input, true);
		assertEquals(1, results.size());
		assertEquals("value", results.get(0).get("key").asText());
		assertEquals(input, output);
	}

	@Test
	void testStreamByOneSymbol() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String json = "{\"foo\":\"bar\"}";
		StringBuilder output = new StringBuilder();
		for (int i = 0; i < json.length(); i++) {
			output.append(parser.feedText(String.valueOf(json.charAt(i)), i == json.length() - 1));
		}

		assertEquals(1, results.size());
		assertEquals("bar", results.get(0).get("foo").asText());
		assertEquals(json, output.toString());
	}

	@Test
	void testStreamByOneSymbolWithFences(){
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		List<JsonNode> results = new ArrayList<>();
		parser.consume(results::add);

		String json = "```json\n{\"foo\":\"bar\"}\n```";
		StringBuilder output = new StringBuilder();
		for (int i = 0; i < json.length(); i++) {
			output.append(parser.feedText(String.valueOf(json.charAt(i)), i == json.length() - 1));
		}

		assertEquals(1, results.size());
		assertEquals("bar", results.get(0).get("foo").asText());
		assertEquals(json, output.toString());
	}

	@Test
	void testCloseResetsStopFlag() {
		StreamingJsonParser parser = new StreamingJsonParser(jsonFactory, objectMapper, true);
		parser.shouldStop();
		assertTrue(parser.isStopped());
		parser.close();
		assertFalse(parser.isStopped());
	}
}
