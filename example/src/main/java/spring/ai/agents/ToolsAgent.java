package spring.ai.agents;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import spring.ai.agents.base.BaseAgent;
import spring.ai.events.EventService;
import spring.ai.events.Events;
import spring.ai.tools.terminal.TerminalService;

public class ToolsAgent extends BaseAgent {

    private final TerminalService terminalService;
    private final EventService eventService;

    public ToolsAgent(ChatClient client, EventService eventService, ObjectMapper mapper,
        TerminalService terminalService, AgentConfig agentConfig) {
        super(client, mapper, agentConfig.tools());
        this.terminalService = terminalService;
        this.eventService = eventService;
    }

    @Tool(description = "Use executeCommand to execute CLI commands in the terminal")
    public Mono<String> executeCommand(@ToolParam(description = "CLI command to be executed") String command) {
        var commandToExceute = command;
        if (!command.endsWith("\n")) {
            commandToExceute += "\n";
        }
        return this.terminalService
            .commandWithOutput(commandToExceute)
            .map(o -> "executeCommand output:\n\n" + o);
    }

    @Tool(description = "Use this tool to ask any question you have to user and get the answers")
    public Mono<String> askQuestion(String question, @ToolParam(description = "Optional list of choices to answer the question") List<String> answerChoices) {
        return this.eventService.inputStream()
            .filter(e -> e.entity() instanceof Events.UserAnswer)
            .map(e -> ((Events.UserAnswer) e.entity()).text())
            .next();
    }
}
