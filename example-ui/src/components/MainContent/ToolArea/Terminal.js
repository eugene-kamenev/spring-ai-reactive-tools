import React, { useEffect, useRef, useState } from 'react';
import { Terminal as XTerm } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';
import './Terminal.css';
import WebSocketService from '../../../services/WebSocketService';
import { TERMINAL_TEXT, EVENT_TYPES, MESSAGE_TYPES } from '../../../AppConstants.tsx';

const Terminal = () => {
  const terminalRef = useRef(null);
  const xtermRef = useRef(null);
  const webSocketService = useRef(WebSocketService.getInstance());
  const [isReady, setIsReady] = useState(false); // Track if terminal is ready and visible

  useEffect(() => {
    let term = null;
    let fitAddon = null;
    let resizeObserver = null;
    let hasSentSize = false; // Prevent duplicate sends

    // Initialize the terminal
    term = new XTerm({
      cursorBlink: true,
      fontSize: 14,
      theme: {
        background: '#1e1e1e',
        foreground: '#f0f0f0'
      }
    });

    // Initialize fit addon to make terminal resize to container
    fitAddon = new FitAddon();
    term.loadAddon(fitAddon);

    // Open the terminal in the container
    if (terminalRef.current) {
      term.open(terminalRef.current);
      // Do not fit or send size yet, wait for visibility
      xtermRef.current = { term, fitAddon };
    }

    // Helper to fit and send terminal size if visible
    const fitAndSendSize = () => {
      if (!fitAddon || !term || !terminalRef.current) return;
      // Only fit if container is visible and has height
      const rect = terminalRef.current.getBoundingClientRect();
      if (rect.height > 0 && rect.width > 0) {
        fitAddon.fit();
        // Only send once per visibility
        if (!hasSentSize && webSocketService.current.isSocketConnected()) {
          webSocketService.current.send({
            entity: {
              type: MESSAGE_TYPES.terminal,
              columns: term.cols.toString(),
              rows: term.rows.toString()
            }
          });
          hasSentSize = true;
        }
      }
    };

    // Observe size changes to detect when terminal becomes visible
    if (terminalRef.current) {
      resizeObserver = new window.ResizeObserver(() => {
        fitAndSendSize();
      });
      resizeObserver.observe(terminalRef.current);
    }

    // WebSocket event handler
    const handleWebSocketEvent = (event, data) => {
      switch (event) {
        case EVENT_TYPES.open:
          // Do not send terminal size here; wait for fitAndSendSize
          hasSentSize = false; // Allow sending again on reconnect
          fitAndSendSize();
          break;
        case EVENT_TYPES.message:
          if (data.entity.type === MESSAGE_TYPES.terminal) {
            term.write(data.entity.output);
          }
          break;
        case EVENT_TYPES.error:
          if (term) {
            term.write(`\r\n\x1b[31m${TERMINAL_TEXT.error}\x1b[0m\r\n`);
          }
          break;
        case EVENT_TYPES.close:
          if (term) {
            term.write(`\r\n\x1b[31m${TERMINAL_TEXT.closed}\x1b[0m\r\n`);
          }
          break;
      }
    };

    // Add WebSocket event listener
    webSocketService.current.addListener(handleWebSocketEvent);

    // Handle input
    if (term) {
      term.onData(data => {
        webSocketService.current.send({
          entity: {
            type: MESSAGE_TYPES.terminal,
            output: data
          }
        });
      });
    }

    // Handle window resize
    const handleResize = () => {
      if (fitAddon && term) {
        fitAddon.fit();
        if (webSocketService.current.isSocketConnected() && term.cols && term.rows) {
          webSocketService.current.send({
            entity: {
              type: MESSAGE_TYPES.terminal,
              columns: term.cols.toString(),
              rows: term.rows.toString()
            }
          });
        }
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      // Cleanup
      window.removeEventListener('resize', handleResize);
      if (resizeObserver && terminalRef.current) {
        resizeObserver.unobserve(terminalRef.current);
        resizeObserver.disconnect();
      }
      if (term) term.dispose();
      webSocketService.current.removeListener(handleWebSocketEvent);
    };
  }, []);

  return (
    <div id="terminal-container">
      <div
        ref={terminalRef}
        className="terminal"
        style={{ flex: 1, minHeight: 0 }}
      ></div>
      {!webSocketService.current.isSocketConnected() && (
        <div className="terminal-status">
          {TERMINAL_TEXT.connecting}
        </div>
      )}
    </div>
  );
};

export default Terminal;