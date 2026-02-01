/**
 * Insight Chat component - AI-powered chat interface for Ignition AI module.
 * Simple JavaScript implementation without build tools.
 */

const { Component, ComponentRegistry } = window.PerspectiveClient;
const React = window.React;

// API endpoint base URL - POST directly to /data/ path to avoid redirect issues
const API_BASE = `/data/ignitionai`;

// HTTP helper using native fetch API
const http = {
    get: (url) => {
        return fetch(url, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        }).then(res => {
            return res.json().then(data => {
                if (!res.ok) {
                    throw new Error(data.error || `HTTP ${res.status}`);
                }
                return { data, status: res.status };
            }).catch(err => {
                // If JSON parsing fails, throw generic error
                if (err.message.includes('HTTP')) {
                    throw err;
                }
                throw new Error(`HTTP ${res.status}`);
            });
        });
    },
    post: (url, body) => {
        return fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(res => {
            return res.json().then(data => {
                if (!res.ok) {
                    throw new Error(data.error || `HTTP ${res.status}`);
                }
                return { data, status: res.status };
            }).catch(err => {
                // If JSON parsing fails, throw generic error
                if (err.message.includes('HTTP') || err.message.includes('error')) {
                    throw err;
                }
                throw new Error(`HTTP ${res.status}`);
            });
        });
    }
};

/**
 * Component Meta implementation - describes the component to Perspective
 */
class InsightChatMeta {
    getComponentType() {
        return "com.iai.ignition.insight-chat";
    }

    getViewComponent() {
        return InsightChatComponent;
    }

    getDefaultSize() {
        return {
            width: 600,
            height: 600
        };
    }

    getPropsReducer(tree) {
        // Read props with explicit null/undefined handling
        const conversationId = tree.readString("conversationId", "");
        const userName = tree.readString("userName", "");
        const projectName = tree.readString("projectName", "");

        return {
            conversationId: conversationId || "",  // Ensure never null/undefined
            userName: userName || "",
            projectName: projectName || "",
            showHistory: tree.readBoolean("showHistory", true),
            showTimestamps: tree.readBoolean("showTimestamps", true),
            showToolDetails: tree.readBoolean("showToolDetails", true),
            showTokenUsage: tree.readBoolean("showTokenUsage", true),
            theme: tree.readString("theme", "light"),
            readOnly: tree.readBoolean("readOnly", false),
            placeholder: tree.readString("placeholder", "Ask about your Ignition system...")
        };
    }
}

/**
 * The Insight Chat Component implementation
 */
class InsightChatComponent extends Component {
    constructor(props) {
        super(props);
        this.state = {
            messages: [],
            inputValue: '',
            loading: false,
            error: null,
            conversationId: props.props.conversationId,
            totalInputTokens: 0,
            totalOutputTokens: 0,
            expandedTools: {}
        };
        this.messagesEndRef = null;
        this.textareaRef = null;
    }

    /**
     * Validate UUID format for conversation IDs.
     */
    isValidUUID(str) {
        if (!str) return false;
        const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
        return uuidRegex.test(str);
    }

    /**
     * Validate component configuration and show helpful error messages.
     */
    validateConfiguration() {
        const { conversationId } = this.state;
        const { projectName } = this.props.props;

        // Check if conversationId is valid UUID format (if provided)
        if (conversationId && !this.isValidUUID(conversationId)) {
            return 'Invalid conversation ID format. Must be a valid UUID (leave empty to start new conversation).';
        }

        // Check if projectName is provided (REQUIRED for AI to function)
        if (!projectName || (typeof projectName === 'string' && projectName.trim() === '')) {
            return 'Project name is required. Please bind the "projectName" property to your session project (e.g., {session.props.projectName} or {session.project}) in the component properties.';
        }

        return null; // No errors
    }

