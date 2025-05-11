import React, { useState, useEffect, useRef } from 'react';
import { AGENTS, UI_TEXT } from '../../AppConstants.tsx';

const AgentSwitcher = ({ currentAgent, onAgentChange }) => {
    const [isOpen, setIsOpen] = useState(false);
    const detailsRef = useRef(null);

    const handleAgentClick = (e, agent) => {
        e.preventDefault();
        onAgentChange(agent);
        setIsOpen(false);
        if (detailsRef.current) {
            const summary = detailsRef.current.querySelector('summary');
            if (summary) summary.blur();
        }
    };

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (detailsRef.current && !detailsRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    return (
        <details
            ref={detailsRef}
            className="dropdown agent-switcher"
            open={isOpen}
            onToggle={e => setIsOpen(e.target.open)}
            style={{ width: '100%', marginBottom: '0.7em' }}
        >
            <summary role="button" className="secondary outline" style={{ width: '100%', borderRadius: '10px', padding: '0.5em 0.7em', fontWeight: 600 }}>
                <span style={{ fontSize: '1.1em' }}>{UI_TEXT.agent}</span>
            </summary>
            <ul style={{ minWidth: '120px', borderRadius: '10px', marginTop: '0.2em' }}>
                {Object.entries(AGENTS).map(([key, label]) => (
                    <li key={key}>
                        <a
                            href="#"
                            onClick={e => handleAgentClick(e, key)}
                            className={currentAgent === key ? 'agent-active' : ''}
                            style={{
                                fontWeight: currentAgent === key ? 700 : 400,
                                color: currentAgent === key ? '#007bff' : undefined,
                                borderRadius: '8px'
                            }}
                        >
                            {label}
                        </a>
                    </li>
                ))}
            </ul>
        </details>
    );
};

export default AgentSwitcher;
