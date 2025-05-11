package spring.ai.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;

import reactor.core.publisher.Mono;

public interface ReactiveToolCallback extends ToolCallback {
    
    Mono<String> callNonBlocking(String input);

	default Mono<String> callNonBlocking(String toolInput, @Nullable ToolContext tooContext) {
		if (tooContext != null && !tooContext.getContext().isEmpty()) {
			throw new UnsupportedOperationException("Tool context is not supported!");
		}
		return callNonBlocking(toolInput);
	}
}
