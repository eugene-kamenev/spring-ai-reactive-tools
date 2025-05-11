import React from 'react';
import AgentSwitcher from './AgentSwitcher';
import { TOOL_NAMES, UI_TEXT } from '../../AppConstants.tsx';

const toolIcons = {
    tool1: <span className="tool-icon" role="img" aria-label="Terminal">🖥️</span>,
    tool2: <span className="tool-icon" role="img" aria-label="Code Generator">💻</span>
};

const Sidebar = ({ activeTool, onToolSelect, currentAgent, onAgentChange }) => {
    const handleLinkClick = (e, toolId) => {
        e.preventDefault();
        onToolSelect(activeTool === toolId ? null : toolId);
    };

    return (
        <aside className="sidebar">
            <AgentSwitcher currentAgent={currentAgent} onAgentChange={onAgentChange} />
            <hgroup>
                <h3>{UI_TEXT.toolsMenu}</h3>
            </hgroup>
            <nav>
                <ul>
                    <li>
                        <a
                            href="#"
                            className={`contrast ${activeTool === 'tool1' ? 'active' : ''}`}
                            onClick={e => handleLinkClick(e, 'tool1')}
                            aria-label={TOOL_NAMES['tool1'] || UI_TEXT.terminal}
                            aria-pressed={activeTool === 'tool1'}
                        >
                            {toolIcons['tool1']}
                            <span className="sidebar-tool-label">{TOOL_NAMES['tool1'] || UI_TEXT.terminal}</span>
                        </a>
                    </li>
                </ul>
            </nav>
        </aside>
    );
};

export default Sidebar;