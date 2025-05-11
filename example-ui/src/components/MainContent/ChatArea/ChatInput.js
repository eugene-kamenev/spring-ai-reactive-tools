import React, { useState, useRef, useEffect, useCallback, useContext } from 'react';
import { ChatContext } from '../../../App';

const ChatInput = ({}) => {
    const [inputValue, setInputValue] = useState('');
    const inputRef = useRef(null);
    const { addMessage } = useContext(ChatContext);
    // Resize textarea to fit content
    const resizeTextarea = useCallback(() => {
        const textarea = inputRef.current;
        if (textarea) {
            textarea.style.height = 'auto';
            textarea.style.height = textarea.scrollHeight + 'px';
        }
    }, []);

    useEffect(() => {
        resizeTextarea();
    }, [inputValue, resizeTextarea]);

    useEffect(() => {
        if (inputRef.current) {
            inputRef.current.focus();
            resizeTextarea();
        }
    }, [resizeTextarea]);

    const handleInputChange = (e) => {
        setInputValue(e.target.value);
    };

    const handleSubmit = () => {
        if (inputValue.trim()) {
            addMessage(inputValue, 'user');
            setInputValue('');
            setTimeout(resizeTextarea, 0); // Reset height after clearing
            if (inputRef.current) {
                inputRef.current.focus();
            }
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit();
        }
    };

    return (
        <footer className="chat-input-area">
            <div className="chat-input-container">
                <div className="chat-input-flex-wrapper">
                    <textarea
                        ref={inputRef}
                        id="message-input"
                        placeholder="Type your message..."
                        aria-label="Chat message input"
                        value={inputValue}
                        onChange={handleInputChange}
                        onKeyDown={handleKeyDown}
                        rows={1}
                        className="chat-input-textarea"
                        style={{
                            overflow: 'hidden',
                            resize: 'none',
                            width: '100%',
                            minHeight: '2.2em',
                            maxHeight: '15em',
                        }}
                    />
                    <div className="chat-input-actions chat-input-actions-absolute">
                        <button
                            id="send-button"
                            aria-label="Send message"
                            onClick={handleSubmit}
                            disabled={!inputValue.trim()}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
                                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                            </svg>
                        </button>
                    </div>
                </div>
            </div>
        </footer>
    );
};

export default ChatInput;