    componentDidMount() {
        // Debug: Log the initial conversationId value
        console.log('InsightChat mounted with conversationId:', this.state.conversationId, 'type:', typeof this.state.conversationId);

        // Validate configuration but don't load conversation on initial mount
        // Conversations should only be loaded when conversationId changes to a valid value
        const configError = this.validateConfiguration();
        if (configError) {
            this.setState({ error: configError });
        }

        // Do NOT load conversation on mount - only load when prop explicitly changes
    }

    componentDidUpdate(prevProps) {
        const oldConvId = prevProps.props.conversationId;
        const newConvId = this.props.props.conversationId;

        // Only reload if conversationId changes AND the new value is a valid UUID
        // This prevents loading on initial render or when changing from one invalid value to another
        if (oldConvId !== newConvId) {
            console.log('ConversationId changed from', oldConvId, 'to', newConvId);

            // Check if new ID is valid and different
            const isNewIdValid = newConvId && newConvId !== 'null' && newConvId !== 'undefined' && this.isValidUUID(newConvId);
            const wasOldIdValid = oldConvId && oldConvId !== 'null' && oldConvId !== 'undefined' && this.isValidUUID(oldConvId);

            this.setState({ conversationId: newConvId, messages: [], error: null }, () => {
                // Validate the new configuration
                const configError = this.validateConfiguration();
                if (configError) {
                    this.setState({ error: configError });
                    return;
                }

                // Only load if transitioning TO a valid UUID (not just any change)
                if (isNewIdValid && oldConvId !== newConvId) {
                    console.log('Loading conversation:', newConvId);
                    this.loadConversation(newConvId);
                }
            });
        }

        // Re-validate if projectName changes
        if (prevProps.props.projectName !== this.props.props.projectName) {
            const configError = this.validateConfiguration();
            if (configError) {
                this.setState({ error: configError });
            } else if (this.state.error && this.state.error.includes('Project name is required')) {
                // Clear error if it was about missing projectName and now it's fixed
                this.setState({ error: null });
            }
        }
    }

    loadConversation(conversationId) {
        // Defensive check - never try to load without a valid UUID
        if (!conversationId || conversationId === 'null' || conversationId === 'undefined' || !this.isValidUUID(conversationId)) {
            console.warn('loadConversation called with invalid ID:', conversationId);
            return; // Silently ignore - this is a new conversation
        }

        this.setState({ loading: true, error: null });

        http.get(`${API_BASE}/getConversation/${encodeURIComponent(conversationId)}`)
            .then(response => {
                if (response.data.success) {
                    this.setState({
                        messages: response.data.messages || [],
                        loading: false
                    }, () => this.scrollToBottom());

                    // Calculate total tokens
                    this.calculateTotalTokens(response.data.messages || []);
                } else {
                    this.setState({
                        error: response.data.error || 'Failed to load conversation',
                        loading: false
                    });
                }
            })
            .catch(error => {
                this.setState({
                    error: error.message || 'Network error loading conversation',
                    loading: false
                });
            });
    }

    calculateTotalTokens(messages) {
        let inputTokens = 0;
        let outputTokens = 0;

        messages.forEach(msg => {
            if (msg.inputTokens) inputTokens += msg.inputTokens;
            if (msg.outputTokens) outputTokens += msg.outputTokens;
        });

        this.setState({
            totalInputTokens: inputTokens,
            totalOutputTokens: outputTokens
        });
    }

    scrollToBottom() {
        if (this.messagesEndRef) {
            this.messagesEndRef.scrollIntoView({ behavior: 'smooth' });
        }
    }

