import React from 'react';
import Terminal from './Terminal';
import { TOOL_NAMES, TOOL_DESCRIPTIONS, UI_TEXT } from '../../../AppConstants.tsx';

// Accept isVisible prop
const ToolAreaContainer = ({ activeTool, onToolSelect, isVisible }) => {

    const handleClose = (e) => {
        e.preventDefault();
        onToolSelect(null); // Use null to signal closing
    };

    // Conditionally apply 'tool-visible' class based on isVisible prop
    const containerClass = `tool-area-container ${isVisible ? 'tool-visible' : ''}`;

    return (
        <section
            id="tool-area-container"
            className={containerClass} // Use dynamic class
            aria-label="Tool Area"
            aria-hidden={!isVisible}
            // Removed inline style for box-shadow, will handle in CSS
        >
            {/* Header content remains, only shown when isVisible is true */}
            {isVisible && (
                <hgroup>
                    <div className="tool-area-header">
                        <h3 id="tool-title" style={{ margin: 0 }}>{TOOL_NAMES[activeTool] || UI_TEXT.toolArea}</h3>
                        <a
                            href="#"
                            className="secondary tool-close-link"
                            onClick={handleClose}
                            aria-label={UI_TEXT.close}
                            tabIndex={0} // Ensure it's focusable
                        >
                            ×
                        </a>
                    </div>
                    <p id="tool-description">{TOOL_DESCRIPTIONS[activeTool] || UI_TEXT.selectTool}</p>
                </hgroup>
            )}
            {/* Tool content area */}
            <article className="tool-content tool-content-article">
                {/* Conditionally render tools based on activeTool AND isVisible */}
                {/* Keep Terminal mounted but hidden */}
                <div className={`tool-content-item ${activeTool === 'tool1' && isVisible ? 'visible' : 'hidden'}`}>
                    <Terminal />
                </div>
                {/* Keep Tool 2 content mounted but hidden */}
                <div className={`tool-content-item ${activeTool === 'tool2' && isVisible ? 'visible' : 'hidden'}`}>
                    <p>{UI_TEXT.contentForCodeGenerator}</p>
                </div>
                {/* Placeholder content removed as it's implicitly handled by isVisible */}
            </article>
        </section>
    );
};

export default ToolAreaContainer;