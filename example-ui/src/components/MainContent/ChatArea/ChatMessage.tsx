import ReactMarkdown from 'react-markdown';
import React, { useState, useContext } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { ChatContext } from '../../../App';
import { ThinkingEvent, UI_TEXT } from '../../../AppConstants';

type CodeBlockProps = {
    language: string;
    value: string;
};

const CodeBlock = ({ language, value }: CodeBlockProps) => {
    const [copied, setCopied] = useState(false);

    const handleCopy = () => {
        navigator.clipboard.writeText(value);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="code-block">
            <button
                onClick={handleCopy}
                className={`copy-btn${copied ? ' copied' : ''}`}
                aria-label={copied ? UI_TEXT.copied : UI_TEXT.copy}
                tabIndex={0}
            >
                {copied ? UI_TEXT.copied : UI_TEXT.copy}
            </button>
            <SyntaxHighlighter
                language={language}
                style={vscDarkPlus}
                customStyle={{
                    borderRadius: '8px',
                    outline: copied ? '2px solid #2ecc40' : 'none',
                    transition: 'outline 0.2s'
                }}
                wrapLines={true}>
                {value}
            </SyntaxHighlighter>
        </div>
    );
};

const LoadingSpinner = () => (
    <div className="spinner" aria-label={UI_TEXT.loading}>
        <div className="spinner-circle"></div>
    </div>
);

type FunctionCallExecuteCommandProps = {
    arguments: any;
    result: any;
    isLoading: boolean;
    isLast: boolean;
};

const FunctionCallExecuteCommand = ({ arguments: args, result, isLoading, isLast }: FunctionCallExecuteCommandProps) => (
    <div className="function-call-card terminal">
        <div>
            <strong>{UI_TEXT.terminalCommand}</strong>
        </div>
        <div>
            <span>{UI_TEXT.command}</span><pre>{args.command}</pre>
        </div>
        {isLoading ? (
            <LoadingSpinner />
        ) : (
            result && (
                <div>
                    <span>{UI_TEXT.output}</span> <pre>{result}</pre>
                </div>
            )
        )}
    </div>
);

type FunctionCallAskQuestionProps = {
    arguments: any;
    result: any;
    isLoading: boolean;
    isLast: boolean;
};

const FunctionCallAskQuestion = ({ arguments: args, result, isLoading, isLast }: FunctionCallAskQuestionProps) => {
    const { addMessage } = useContext(ChatContext);
    const [selected, setSelected] = useState<string | null>(null);

    const handleSubmit = () => {
        if (selected !== null && isLast) {
            addMessage(selected, 'user', 'USER_CHOICE');
        }
    };

    return (
        <div className="function-call-card question">
            <div>
                <strong>{UI_TEXT.question}</strong>
            </div>
            <div className="question-text">
                {args.question}
            </div>
            {args.answerChoices?.length > 0 &&  (
                <form
                    onSubmit={e => {
                        e.preventDefault();
                        handleSubmit();
                    }}
                >
                    <div>
                        <ul className="answer-choices">
                            {args.answerChoices.map((choice: string, index: number) => (
                                <li key={index}>
                                    <label className={!isLast ? 'disabled-choice' : ''}>
                                        <input
                                            type="radio"
                                            name="answerChoice"
                                            value={choice}
                                            checked={selected === choice}
                                            onChange={() => setSelected(choice)}
                                            disabled={!isLast}
                                        />
                                        {choice}
                                    </label>
                                </li>
                            ))}
                        </ul>
                        <em>{UI_TEXT.selectAnswer}</em>
                    </div>
                    <button
                        type="submit"
                        disabled={selected === null || !isLast}
                        className={`submit-btn${!isLast ? ' submitted' : ''}`}
                    >
                        {!isLast ? UI_TEXT.submitted : UI_TEXT.submit}
                    </button>
                </form>
            )}
        </div>
    );
};

const functionCallViews: Record<string, React.FC<any>> = {
    'executeCommand': FunctionCallExecuteCommand,
    'askQuestion': FunctionCallAskQuestion
};