    handleSendMessage() {
        const { inputValue, conversationId } = this.state;
        const { userName, projectName } = this.props.props;

        // Validate input
        if (!inputValue.trim()) return;

        // Validate configuration before sending
        const configError = this.validateConfiguration();
        if (configError) {
            this.setState({ error: configError });
            return;
        }

        // Add user message to UI immediately
        const userMessage = {
            role: 'user',
            content: inputValue,
            timestamp: Date.now()
        };

        this.setState(prevState => ({
            messages: [...prevState.messages, userMessage],
            inputValue: '',
            loading: true,
            error: null
        }), () => this.scrollToBottom());

        // Send to API
        const requestBody = {
            conversationId: conversationId,
            userName: userName,
            projectName: projectName,
            message: inputValue
        };

        const url = `${API_BASE}/sendMessage`;
        console.log('Sending message to:', url);
        console.log('Request body:', requestBody);

        http.post(url, requestBody)
            .then(response => {
                if (response.data.success) {
                    // Add assistant response
                    const assistantMessage = {
                        id: response.data.messageId,
                        role: 'assistant',
                        content: response.data.content,
                        inputTokens: response.data.inputTokens,
                        outputTokens: response.data.outputTokens,
                        toolCalls: response.data.toolCalls,
                        timestamp: Date.now()
                    };

                    this.setState(prevState => ({
                        messages: [...prevState.messages, assistantMessage],
                        loading: false,
                        conversationId: response.data.conversationId,
                        totalInputTokens: prevState.totalInputTokens + (response.data.inputTokens || 0),
                        totalOutputTokens: prevState.totalOutputTokens + (response.data.outputTokens || 0)
                    }), () => this.scrollToBottom());
                } else {
                    this.setState({
                        error: response.data.error || 'Failed to send message',
                        loading: false
                    });
                }
            })
            .catch(error => {
                console.error('Error sending message:', error);
                console.error('URL was:', url);
                this.setState({
                    error: error.message || 'Network error',
                    loading: false
                });
            });
    }

    handleInputChange(event) {
        this.setState({ inputValue: event.target.value });
    }

