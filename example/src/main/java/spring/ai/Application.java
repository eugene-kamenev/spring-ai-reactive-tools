package spring.ai;

import java.io.IOException;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import spring.ai.agents.AgentConfig;
import spring.ai.events.Events;

@SpringBootApplication
@EnableConfigurationProperties(AgentConfig.class)
public class Application {

  static {
    // this is not required if your LLM Engine responds correctly with non empty finish reason, unfortunately for example Local.AI does not
    class ChatCompletionFinishReasonDeserializer extends JsonDeserializer<OpenAiApi.ChatCompletionFinishReason> {

      @Override
      public OpenAiApi.ChatCompletionFinishReason deserialize(JsonParser p, DeserializationContext ctxt)
          throws IOException {
        String value = p.getValueAsString();

        if (value == null || value.trim().isEmpty()) {
          // Return a default value or handle it gracefully
          return null;
        }

        try {
          return OpenAiApi.ChatCompletionFinishReason.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
          throw new IOException("Invalid finish reason: " + value, e);
        }
      }
    }
    var module = new SimpleModule();
    module.addDeserializer(OpenAiApi.ChatCompletionFinishReason.class, new ChatCompletionFinishReasonDeserializer());
    ModelOptionsUtils.OBJECT_MAPPER.registerModule(module);
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  OpenAiChatOptions chatOptions() {
    return OpenAiChatOptions.builder()
        .model("qwen3-4b")
        .internalToolExecutionEnabled(false)
        .build();
  }

  @Bean
  ObjectMapper jsonMapper() {
    var mapper = JsonMapper.builder()
        .addModules(JacksonUtils.instantiateAvailableModules())
        .enable(JsonParser.Feature.ALLOW_COMMENTS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
        .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();
    Events.register(mapper);
    return mapper;
  }
}
