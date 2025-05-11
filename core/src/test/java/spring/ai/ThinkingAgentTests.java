package spring.ai;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import spring.ai.agents.Agent.Event.*;
import spring.ai.agents.AgentConfig.ToolCallConfig;
import spring.ai.agents.AgentConfig.ToolCallHandler;
import spring.ai.agents.base.BaseAgent;
import spring.ai.agents.base.PayloadAggregator;
import spring.ai.schema.ObjectSchemaGenerator;
import spring.ai.schema.SchemaFormat;

import static org.junit.jupiter.api.Assertions.*;

public class ThinkingAgentTests extends BaseTests {

	@Test
	void testThinking() {
		List<Payload> payloads = new ArrayList<>();
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper());
		var prompt = new Prompt("Hello, how are you?");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(2, payloads.size());
		assertInstanceOf(Thinking.class, payloads.get(0));
		assertInstanceOf(Text.class, payloads.get(1));
	}

	@Test
	void testNonThinking() {
		List<Payload> payloads = new ArrayList<>();
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper());
		var prompt = new Prompt("Hello, how are you? /no_think");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(1, payloads.size());
		assertInstanceOf(Text.class, payloads.get(0));
	}

	@Test
	void testToolsCustomYamlFormat() {
		List<Payload> payloads = new ArrayList<>();
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper()) {
			@Tool
			public Mono<String> executeCommand(@ToolParam String command) {
				return EXECUTE_COMMAND.apply(command);
			}
		};
		var prompt = new Prompt("Execute ls -la command");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(5, payloads.size());
		assertInstanceOf(Thinking.class, payloads.get(0));
		assertInstanceOf(FuncCall.class, payloads.get(1));
		assertInstanceOf(FuncResult.class, payloads.get(2));
		assertInstanceOf(Thinking.class, payloads.get(3));
		assertInstanceOf(Text.class, payloads.get(4));

		var messages = chatAgent.toMessages(payloads, false);
		assertEquals(3, messages.size());
		assertInstanceOf(AssistantMessage.class, messages.get(0));
		assertFalse(() -> messages.get(0).getText().contains("<think>"));
		assertInstanceOf(UserMessage.class, messages.get(1));
		assertInstanceOf(AssistantMessage.class, messages.get(2));
		assertFalse(() -> messages.get(2).getText().contains("<think>"));

		var thinkingMessages = chatAgent.toMessages(payloads, true);
		assertEquals(3, thinkingMessages.size());
		assertInstanceOf(AssistantMessage.class, thinkingMessages.get(0));
		assertFalse(() -> thinkingMessages.get(0).getText().contains("<think>"));
		assertInstanceOf(UserMessage.class, thinkingMessages.get(1));
		assertInstanceOf(AssistantMessage.class, thinkingMessages.get(2));
		assertTrue(() -> thinkingMessages.get(2).getText().contains("<think>"));
	}

	@Test
	void testToolsCustomJsonFormat() {
		List<Payload> payloads = new ArrayList<>();
		var config = new ToolCallConfig(null, SchemaFormat.JSON, null, null, null);
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper(), config) {
			@Tool
			public Mono<String> executeCommand(@ToolParam String command) {
				return EXECUTE_COMMAND.apply(command);
			}
		};
		var prompt = new Prompt("Execute ls -la command");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(5, payloads.size());
		assertInstanceOf(Thinking.class, payloads.get(0));
		assertInstanceOf(FuncCall.class, payloads.get(1));
		assertInstanceOf(FuncResult.class, payloads.get(2));
		assertInstanceOf(Thinking.class, payloads.get(3));
		assertInstanceOf(Text.class, payloads.get(4));
	}

	@Test
	void testToolsCustomJsonRfcFormat() {
		List<Payload> payloads = new ArrayList<>();
		var config = new ToolCallConfig(null, SchemaFormat.JSON_RFC, null, null, null);
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper(), config) {
			@Tool
			public Mono<String> executeCommand(@ToolParam String command) {
				return EXECUTE_COMMAND.apply(command);
			}
		};
		var prompt = new Prompt("Execute ls -la command");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(5, payloads.size());
		assertInstanceOf(Thinking.class, payloads.get(0));
		assertInstanceOf(FuncCall.class, payloads.get(1));
		assertInstanceOf(FuncResult.class, payloads.get(2));
		assertInstanceOf(Thinking.class, payloads.get(3));
		assertInstanceOf(Text.class, payloads.get(4));
	}

	@Test
	void testToolsEngine() {
		List<Payload> payloads = new ArrayList<>();
		var config = new ToolCallConfig(ToolCallHandler.ENGINE, null, null, null, null);
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper(), config) {
			@Tool
			public Mono<String> executeCommand(@ToolParam String command) {
				return EXECUTE_COMMAND.apply(command);
			}
		};
		var prompt = new Prompt("Execute ls -la command");
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.stream(prompt), payloads::addAll))
				.expectNextCount(1)
				.thenConsumeWhile(e -> true)
				.expectComplete()
				.verify();
		assertEquals(5, payloads.size());
		assertInstanceOf(Thinking.class, payloads.get(0));
		assertInstanceOf(FuncCall.class, payloads.get(1));
		assertInstanceOf(FuncResult.class, payloads.get(2));
		assertInstanceOf(Thinking.class, payloads.get(3));
		assertInstanceOf(Text.class, payloads.get(4));

		var messages = chatAgent.toMessages(payloads, false);
		assertEquals(3, messages.size());
		assertInstanceOf(AssistantMessage.class, messages.get(0));
		assertNull(messages.get(0).getText());
		assertInstanceOf(ToolResponseMessage.class, messages.get(1));
		assertInstanceOf(AssistantMessage.class, messages.get(2));
		assertFalse(() -> messages.get(2).getText().contains("<think>"));
		
		var thinkingMessages = chatAgent.toMessages(payloads, true);
		assertEquals(3, thinkingMessages.size());
		assertInstanceOf(AssistantMessage.class, thinkingMessages.get(0));
		assertNull(thinkingMessages.get(0).getText());
		assertInstanceOf(ToolResponseMessage.class, thinkingMessages.get(1));
		assertInstanceOf(AssistantMessage.class, thinkingMessages.get(2));
		assertTrue(() -> thinkingMessages.get(2).getText().contains("<think>"));
	}

	@Test
	void testStructuredYaml() {
		List<Payload> payloads = new ArrayList<>();
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper());
		var schema = ObjectSchemaGenerator.generateYamlSchema(Person.class);
		var prompt = new Prompt("Return an example JSON object generated using the following schema:\n" +
				schema);
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.parseJSON(chatAgent.stream(prompt), new TypeReference<Person>() {
		}), payloads::addAll))
			.expectNextCount(1)
			.thenConsumeWhile(e -> true)
			.expectComplete()
			.verify();
		assertEquals(1, payloads.size());
		assertInstanceOf(Structured.class, payloads.get(0));
		if (payloads.get(0) instanceof Structured<?> structured) {
			assertInstanceOf(Person.class, structured.data());
			assertNotNull(((Person) structured.data()).address());
			assertNotNull(((Person) structured.data()).educationHistory());
			assertTrue(((Person) structured.data()).educationHistory().size() > 0);
			assertNotNull(((Person) structured.data()).gender());
		}
	}

	@Test
	void testStructuredJson() {
		List<Payload> payloads = new ArrayList<>();
		var chatAgent = new BaseAgent(getChatClient(), new ObjectMapper());
		var schema = ObjectSchemaGenerator.generateSimpleJsonSchema(Person.class);
		var prompt = new Prompt("Return an example JSON object generated using the following schema:\n" +
				schema);
		StepVerifier.create(PayloadAggregator.aggregatePayloads(chatAgent.parseJSON(chatAgent.stream(prompt), new TypeReference<Person>() {
		}), payloads::addAll))
			.expectNextCount(1)
			.thenConsumeWhile(e -> true)
			.expectComplete()
			.verify();
		assertEquals(1, payloads.size());
		assertInstanceOf(Structured.class, payloads.get(0));
		if (payloads.get(0) instanceof Structured<?> structured) {
			assertInstanceOf(Person.class, structured.data());
			assertNotNull(((Person) structured.data()).address());
			assertNotNull(((Person) structured.data()).educationHistory());
			assertTrue(((Person) structured.data()).educationHistory().size() > 0);
			assertNotNull(((Person) structured.data()).gender());
		}
	}

	private ChatClient getChatClient() {
		var chatOptions = OpenAiChatOptions
				.builder()
				.model("qwen3-1.7b")
				.temperature(0.6)
				.internalToolExecutionEnabled(false)
				.build();
		var openAi = OpenAiApi.builder()
				.baseUrl("http://192.168.50.207:9090")
				.apiKey("4c98a7f7-a3a6-432f-8f0f-3ec91559f0d3")
				.build();
		var model = OpenAiChatModel.builder()
				.openAiApi(openAi)
				.defaultOptions(chatOptions)
				.build();
		return ChatClient.builder(model)
				.build();
	}
}
