export const AGENTS: Record<string, string> = {
    'basic-chat': 'Basic Chat',
    'terminal-agent': 'Tool Agent',
};

export const DEFAULT_AGENT = 'solution-finder';

export const DEFAULT_MESSAGES = [
    { text: 'I am Basic Chat agent. How can I help you today?', sender: 'bot' }
];

export const TOOL_NAMES = {
    tool1: "Terminal",
};

export const TOOL_DESCRIPTIONS = {
    tool1: "Interact via terminal.",
    tool2: "Generate code snippets."
};

export const UI_TEXT = {
    toolsMenu: "Tools Menu",
    terminal: "Terminal",
    codeGenerator: "Code Generator",
    toolArea: "Tool Area",
    selectTool: "Select a tool from the menu.",
    interactTerminal: "Interact via terminal.",
    generateCode: "Generate code snippets.",
    close: "Close",
    agent: "Agent",
    switchedToAgent: (agent: string) => `Switched to ${AGENTS[agent] || agent} agent. How can I help you?`,
    solutionFinderGreeting: "I am Solution Finder agent. How can I help you today?",
    contentForCodeGenerator: "Content for Code Generator will appear here.",
    terminalCommand: "Terminal command",
    command: "Command:",
    output: "Output:",
    question: "Question:",
    selectAnswer: "Select an Answer (or answer directly in the message input):",
    submit: "Submit",
    submitted: "Submitted",
    function: "Function:",
    arguments: "Arguments:",
    result: "Result:",
    tool: "T",
    user: "U",
    bot: "B",
    loading: "Loading...",
    copy: "Copy",
    copied: "Copied!",
    thinking: "Thinking...",
};

export const TERMINAL_TEXT = {
    connecting: "Connecting to terminal service...",
    error: "Error connecting to terminal service",
    closed: "Terminal connection closed"
};

export const MESSAGE_TYPES = {
    userMessage: 'USR',
    userChoice: 'USER_CHOICE',
    text: 'TXT',
    funcCall: 'FC',
    funcResult: 'FR',
    terminal: 'TERMINAL'
};

export const EVENT_TYPES = {
    open: 'open',
    message: 'message',
    error: 'error',
    close: 'close'
};

// Specific entity interfaces with discriminated 'type'
export interface WithText {
    text: string;
    isLast: boolean;
}

export interface TextEvent extends WithText {
    type: "TXT";
};

export interface FuncCall {
    type: "FC";
    name: string;
    arguments: Record<string, any>;
    result: string;
};

export interface FuncResult {
    type: "FR";
    name: string;
    result: string;
};

export interface TerminalEvent {
    type: "TERMINAL";
    output: string;
    columns: number;
    rows: number;
};

export interface ThinkingEvent extends WithText {
    type: "TH";
}

// Union type of all possible entities
export type Entity = TextEvent | FuncCall | FuncResult | TerminalEvent | ThinkingEvent;

// Event interface uses the union type for entity
export interface Event {
    id: string,
    entity: Entity,
}

export interface Message {
    id: string,
    sender: 'bot' | 'user',
    timestamp: number,
    isLoading: boolean,
    parts: Entity[],
};

export function addEvent(messages: Message[], event: Event, sender: 'bot' | 'user') : Message[] {
    const updatedMessages = [...messages];
    const last = updatedMessages.length > 0 ? updatedMessages[updatedMessages.length - 1] : null;
    if (last && last.id === event.id) {
        const parts = [...last.parts];
        const lastPart = parts[parts.length - 1];
        if (lastPart.type === "TXT" && event.entity.type === "TXT") {
            lastPart.text += (event.entity.text ? event.entity.text : "");
            lastPart.isLast = event.entity.isLast;
            parts[parts.length - 1] = {...lastPart};
        } else if (lastPart.type === "TH" && event.entity.type === "TH") {
            lastPart.text += (event.entity.text ? event.entity.text : "");
            lastPart.isLast = event.entity.isLast;
            parts[parts.length - 1] = {...lastPart};
        } else if (lastPart.type === "FC" && event.entity.type === "FR") {
            parts[parts.length - 1] = {...lastPart, result: event.entity.result};
        } else {
            parts.push({...event.entity});
        }
        updatedMessages[updatedMessages.length - 1] = {
            ...last,
            isLoading: false,
            parts: parts
        };
    } else if (last && last.id === null && last.isLoading) {
        updatedMessages[updatedMessages.length - 1] = {
            ...last,
            id: event.id,
            isLoading: false,
            parts: [{...event.entity}]
        };
    } else {
        updatedMessages.push({
            id: event.id,
            sender: sender,
            timestamp: Date.now(),
            parts: [{...event.entity}],
            isLoading: false
        });
    }
    return updatedMessages;
}
