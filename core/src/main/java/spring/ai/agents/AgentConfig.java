package spring.ai.agents;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import spring.ai.agents.Agent.Event.FuncResult;
import spring.ai.schema.SchemaFormat;

@ConfigurationProperties(prefix = "agents")
public record AgentConfig(ToolCallConfig tools, Map<String, Resource> prompts) {

    public AgentConfig {
        tools = tools != null ? tools : ToolCallConfig.DEFAULT;
        prompts = prompts != null ? prompts : Map.of();
    }

    public record ToolCallConfig(
        ToolCallHandler provider,
        // Tools schema rendering format
        SchemaFormat schemaFormat,
        // If true, injects schema with prompt to the very first message
        Boolean injectSchema,
        // Prompt template for tool schema injection
        Resource toolsPrompt,
        // Message roles configuration
        Roles roles
    ) {

        public record Roles(
            // (USER|SYSTEM) message type for tools prompt injection
            MessageType prompt,
            // (TOOL|USER) message type for tool results
            MessageType response,
            // (CUSTOM|ENGINE) tool calls are provided back to engine in assistant message: as text message (CUSTOM), or in request (ENGINE)
            ToolCallHandler call
        ) {
            public static final Roles DEFAULT = new Roles(null,null,null);

            public Roles {
                prompt = prompt != null ? prompt : MessageType.USER;
                response = response != null ? response : MessageType.USER;
                call = call != null ? call : ToolCallHandler.CUSTOM;
            }
        }

        public ToolCallConfig {
            provider = provider != null ? provider : ToolCallHandler.CUSTOM;
            schemaFormat = schemaFormat != null ? schemaFormat : SchemaFormat.YAML;
            roles = roles != null ? roles : provider == ToolCallHandler.CUSTOM ? Roles.DEFAULT : new Roles(Roles.DEFAULT.prompt(), MessageType.TOOL, provider);
            toolsPrompt = toolsPrompt != null ? toolsPrompt : new ClassPathResource("agents/prompts/default-tools.md");
            injectSchema = injectSchema != null ? injectSchema : true;
        }

        public static final ToolCallConfig DEFAULT = new ToolCallConfig(null,null, null, null, null);

        public Message toolResponse(ChatResponse chatResponse) {
            if (MessageType.USER != this.roles.response()) {
                return new ToolResponseMessage(
                        List.of(new ToolResponse("", "", chatResponse.getResult().getOutput().getText())));
            } else {
                return new UserMessage(chatResponse.getResult().getOutput().getText());
            }
        }

        public List<Message> toolResponse(FuncResult func, ObjectMapper mapper) {
            var toolCallMessage = isProviderEngine() ?
                    new AssistantMessage(null, Map.of(), List.of(new ToolCall(func.call().id(), "function", func.call().name(), Utils.write(mapper, func.call().arguments())))) :
                    new AssistantMessage(Utils.write(mapper, func.call()));
            var toolResponseMessage = MessageType.USER != this.roles.response() ?
                    new ToolResponseMessage(List.of(new ToolResponse(func.call().id(), func.call().name(), func.result()))) :
                    new UserMessage(func.result());
            return List.of(toolCallMessage, toolResponseMessage);
        }

        public Message toolsPrompt(String toolSchema) {
            if (MessageType.USER != this.roles.prompt()) {
                return new SystemPromptTemplate(toolsPrompt)
                        .createMessage(Map.of("tools", toolSchema));
            } else {
                return new PromptTemplate(toolsPrompt)
                        .createMessage(Map.of("tools", toolSchema));
            }
        }

        public boolean isProviderEngine() {
            return ToolCallHandler.CUSTOM != provider;
        }
    }

    public enum ToolCallHandler {
        /**
         * Chat agent will not inject tools into request, will use system/user message for tools prompt,
         * will do parse tool calls manually, engine/model agnostic
         */
        CUSTOM,
        
        /**
         * LLM engine and Model do support tools/tool calls parsing and will respond with valid ToolCalls
         */
        ENGINE
    }

    public SystemPromptTemplate getSystemPrompt(String name) {
        return new SystemPromptTemplate(prompts.get(name));
    }

    public PromptTemplate getPrompt(String name) {
        return new PromptTemplate(prompts.get(name));
    }

    public String getAsString(String name) {
        var resource = prompts.get(name);
        if (resource == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, Resource> getPrompts() {
        return prompts;
    }
}
