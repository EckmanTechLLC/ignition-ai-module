/**
 * Insight Chat component - AI-powered chat interface for Ignition AI module.
 * Simple JavaScript implementation without build tools.
 */

const { Component, ComponentRegistry } = window.PerspectiveClient;
const React = window.React;

// Load marked.js dynamically from CDN
(function loadMarked() {
    if (window.marked) return; // Already loaded

    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/marked@11.1.1/marked.min.js';
    script.async = false;
    document.head.appendChild(script);
})();

// Load highlight.js for syntax highlighting
(function loadHighlightJS() {
    if (window.hljs) return; // Already loaded

    // Load highlight.js library
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/highlight.min.js';
    script.async = false;
    script.onload = function() {
        // Configure marked.js to use highlight.js after both libraries load
        if (window.marked && window.hljs) {
            marked.setOptions({
                highlight: function(code, lang) {
                    if (lang && hljs.getLanguage(lang)) {
                        try {
                            return hljs.highlight(code, { language: lang }).value;
                        } catch (e) {
                            console.warn('Highlight.js error:', e);
                        }
                    }
                    return hljs.highlightAuto(code).value;
                },
                breaks: true,
                gfm: true
            });
        }
    };
    document.head.appendChild(script);
})();

// Inject highlight.js CSS theme based on dark/light mode
function injectHighlightTheme(isDark) {
    const themeId = 'hljs-theme';
    let existing = document.getElementById(themeId);

    // Remove existing theme if present
    if (existing) {
        existing.remove();
    }

    // Inject new theme
    const link = document.createElement('link');
    link.id = themeId;
    link.rel = 'stylesheet';
    link.href = isDark
        ? 'https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/styles/github-dark.min.css'
        : 'https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.9.0/build/styles/github.min.css';
    document.head.appendChild(link);
}

// HTML-to-React converter for safe markdown rendering
function htmlToReactElements(html, isDark) {
    const container = document.createElement('div');
    container.innerHTML = html;
    return convertDOMToReact(container, isDark);
}

function convertDOMToReact(node, isDark) {
    if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent;
    }

    if (node.nodeType !== Node.ELEMENT_NODE) {
        return null;
    }

    const tagName = node.tagName.toLowerCase();
    const props = { style: getMarkdownStyles(tagName, isDark) };

    // Preserve className for highlight.js syntax highlighting
    if (node.className) {
        props.className = node.className;
    }

    const children = Array.from(node.childNodes).map(child => convertDOMToReact(child, isDark)).filter(Boolean);

    return React.createElement(tagName, props, children.length > 0 ? children : null);
}