    handleKeyDown(event) {
        // Send on Enter, new line on Shift+Enter
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            this.handleSendMessage();
        }
    }

    handleClearConversation() {
        if (confirm('Clear this conversation?')) {
            this.setState({
                messages: [],
                conversationId: null,
                totalInputTokens: 0,
                totalOutputTokens: 0,
                error: null
            });
        }
    }

    handleExportConversation() {
        const { conversationId } = this.state;
        if (!conversationId) {
            alert('No conversation to export');
            return;
        }

        window.open(`${API_BASE}/exportConversation/${encodeURIComponent(conversationId)}?format=markdown`, '_blank');
    }

    toggleToolExpand(messageIndex) {
        this.setState(prevState => ({
            expandedTools: {
                ...prevState.expandedTools,
                [messageIndex]: !prevState.expandedTools[messageIndex]
            }
        }));
    }

    formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleTimeString();
    }

    renderMessage(message, index) {
        const { showTimestamps, showToolDetails, showTokenUsage, theme } = this.props.props;
        const isDark = theme === 'dark';

        const messageStyles = {
            marginBottom: '12px',
            padding: '12px',
            borderRadius: '8px',
            maxWidth: '85%',
            wordWrap: 'break-word'
        };

        const userStyles = {
            ...messageStyles,
            backgroundColor: isDark ? '#1e3a5f' : '#e3f2fd',
            marginLeft: 'auto',
            textAlign: 'right'
        };

        const assistantStyles = {
            ...messageStyles,
            backgroundColor: isDark ? '#2d2d2d' : '#f5f5f5',
            marginRight: 'auto'
        };

        const isUser = message.role === 'user';
        const style = isUser ? userStyles : assistantStyles;

        const elements = [];

        // Role label
        elements.push(
            React.createElement('div', {
                key: 'role',
                style: {
                    fontWeight: 'bold',
                    fontSize: '0.875rem',
                    marginBottom: '4px',
                    color: isDark ? '#aaa' : '#666'
                }
            }, isUser ? 'You' : 'Assistant')
        );

        // Message content
        elements.push(
            React.createElement('div', {
                key: 'content',
                style: {
                    whiteSpace: 'pre-wrap',
                    fontSize: '0.9375rem',
                    lineHeight: '1.5'
                }
            }, message.content)
        );

        // Tool calls (if any)
        if (showToolDetails && message.toolCalls && message.toolCalls.length > 0) {
            const isExpanded = this.state.expandedTools[index];

            elements.push(
                React.createElement('div', {
                    key: 'tools',
                    style: {
                        marginTop: '8px',
                        padding: '8px',
                        backgroundColor: isDark ? '#3a3a3a' : '#fff3cd',
                        borderRadius: '4px',
                        fontSize: '0.875rem'
                    }
                }, [
                    React.createElement('div', {
                        key: 'tools-header',
                        style: { cursor: 'pointer', fontWeight: 'bold' },
                        onClick: () => this.toggleToolExpand(index)
                    }, `🔧 ${message.toolCalls.length} tool${message.toolCalls.length > 1 ? 's' : ''} used ${isExpanded ? '▼' : '▶'}`),
                    isExpanded && React.createElement('div', {
                        key: 'tools-list',
                        style: { marginTop: '8px', fontFamily: 'monospace', fontSize: '0.8125rem' }
                    }, message.toolCalls.map((tool, i) =>
                        React.createElement('div', {
                            key: i,
                            style: { marginBottom: '4px' }
                        }, `• ${tool.name}`)
                    ))
                ])
            );
        }

        // Token usage and timestamp footer
        const footerItems = [];
        if (showTokenUsage && (message.inputTokens || message.outputTokens)) {
            footerItems.push(
                React.createElement('span', {
                    key: 'tokens'
                }, `${message.inputTokens || 0} in / ${message.outputTokens || 0} out`)
            );
        }
        if (showTimestamps && message.timestamp) {
            footerItems.push(
                React.createElement('span', {
                    key: 'timestamp'
                }, this.formatTimestamp(message.timestamp))
            );
        }

        if (footerItems.length > 0) {
            elements.push(
                React.createElement('div', {
                    key: 'footer',
                    style: {
                        marginTop: '6px',
                        fontSize: '0.75rem',
                        color: isDark ? '#888' : '#999',
                        display: 'flex',
                        justifyContent: 'space-between',
                        gap: '10px'
                    }
                }, footerItems)
            );
        }

        return React.createElement('div', {
            key: index,
            style: style
        }, elements);
    }

    render() {
        const { props, emit } = this.props;
        const { messages, inputValue, loading, error } = this.state;
        const isDark = props.theme === 'dark';

        // Apply theme styles to container, but let Perspective control sizing/position
        const themeStyles = {
            border: `1px solid ${isDark ? '#444' : '#ddd'}`,
            borderRadius: '8px',
            backgroundColor: isDark ? '#1a1a1a' : '#ffffff',
            color: isDark ? '#e0e0e0' : '#000000',
            fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
            // Internal flex layout for chat components
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden'
        };

        const headerStyle = {
            padding: '12px 16px',
            borderBottom: `1px solid ${isDark ? '#444' : '#ddd'}`,
            backgroundColor: isDark ? '#2d2d2d' : '#f8f9fa',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
        };

        const messagesStyle = {
            flex: 1,
            padding: '16px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column'
        };

        const inputContainerStyle = {
            padding: '12px',
            borderTop: `1px solid ${isDark ? '#444' : '#ddd'}`,
            backgroundColor: isDark ? '#2d2d2d' : '#f8f9fa'
        };

        const textareaStyle = {
            width: '100%',
            minHeight: '60px',
            padding: '8px',
            border: `1px solid ${isDark ? '#555' : '#ccc'}`,
            borderRadius: '4px',
            resize: 'vertical',
            fontFamily: 'inherit',
            fontSize: '0.9375rem',
            backgroundColor: isDark ? '#1a1a1a' : '#ffffff',
            color: isDark ? '#e0e0e0' : '#000000',
            boxSizing: 'border-box'
        };

        const buttonStyle = {
            marginTop: '8px',
            padding: '8px 16px',
            backgroundColor: loading ? '#999' : '#1976d2',
            color: '#ffffff',
            border: 'none',
            borderRadius: '4px',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontSize: '0.9375rem',
            fontWeight: '500'
        };

        const headerButtonStyle = {
            padding: '6px 12px',
            marginLeft: '8px',
            backgroundColor: 'transparent',
            border: `1px solid ${isDark ? '#555' : '#ccc'}`,
            borderRadius: '4px',
            cursor: 'pointer',
            fontSize: '0.875rem',
            color: isDark ? '#e0e0e0' : '#000000'
        };

        // Header with title and action buttons
        const header = React.createElement('div', { style: headerStyle }, [
            React.createElement('div', {
                key: 'title',
                style: { fontWeight: 'bold', fontSize: '1.125rem' }
            }, 'Ignition AI'),
            React.createElement('div', { key: 'actions' }, [
                !props.readOnly && React.createElement('button', {
                    key: 'clear',
                    style: headerButtonStyle,
                    onClick: () => this.handleClearConversation()
                }, 'Clear'),
                this.state.conversationId && React.createElement('button', {
                    key: 'export',
                    style: headerButtonStyle,
                    onClick: () => this.handleExportConversation()
                }, 'Export')
            ])
        ]);

        // Messages area
        const messagesArea = React.createElement('div', {
            style: messagesStyle,
            ref: el => { if (el) this.messagesEndRef = el.lastElementChild; }
        }, [
            messages.length === 0 && !loading && React.createElement('div', {
                key: 'empty',
                style: {
                    textAlign: 'center',
                    color: isDark ? '#666' : '#999',
                    padding: '40px 20px'
                }
            }, 'Start a conversation by typing a message below'),
            ...messages.map((msg, i) => this.renderMessage(msg, i)),
            loading && React.createElement('div', {
                key: 'loading',
                style: {
                    textAlign: 'center',
                    padding: '12px',
                    color: isDark ? '#888' : '#666',
                    fontStyle: 'italic'
                }
            }, '✨ Thinking...'),
            error && React.createElement('div', {
                key: 'error',
                style: {
                    padding: '12px',
                    backgroundColor: '#ffebee',
                    color: '#c62828',
                    borderLeft: '4px solid #c62828',
                    borderRadius: '4px',
                    marginTop: '8px'
                }
            }, `Error: ${error}`)
        ]);

        // Input area
        const inputArea = !props.readOnly && React.createElement('div', { style: inputContainerStyle }, [
            React.createElement('textarea', {
                key: 'textarea',
                style: textareaStyle,
                value: inputValue,
                placeholder: props.placeholder,
                disabled: loading,
                onChange: e => this.handleInputChange(e),
                onKeyDown: e => this.handleKeyDown(e),
                ref: el => this.textareaRef = el
            }),
            React.createElement('div', {
                key: 'button-row',
                style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' }
            }, [
                props.showTokenUsage && React.createElement('div', {
                    key: 'tokens',
                    style: { fontSize: '0.75rem', color: isDark ? '#888' : '#666' }
                }, `Total: ${this.state.totalInputTokens + this.state.totalOutputTokens} tokens`),
                React.createElement('button', {
                    key: 'send',
                    style: buttonStyle,
                    onClick: () => this.handleSendMessage(),
                    disabled: loading || !inputValue.trim()
                }, loading ? 'Sending...' : 'Send ➤')
            ])
        ]);

        // Main container - emit() provides Perspective positioning/layout, merge with theme styles
        const emitProps = emit();
        return React.createElement(
            'div',
            {
                ...emitProps,
                style: { ...emitProps.style, ...themeStyles }
            },
            [header, messagesArea, inputArea]
        );
    }
}

// Register the component with the Perspective component registry
ComponentRegistry.register(new InsightChatMeta());