type FunctionCallViewProps = {
    name: string;
    arguments: any;
    isLoading: boolean;
    isLast: boolean;
    result: any;
    timestamp: number;
};

const FunctionCallView = (props: FunctionCallViewProps) => {
    const { name } = props;
    const ViewComponent = functionCallViews[name];
    return ViewComponent ? (<ViewComponent {...props} />) : null;
};

const infoBarStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    background: '#23272e',
    borderRadius: '7px',
    padding: '0.3em 0.8em',
    fontSize: '0.93em',
    color: '#bfc7d5',
    margin: '0.3em 0',
    gap: '0.7em',
    boxShadow: '0 1px 4px rgba(0,0,0,0.07)'
};

const infoBarTextStyle: React.CSSProperties = {
    fontSize: '0.93em',
    color: '#bfc7d5',
    fontWeight: 500,
    display: 'flex',
    alignItems: 'center',
    gap: '0.4em'
};

const ThinkingEventView = (event: ThinkingEvent) => {
    const [isOpen, setIsOpen] = useState(false);

    return (
        <details className="thinking-section" open={isOpen} onToggle={(e) => setIsOpen((e.target as HTMLDetailsElement).open)}>
            <summary className="thinking-summary">
                <span className="thinking-title">{UI_TEXT.thinking}</span>
                {!event.isLast && <LoadingSpinner />}
            </summary>
            <div className="thinking-content">
            <ReactMarkdown
                    components={{
                        code({ node, inline, className, children, ...props }: any) {
                            const match = /language-(\w+)/.exec(className || '');
                            return !inline && match ? (
                                <CodeBlock
                                    language={match[1]}
                                    value={String(children).replace(/\n$/, '')}
                                    {...props}
                                />
                            ) : (
                                <code className={className} {...props}>
                                    {children}
                                </code>
                            );
                        }
                    }}
                >
                    {event.text}
                </ReactMarkdown>
            </div>
        </details>
    );
};

const renderEntity = (entity: any, message: any, isLast: boolean) => {
    switch (entity.type) {
        case "TXT":
            return (
                <ReactMarkdown
                    components={{
                        code({ node, inline, className, children, ...props }: any) {
                            const match = /language-(\w+)/.exec(className || '');
                            return !inline && match ? (
                                <CodeBlock
                                    language={match[1]}
                                    value={String(children).replace(/\n$/, '')}
                                    {...props}
                                />
                            ) : (
                                <code className={className} {...props}>
                                    {children}
                                </code>
                            );
                        }
                    }}
                >
                    {entity.text}
                </ReactMarkdown>
            );
        case "FC":
            return (
                <FunctionCallView
                    name={entity.name}
                    arguments={entity.arguments}
                    isLoading={message.isLoading}
                    isLast={isLast}
                    result={entity.result}
                    timestamp={message.timestamp}
                />
            );
        case "TH":
            return (
                <ThinkingEventView
                    {...entity as ThinkingEvent} />
            );
        default:
            return null;
    }
};

type ChatMessageProps = {
    message: any;
};

const ChatMessage = ({ message }: ChatMessageProps) => {
    const { sender, timestamp, isLast, parts = [], isLoading } = message;

    if (isLoading && (!parts || parts.length === 0 || (parts.length === 1 && parts[0].type === "TXT" && !parts[0].text))) {
        return (
            <div className={`message ${sender}`}>
                <div className="content">
                    <LoadingSpinner />
                </div>
            </div>
        );
    }

    return (
        <div className={`message ${sender}`}>
            <div className="content">
                <div className="message-header">
                    <span
                        className={`avatar ${sender}`}
                        aria-label={sender}
                    >
                        {sender === 'user' ? UI_TEXT.user : UI_TEXT.bot}
                    </span>
                    <span className="message-time">
                        {timestamp ? new Date(timestamp).toLocaleTimeString() : ''}
                    </span>
                </div>
                {parts && parts.map((entity: any, idx: number) => (
                    <div key={idx} className="message-part">
                        {renderEntity(entity, message, isLast)}
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ChatMessage;