// Markdown element styles
function getMarkdownStyles(tagName, isDark) {
    const styles = {
        h1: { fontSize: '1.875rem', fontWeight: 'bold', marginTop: '16px', marginBottom: '8px', color: isDark ? '#fff' : '#000' },
        h2: { fontSize: '1.5rem', fontWeight: 'bold', marginTop: '14px', marginBottom: '7px', color: isDark ? '#fff' : '#000' },
        h3: { fontSize: '1.25rem', fontWeight: 'bold', marginTop: '12px', marginBottom: '6px', color: isDark ? '#fff' : '#000' },
        h4: { fontSize: '1.125rem', fontWeight: 'bold', marginTop: '10px', marginBottom: '5px', color: isDark ? '#fff' : '#000' },
        h5: { fontSize: '1rem', fontWeight: 'bold', marginTop: '8px', marginBottom: '4px', color: isDark ? '#fff' : '#000' },
        h6: { fontSize: '0.875rem', fontWeight: 'bold', marginTop: '6px', marginBottom: '3px', color: isDark ? '#aaa' : '#666' },
        p: { marginTop: '0', marginBottom: '8px', lineHeight: '1.5' },
        ul: { marginTop: '4px', marginBottom: '8px', paddingLeft: '20px' },
        ol: { marginTop: '4px', marginBottom: '8px', paddingLeft: '20px' },
        li: { marginBottom: '4px' },
        code: {
            backgroundColor: isDark ? '#1e1e1e' : '#f5f5f5',
            padding: '2px 4px',
            borderRadius: '3px',
            fontFamily: 'monospace',
            fontSize: '0.875rem',
            color: isDark ? '#d4d4d4' : '#333'
        },
        pre: {
            backgroundColor: isDark ? '#1e1e1e' : '#f5f5f5',
            padding: '12px',
            borderRadius: '6px',
            overflowX: 'auto',
            marginTop: '8px',
            marginBottom: '8px'
        },
        blockquote: {
            borderLeft: `4px solid ${isDark ? '#555' : '#ddd'}`,
            paddingLeft: '12px',
            marginLeft: '0',
            marginTop: '8px',
            marginBottom: '8px',
            color: isDark ? '#aaa' : '#666',
            fontStyle: 'italic'
        },
        table: {
            borderCollapse: 'collapse',
            width: '100%',
            marginTop: '8px',
            marginBottom: '8px',
            fontSize: '0.875rem'
        },
        th: {
            borderBottom: `2px solid ${isDark ? '#555' : '#ddd'}`,
            padding: '8px',
            textAlign: 'left',
            fontWeight: 'bold',
            backgroundColor: isDark ? '#2a2a2a' : '#f9f9f9'
        },
        td: {
            borderBottom: `1px solid ${isDark ? '#444' : '#eee'}`,
            padding: '8px',
            textAlign: 'left'
        },
        a: {
            color: isDark ? '#58a6ff' : '#0969da',
            textDecoration: 'none'
        },
        strong: { fontWeight: 'bold' },
        em: { fontStyle: 'italic' },
        hr: {
            border: 'none',
            borderTop: `1px solid ${isDark ? '#444' : '#ddd'}`,
            marginTop: '12px',
            marginBottom: '12px'
        }
    };

    return styles[tagName] || {};
}

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
    },
    delete: (url) => {
        return fetch(url, {
            method: 'DELETE',
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
            enableAutoCompaction: tree.readBoolean("enableAutoCompaction", true),
            compactionTokenThreshold: tree.readNumber("compactionTokenThreshold", 180000),
            compactToRecentMessages: tree.readNumber("compactToRecentMessages", 30),
            theme: tree.readString("theme", "light"),
            readOnly: tree.readBoolean("readOnly", false),
            showScheduledTasks: tree.readBoolean("showScheduledTasks", false),
            taskPanelPosition: tree.readString("taskPanelPosition", "right"),
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
            expandedTools: {},
            tasks: [],
            taskPanelOpen: false,
            loadingTasks: false,
            taskError: null
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

        // Inject highlight.js theme based on current theme
        const isDark = this.props.props.theme === 'dark';
        injectHighlightTheme(isDark);

        // Validate configuration but don't load conversation on initial mount
        // Conversations should only be loaded when conversationId changes to a valid value
        const configError = this.validateConfiguration();
        if (configError) {
            this.setState({ error: configError });
        }

        // Load tasks if enabled
        if (this.props.props.showScheduledTasks) {
            this.loadTasks();

            // Set up interval for task refresh (every 10 seconds to catch tasks created by AI)
            this.taskRefreshInterval = setInterval(() => {
                this.loadTasks();
            }, 10000);
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

        // Re-inject highlight.js theme if theme changes
        if (prevProps.props.theme !== this.props.props.theme) {
            const isDark = this.props.props.theme === 'dark';
            injectHighlightTheme(isDark);
        }
    }

    componentWillUnmount() {
        // Clean up task refresh interval
        if (this.taskRefreshInterval) {
            clearInterval(this.taskRefreshInterval);
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
        const { userName, projectName, enableAutoCompaction, compactionTokenThreshold, compactToRecentMessages } = this.props.props;

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
            message: inputValue,
            enableAutoCompaction: enableAutoCompaction,
            compactionTokenThreshold: compactionTokenThreshold,
            compactToRecentMessages: compactToRecentMessages
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

    loadTasks() {
        const { userName, projectName } = this.props.props;
        if (!projectName) return;

        this.setState({ loadingTasks: true, taskError: null });

        http.get(`${API_BASE}/listTasks?userName=${encodeURIComponent(userName || '')}&projectName=${encodeURIComponent(projectName)}`)
            .then(response => {
                if (response.data.success) {
                    this.setState({
                        tasks: response.data.tasks || [],
                        loadingTasks: false
                    });
                } else {
                    this.setState({
                        taskError: response.data.error || 'Failed to load tasks',
                        loadingTasks: false
                    });
                }
            })
            .catch(err => {
                this.setState({
                    taskError: err.message,
                    loadingTasks: false
                });
            });
    }

    handleToggleTaskPanel() {
        this.setState(prevState => ({
            taskPanelOpen: !prevState.taskPanelOpen
        }), () => {
            // Always refresh tasks when opening panel to ensure current data
            if (this.state.taskPanelOpen) {
                this.loadTasks();
            }
        });
    }

    handleCreateTask(taskData) {
        http.post(`${API_BASE}/createTask`, taskData)
            .then(response => {
                if (response.data.success) {
                    this.loadTasks();
                } else {
                    alert('Failed to create task: ' + (response.data.error || 'Unknown error'));
                }
            })
            .catch(err => {
                alert('Error creating task: ' + err.message);
            });
    }

    handlePauseTask(taskId) {
        http.post(`${API_BASE}/pauseTask/${taskId}`, {})
            .then(response => {
                if (response.data.success) {
                    this.loadTasks();
                } else {
                    alert('Failed to pause task: ' + (response.data.error || 'Unknown error'));
                }
            })
            .catch(err => {
                alert('Error pausing task: ' + err.message);
            });
    }

    handleResumeTask(taskId) {
        http.post(`${API_BASE}/resumeTask/${taskId}`, {})
            .then(response => {
                if (response.data.success) {
                    this.loadTasks();
                } else {
                    alert('Failed to resume task: ' + (response.data.error || 'Unknown error'));
                }
            })
            .catch(err => {
                alert('Error resuming task: ' + err.message);
            });
    }

    handleDeleteTask(taskId) {
        if (!confirm('Delete this task?')) return;

        http.delete(`${API_BASE}/deleteTask/${taskId}`)
            .then(response => {
                if (response.data.success) {
                    this.loadTasks();
                } else {
                    alert('Failed to delete task: ' + (response.data.error || 'Unknown error'));
                }
            })
            .catch(err => {
                alert('Error deleting task: ' + err.message);
            });
    }

    handleViewTaskExecutions(taskId) {
        http.get(`${API_BASE}/getTaskExecutions/${taskId}?limit=10`)
            .then(response => {
                if (response.data.success) {
                    const executions = response.data.executions || [];
                    const message = executions.length > 0
                        ? executions.map(e => `${new Date(e.executedAt).toLocaleString()}: ${e.status}` + (e.errorMessage ? ` - ${e.errorMessage}` : '')).join('\n')
                        : 'No execution history';
                    alert('Task Execution History:\n\n' + message);
                } else {
                    alert('Failed to load executions: ' + (response.data.error || 'Unknown error'));
                }
            })
            .catch(err => {
                alert('Error loading executions: ' + err.message);
            });
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

        // Message content - parse markdown and convert to React elements
        const markdownHtml = marked.parse(message.content || '', { breaks: true, gfm: true });
        const contentElements = htmlToReactElements(markdownHtml, isDark);

        elements.push(
            React.createElement('div', {
                key: 'content',
                style: {
                    fontSize: '0.9375rem',
                    lineHeight: '1.5'
                }
            }, contentElements)
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

    renderTask(task, index) {
        const isDark = this.props.props.theme === 'dark';

        const taskCardStyle = {
            padding: '12px',
            marginBottom: '8px',
            backgroundColor: isDark ? '#3a3a3a' : '#fff',
            border: `1px solid ${isDark ? '#555' : '#e0e0e0'}`,
            borderRadius: '6px'
        };

        const taskHeaderStyle = {
            fontWeight: 'bold',
            marginBottom: '6px',
            fontSize: '0.875rem',
            color: isDark ? '#fff' : '#000'
        };

        const taskInfoStyle = {
            fontSize: '0.75rem',
            color: isDark ? '#aaa' : '#666',
            marginBottom: '4px'
        };

        const statusBadgeStyle = {
            display: 'inline-block',
            padding: '2px 6px',
            borderRadius: '3px',
            fontSize: '0.7rem',
            fontWeight: 'bold',
            backgroundColor: task.enabled ? (isDark ? '#2d5a2d' : '#d4edda') : (isDark ? '#5a2d2d' : '#f8d7da'),
            color: task.enabled ? (isDark ? '#90ee90' : '#155724') : (isDark ? '#ff9999' : '#721c24')
        };

        const buttonStyle = {
            padding: '4px 8px',
            marginRight: '4px',
            fontSize: '0.7rem',
            border: 'none',
            borderRadius: '3px',
            cursor: 'pointer',
            backgroundColor: isDark ? '#555' : '#e0e0e0',
            color: isDark ? '#fff' : '#000'
        };

        return React.createElement('div', { key: index, style: taskCardStyle }, [
            React.createElement('div', { key: 'header', style: taskHeaderStyle }, task.taskDescription),
            React.createElement('div', { key: 'cron', style: taskInfoStyle }, `Schedule: ${task.cronExpression}`),
            React.createElement('div', { key: 'next', style: taskInfoStyle }, `Next run: ${new Date(task.nextRunAt).toLocaleString()}`),
            task.lastRunAt && React.createElement('div', { key: 'last', style: taskInfoStyle }, `Last run: ${new Date(task.lastRunAt).toLocaleString()}`),
            React.createElement('div', { key: 'status', style: { marginTop: '6px', marginBottom: '6px' } }, [
                React.createElement('span', { key: 'badge', style: statusBadgeStyle }, task.enabled ? 'ACTIVE' : 'PAUSED')
            ]),
            React.createElement('div', { key: 'actions' }, [
                task.enabled
                    ? React.createElement('button', {
                        key: 'pause',
                        style: buttonStyle,
                        onClick: () => this.handlePauseTask(task.id)
                    }, 'Pause')
                    : React.createElement('button', {
                        key: 'resume',
                        style: buttonStyle,
                        onClick: () => this.handleResumeTask(task.id)
                    }, 'Resume'),
                React.createElement('button', {
                    key: 'delete',
                    style: { ...buttonStyle, backgroundColor: isDark ? '#5a2d2d' : '#f8d7da', color: isDark ? '#ff9999' : '#721c24' },
                    onClick: () => this.handleDeleteTask(task.id)
                }, 'Delete'),
                React.createElement('button', {
                    key: 'history',
                    style: buttonStyle,
                    onClick: () => this.handleViewTaskExecutions(task.id)
                }, 'History')
            ])
        ]);
    }

    renderTaskPanel() {
        const { taskPanelOpen, tasks, loadingTasks, taskError } = this.state;
        const { taskPanelPosition, theme } = this.props.props;
        const isDark = theme === 'dark';

        if (!taskPanelOpen) return null;

        const panelStyle = {
            position: 'absolute',
            top: 0,
            [taskPanelPosition]: 0,
            width: '320px',
            height: '100%',
            backgroundColor: isDark ? '#2d2d2d' : '#f8f9fa',
            borderLeft: taskPanelPosition === 'right' ? `1px solid ${isDark ? '#444' : '#ddd'}` : 'none',
            borderRight: taskPanelPosition === 'left' ? `1px solid ${isDark ? '#444' : '#ddd'}` : 'none',
            overflowY: 'auto',
            zIndex: 100,
            boxShadow: '0 0 10px rgba(0,0,0,0.3)',
            padding: '16px'
        };

        const headerStyle = {
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
            paddingBottom: '8px',
            borderBottom: `1px solid ${isDark ? '#444' : '#ddd'}`
        };

        const closeButtonStyle = {
            background: 'none',
            border: 'none',
            fontSize: '1.5rem',
            cursor: 'pointer',
            color: isDark ? '#fff' : '#000',
            padding: '0',
            lineHeight: '1'
        };

        const emptyStyle = {
            padding: '20px',
            textAlign: 'center',
            color: isDark ? '#888' : '#999',
            fontSize: '0.875rem'
        };

        const errorStyle = {
            padding: '12px',
            backgroundColor: isDark ? '#5a2d2d' : '#f8d7da',
            color: isDark ? '#ff9999' : '#721c24',
            borderRadius: '6px',
            marginBottom: '12px',
            fontSize: '0.875rem'
        };

        return React.createElement('div', { style: panelStyle }, [
            React.createElement('div', { key: 'header', style: headerStyle }, [
                React.createElement('h3', {
                    key: 'title',
                    style: { margin: 0, fontSize: '1rem', color: isDark ? '#fff' : '#000' }
                }, 'Scheduled Tasks'),
                React.createElement('button', {
                    key: 'close',
                    style: closeButtonStyle,
                    onClick: () => this.handleToggleTaskPanel()
                }, '✕')
            ]),
            loadingTasks && React.createElement('div', {
                key: 'loading',
                style: { padding: '20px', textAlign: 'center', color: isDark ? '#888' : '#999' }
            }, 'Loading tasks...'),
            taskError && React.createElement('div', { key: 'error', style: errorStyle }, taskError),
            !loadingTasks && !taskError && tasks.length === 0 && React.createElement('div', {
                key: 'empty',
                style: emptyStyle
            }, 'No scheduled tasks'),
            !loadingTasks && !taskError && tasks.length > 0 && React.createElement('div', {
                key: 'tasks'
            }, tasks.map((task, i) => this.renderTask(task, i)))
        ]);
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
                props.showScheduledTasks && React.createElement('button', {
                    key: 'tasks',
                    style: headerButtonStyle,
                    onClick: () => this.handleToggleTaskPanel()
                }, `📋 Tasks (${this.state.tasks.filter(t => t.enabled).length})`),
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
                style: { ...emitProps.style, ...themeStyles, position: 'relative' }
            },
            [header, messagesArea, inputArea, props.showScheduledTasks && this.renderTaskPanel()]
        );
    }
}

// Register the component with the Perspective component registry
ComponentRegistry.register(new InsightChatMeta());
