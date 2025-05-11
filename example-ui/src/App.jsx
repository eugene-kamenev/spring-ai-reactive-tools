import React, { useState, useEffect, useCallback, useRef, createContext } from 'react';
import Sidebar from './components/Sidebar/Sidebar';
import ToolAreaContainer from './components/MainContent/ToolArea/ToolAreaContainer';
import ChatHistory from './components/MainContent/ChatArea/ChatHistory';
import ChatInput from './components/MainContent/ChatArea/ChatInput';
import WebSocketService from './services/WebSocketService';
import { AGENTS, DEFAULT_AGENT, DEFAULT_MESSAGES, UI_TEXT, MESSAGE_TYPES, EVENT_TYPES, addEvent } from './AppConstants.tsx';
import './App.css';

export const ChatContext = createContext();

function App() {
    const ws = useRef(WebSocketService.getInstance());
    const [activeTool, setActiveTool] = useState(null);
    const [currentAgent, setCurrentAgent] = useState(DEFAULT_AGENT);
    const theme = 'dark';
    const [messages, setMessages] = useState([
        ...DEFAULT_MESSAGES.map((msg, idx) => ({
            id: `init-${idx}`,
            sender: msg.sender,
            timestamp: Date.now() + idx,
            isLoading: false,
            parts: [
                { type: "TXT", text: msg.text }
            ]
        }))
    ]);

    useEffect(() => {
        ws.current.addListener((eventType, data) => {
            if (eventType === EVENT_TYPES.message && data && data.entity.type !== 'TERMINAL') {
                setMessages(prevMessages => addEvent(prevMessages, data, 'bot'));
            }
        });
        return () => ws.current.removeListener();
    }, []);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme)
        localStorage.setItem('pico-theme', theme);
    }, [theme]);

    const sendWsMessage = (event) => {
        ws.current.send({
            entity: event,
            metadata: { agent: currentAgent }
        });
    };

    const addMessage = useCallback((text, sender = 'bot', type = MESSAGE_TYPES.userMessage) => {
        if (!text.trim()) return;
        if (sender === 'user') {
            setMessages(prev => {
                const last = prev.at(-1);
                const event = {
                    id: `user-${Date.now()}`,
                    entity: {
                        type: "TXT",
                        text
                    }
                };
                const updated = addEvent(prev, event, 'user');
                if (last && last.sender === 'bot') {
                    const lastPart = last.parts.at(-1);
                    if (lastPart && lastPart.type === 'FC' && lastPart.name === "askQuestion") {
                        type = MESSAGE_TYPES.userChoice;
                    }
                }
                sendWsMessage({ type: type, text: text });
                return [
                    ...updated,
                    {
                        id: null,
                        sender: 'bot',
                        timestamp: Date.now(),
                        isLoading: true,
                        parts: []
                    }
                ];
            });
        }
    }, [currentAgent]);

    const handleToolSelect = useCallback((toolId) => {
        setActiveTool(prevTool => (prevTool === toolId ? null : toolId));
    }, []);

    const handleAgentChange = useCallback((agent) => {
        setCurrentAgent(agent);
        setMessages([
            {
                id: `agent-switch-${Date.now()}`,
                sender: 'bot',
                timestamp: Date.now(),
                isLoading: false,
                parts: [{ type: "TXT", text: UI_TEXT.switchedToAgent(agent) }]
            }
        ]);
    }, []);

    return (
        <ChatContext.Provider value={{ addMessage }}>
            <div className="app-container" style={{ display: 'flex', height: '100vh' }}>
                <div style={{ flex: '0 0 250px', borderRight: '1px solid #ccc' }}>
                    <Sidebar
                        activeTool={activeTool}
                        onToolSelect={handleToolSelect}
                        currentAgent={currentAgent}
                        onAgentChange={handleAgentChange}
                    />
                </div>
                <div
                    className="main-content-wrapper"
                    style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
                >
                    <section
                        id="chat-area"
                        className="chat-area"
                        aria-label="Chat Area"
                        style={{
                            flex: 1,
                            display: 'flex',
                            flexDirection: 'column',
                            minHeight: 0
                        }}
                    >
                        <ChatHistory messages={messages} />
                        <ChatInput />
                    </section>
                    <ToolAreaContainer
                        activeTool={activeTool}
                        onToolSelect={handleToolSelect}
                        isVisible={!!activeTool}
                    />
                </div>
            </div>
        </ChatContext.Provider>
    );
}

export default App;