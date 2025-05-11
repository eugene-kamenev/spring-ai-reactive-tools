package spring.ai.agents.base;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

public class PromptWithTools extends Prompt {

	public PromptWithTools(List<Message> messages) {
		this(messages, null);
	}

	public PromptWithTools(List<Message> messages, ChatOptions options) {
		super(messages, options);
	}
}
