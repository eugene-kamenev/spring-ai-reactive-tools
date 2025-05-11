## spring-ai-reactive-tools 

Example project which implements reactive tool calls for Spring AI (OpenAI client only).

## Features
- Transforms OpenAI ChatResponse into clear events (Thinking, Text, FuncCall, FuncResult, Structured etc.)
- Supports manual and engine based tool call processing (when model/engine does not support function calling)
- Supports different tool schema formats: **YAML**, **JSON**, **JSON-RFC** (JSON is a relaxed version of JSON-RFC)
- Supports parsing valid JSON structured entities from free streaming text  

## Example Usage
```java
var agent = new BaseAgent(client, mapper, config) {
    @Tool
    public Mono<String> getWeather(String city, String unit) {
        return Mono.just("Weather in " + city + " is 20 degrees " + unit);
    }
};
var payloads = new ArrayList<Payload>();
var stream = agent.stream(new Prompt("What is the weather in London?"));

// wrap with PayloadAggregator to collect all payloads
// stream = PayloadAggregator.aggregatePayloads(stream, payloads::addAll); 
// etc..
```

## Example Project
Review example project which includes conversation history handling, interactive terminal in the browser linked with agent for tool calls.
[Demo](https://github.com/eugene-kamenev/spring-ai-reactive-tools/blob/main/video/reactive-tools.webm)