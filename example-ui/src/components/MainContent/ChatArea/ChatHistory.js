import React, { useEffect, useRef } from 'react';
import ChatMessage from './ChatMessage.tsx';

const ChatHistory = ({ messages }) => {
    const historyEndRef = useRef(null);

    useEffect(() => {
        historyEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    return (
        <div id="chat-history" className="chat-history" aria-live="polite" tabIndex={0}>
            {messages.map((msg, index) => (
                <ChatMessage key={msg.id || index} message={{ ...msg, isLast: index === messages.length - 1 }} />
            ))}
            <div ref={historyEndRef} />
        </div>
    );
};

export default ChatHistory;