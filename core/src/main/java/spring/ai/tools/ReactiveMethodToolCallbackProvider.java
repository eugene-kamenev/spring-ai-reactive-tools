package spring.ai.tools;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.util.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import reactor.core.publisher.Mono;

public class ReactiveMethodToolCallbackProvider implements ToolCallbackProvider {

	private final List<Object> toolObjects;

	 public ReactiveMethodToolCallbackProvider(List<Object> toolObjects) {
        Assert.notNull(toolObjects, "toolObjects cannot be null");
		Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
		this.toolObjects = toolObjects;
    }

    @Override
    public ReactiveMethodToolCallback[] getToolCallbacks() {
        var toolCallbacks = toolObjects.stream()
			.map(toolObject -> Stream
				.of(ReflectionUtils.getDeclaredMethods(
						AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
				.filter(toolMethod -> toolMethod.isAnnotationPresent(Tool.class))
				.filter(toolMethod -> isReactiveReturnType(toolMethod))
				.map(toolMethod -> ReactiveMethodToolCallback.builder()
					.toolDefinition(ToolDefinition.from(toolMethod))
					.toolMetadata(ToolMetadata.from(toolMethod))
					.toolMethod(toolMethod)
					.toolObject(toolObject)
					.toolCallResultConverter(ToolUtils.getToolCallResultConverter(toolMethod))
					.build())
				.toArray(ReactiveMethodToolCallback[]::new))
			.flatMap(Stream::of)
			.toArray(ReactiveMethodToolCallback[]::new);

		validateToolCallbacks(toolCallbacks);

		return toolCallbacks;
    }

    private boolean isReactiveReturnType(Method method) {
        Class<?> returnType = method.getReturnType();
        return ClassUtils.isAssignable(Mono.class, returnType);
    }

	private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalStateException("Multiple tools with the same name (%s) found in sources: %s".formatted(
					String.join(", ", duplicateToolNames),
					toolObjects.stream().map(o -> o.getClass().getName()).collect(Collectors.joining(", "))));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<Object> toolObjects;

		private Builder() {
		}

		public Builder toolObjects(Object... toolObjects) {
			Assert.notNull(toolObjects, "toolObjects cannot be null");
			this.toolObjects = Arrays.asList(toolObjects);
			return this;
		}

		public ReactiveMethodToolCallbackProvider build() {
			return new ReactiveMethodToolCallbackProvider(toolObjects);
		}

	}
}
