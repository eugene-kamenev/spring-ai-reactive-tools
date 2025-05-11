package spring.ai.tools;

public class ReactiveToolCallbacks {

	private ReactiveToolCallbacks() {
	}

	public static ReactiveMethodToolCallback[] from(Object... sources) {
		return ReactiveMethodToolCallbackProvider.builder().toolObjects(sources).build().getToolCallbacks();
	}
}
