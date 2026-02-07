# Ignition AI Module

An AI-powered assistant for exploring and understanding Ignition SCADA systems through natural language queries. Integrates Claude AI with comprehensive tools for reading project resources, querying tags/alarms, and analyzing system data.

## Features

- **Natural Language Queries** - Ask questions about your Ignition system in plain English
- **Insight Chat Component** - Perspective component for interactive AI conversations
- **Comprehensive Tools** - 14 tools for project files, databases, system functions, and conversation management
- **Conversation History** - Persistent conversations with automatic compaction to prevent token limits
- **Scheduled Tasks** - Cron-based recurring AI queries with execution history
- **System Function Execution** - Execute any system.* Jython function (optional, 100% coverage)
- **Conversation Memory** - AI can query its own conversation history for context
- **Read-Only Safety** - Default mode analyzes without modifications (system functions can be enabled)

## Requirements

- **Ignition 8.1.8+** - Gateway with Perspective module
- **JDK 17** - For building the module
- **Claude API Key** - From Anthropic (https://www.anthropic.com)
- **Database** - SQL database for conversation storage (optional but recommended)

## Installation

### 1. Enable Developer Mode (Unsigned Modules)

Since this module is unsigned, you must enable unsigned modules in Ignition:

1. Stop the Ignition Gateway
2. Edit `<ignition-install>/data/ignition.conf`
3. Add to the `wrapper.java.additional` section:
   ```
   wrapper.java.additional.N=-Dignition.allowunsignedmodules=true
   ```
   (Replace `N` with the next available number, e.g., if the last line is `wrapper.java.additional.15`, use `16`)
4. Restart the Ignition Gateway

### 2. Install the Module

1. Build the module:
   ```bash
   ./gradlew clean build -x test
   ```
   Module file: `build/ignition-ai-module-unsigned.modl`

2. Open Ignition Gateway webpage (http://localhost:8088)
3. Navigate to **Config → System → Modules**
4. Click **Install or Upgrade a Module**
5. Upload `build/ignition-ai-module-unsigned.modl`
6. Restart the gateway when prompted

## Configuration

### Gateway Settings

Navigate to **Config → Ignition AI → Settings** in the Gateway webpage.

#### Claude Configuration
- **API Key** (required) - Your Anthropic Claude API key
- **Model Name** - Default: `claude-sonnet-4-5-20250929`
- **System Prompt** - Leave empty to use default (recommended), or customize for specific behavior

#### Database Configuration
- **Database Connection** (required) - Database for storing conversations
  - Tables created automatically on first use
  - Tables: `iai_conversations`, `iai_messages`, `iai_debug_log`, `iai_scheduled_tasks`, `iai_task_executions`
- **Enable Database Tools** - Allow AI to query databases (default: true)

#### Tool Limits
- **Max Tool Result Size (KB)** - Truncate large tool results (default: 100)
- **Max Tag History Records** - Limit tag history queries (default: 1000)
- **Max Alarm History Records** - Limit alarm history queries (default: 1000)
- **Query Timeout (seconds)** - Database query timeout (default: 30)

#### Conversation Settings
- **Max Conversation History Messages** - Message limit per conversation (default: 50)
- **Max Tool Iterations** - Max tool calls per AI response to prevent loops (default: 10)

#### Gateway Settings
- **Gateway Data Path** - Auto-detected, usually `/usr/local/bin/ignition/data` or similar

#### System Function Execution (Optional)
- **Allow System Function Execution** - Enable Jython script execution (default: false, CAUTION)
- **System Function Mode** - READ_ONLY (whitelisted safe functions) or UNRESTRICTED (all functions, testing only)
- **System Function Timeout** - Execution timeout in seconds (default: 30)
- **Max System Function Result Size** - Size limit in KB (default: 100)

### Database Setup

Create the required tables in your chosen database:

```sql
-- See gateway/src/main/java/com/iai/ignition/gateway/database/ConversationSchemaManager.java
-- Tables created automatically when database connection is configured
```

## Usage

### Add Insight Chat Component

1. Open Perspective Designer
2. Create or open a view
3. Find **Insight Chat** in the component palette under "Ignition AI"
4. Drag onto your view
5. Configure properties:
   - `projectName` - Bind to `{session.props.projectName}` (REQUIRED)
   - `userName` - Bind to `{session.props.auth.user.userName}` or leave empty
   - `conversationId` - Leave empty for new conversation, or bind to resume
   - `showHistory`, `showTimestamps`, `showToolDetails`, `showTokenUsage` - UI display options
   - `enableAutoCompaction` - Auto-summarize old messages at token threshold (default: true)
   - `compactionTokenThreshold` - Token limit before compaction (default: 180000)
   - `compactToRecentMessages` - Messages to keep in full during compaction (default: 30)
   - `showScheduledTasks` - Show scheduled tasks panel (default: false)
   - `taskPanelPosition` - "right" or "left" (default: "right")
   - `theme` - "light", "dark", or "auto"
   - `readOnly` - Disable input for display-only mode
   - `placeholder` - Custom input prompt text

### Example Questions

- "What Perspective views exist in this project?"
- "Show me the current value of CompressorStation/Compressor1/Discharge_Pressure"
- "What are the last 10 alarm events?"
- "List all database connections and tables"
- "Search for scripts that reference 'pump' in their code"
- "Browse the OPC device structure under [power]" (requires system functions enabled)
- "Create a scheduled task to check alarm count every morning at 8am"
- "What did we discuss about the compressor alarms earlier?" (conversation memory)

### Conversation Export

Export conversations as Markdown or JSON via the component or directly from the database.

## Available Tools

The AI has access to **14 tools** (maximum when all features enabled):

**Core Tools** (3 tools - always available)
- **QueryConversationMemoryTool** - Search conversation history for context
- **ProjectFilesTool** - List project files with filtering (views, scripts, queries, etc.)
- **SearchResourcesTool** - Fuzzy search across all project resources

**Database Tools** (6 tools - gated by EnableDatabaseTools setting)
- **ListDatabasesTool** - List available database connections
- **ListTablesTool** - List tables in a database
- **DescribeTableTool** - Get table schema (columns, types, constraints)
- **QueryTableTool** - Query table data with filtering
- **ExecuteNamedQueryTool** - Execute Ignition named queries
- **ExecuteSqlQueryTool** - Execute arbitrary SQL queries

**System Function Tools** (2 tools - gated by AllowSystemFunctionExecution setting)
- **ListSystemFunctionsTool** - Discover available system.* functions dynamically
- **ExecuteSystemFunctionTool** - Execute any system.* function via Jython
  - 100% coverage of all system.* modules (tag, db, alarm, opc, date, util, etc.)
  - READ_ONLY mode (whitelisted safe functions) or UNRESTRICTED mode (all functions)
  - Supports positional and keyword arguments
  - Three-tier serialization fallback for complex return types

**Scheduled Task Tools** (3 tools - always available)
- **CreateScheduledTaskTool** - Create cron-based recurring AI queries
- **ListScheduledTasksTool** - List scheduled tasks with execution history
- **ManageScheduledTaskTool** - Enable/disable/delete scheduled tasks

## Known Limitations

- **Unsigned Module** - Requires developer mode enabled
- **Token Limits** - Mitigated by automatic conversation compaction (summarizes old messages)
- **Read-Only by Default** - Cannot modify tags or configurations unless system functions enabled
- **System Functions Caution** - UNRESTRICTED mode allows write operations (testing only)

## Troubleshooting

### Module Won't Load
- Check `<ignition>/logs/wrapper.log` for errors
- Verify unsigned modules are enabled (`-Dignition.allowunsignedmodules=true`)
- Ensure Perspective module is installed

### Component Not in Palette
- Restart Designer
- Check Gateway logs for registration errors
- Verify module installed successfully

### AI Not Responding
- Check API key in Gateway settings
- Verify database connection configured
- Check Gateway logs for API errors
- Ensure `projectName` prop is bound

### Hallucination / Incorrect Answers
- AI may fabricate data if tools fail or return no results
- Automatic compaction prevents token limit issues in long conversations
- Check tool execution details (expand in UI) to verify AI's data sources

## Development

### Build
```bash
./gradlew clean build -x test
```

### Project Structure
```
ignition-ai-module/
├── common/          - Shared code (ComponentDescriptor, models)
├── gateway/         - Gateway scope (tools, endpoints, database)
├── designer/        - Designer scope (minimal)
└── build/           - Output (.modl file)
```

### Adding Tools

1. Implement `IAITool` interface
2. Add to `gateway/src/main/java/com/iai/ignition/gateway/tools/[category]/`
3. Register in `ToolRegistry.java` constructor

## Architecture Notes

- **System Prompt** - Modular system in Gateway settings (leave empty for default)
- **Conversation Compaction** - Automatic summarization at 180K tokens, keeps 30 recent messages
- **Tool Results** - Sanitized and size-limited to prevent token overflow
- **Component** - Uses HTTP POST endpoints (not ModelDelegate pattern)
- **Database** - Tables auto-created via ConversationSchemaManager
- **Scheduled Tasks** - TaskSchedulerService with static accessor for persistence across settings reloads

## License

MIT License - See LICENSE file

## Credits

Built using the Ignition SDK 8.1.16 and Claude API.

## Support

This is an open-source project. For issues and feature requests, use the GitHub issue tracker.
