# Ignition AI Module

An AI-powered assistant for exploring and understanding Ignition SCADA systems through natural language queries. Integrates Claude AI with comprehensive tools for reading project resources, querying tags/alarms, and analyzing system data.

## Features

- **Natural Language Queries** - Ask questions about your Ignition system in plain English
- **Insight Chat Component** - Perspective component for interactive AI conversations
- **Comprehensive Tools** - Access to project files, tags, alarms, databases, and gateway resources
- **Conversation History** - Persistent conversations stored in database with export capabilities
- **Read-Only Safety** - Analyzes and explains systems without making modifications
- **Multi-Project Support** - Works across different Ignition projects

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
  - Create tables using the SQL schemas in `gateway/src/main/java/com/iai/ignition/gateway/database/`
  - Tables: `iai_conversations`, `iai_messages`
- **Enable Database Tools** - Allow AI to query databases (default: true)

#### Tool Limits
- **Max Tool Result Size (KB)** - Truncate large tool results (default: 100)
- **Max Tag History Records** - Limit tag history queries (default: 1000)
- **Max Alarm History Records** - Limit alarm history queries (default: 1000)
- **Query Timeout (seconds)** - Database query timeout (default: 30)

#### Conversation Settings
- **Max Conversation History Messages** - Message limit per conversation (default: 50)

#### Gateway Settings
- **Gateway Data Path** - Auto-detected, usually `/usr/local/bin/ignition/data` or similar

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
   - `showHistory`, `showTimestamps`, `showToolDetails`, `showTokenUsage` - UI options
   - `theme` - "light", "dark", or "auto"
   - `readOnly` - Disable input for display-only mode
   - `placeholder` - Custom input prompt text

### Example Questions

- "What Perspective views exist in this project?"
- "Show me the current value of CompressorStation/Compressor1/Discharge_Pressure"
- "What are the last 10 alarm events?"
- "List all database connections"
- "What modules are installed on this gateway?"
- "Search for scripts that reference 'pump' in their code"

### Conversation Export

Export conversations as Markdown or JSON via the component or directly from the database.

## Available Tools

The AI has access to these tool categories:

**File System** (12 tools)
- Project structure, file metadata, Perspective views, Vision windows, scripts, named queries, search

**Tags** (3 tools)
- List tags, get configuration, query history

**Alarms** (2 tools)
- Query history, get configuration

**Databases** (6 tools, if enabled)
- List databases/tables, describe schema, query data, execute named queries

**Search** (1 tool)
- Project-wide resource search

## Known Limitations

- **Unsigned Module** - Requires developer mode enabled
- **Token Limits** - Long conversations (>50 messages with heavy tool use) may degrade performance
- **Read-Only** - Cannot modify tags, create resources, or change configurations
- **No Code Execution** - Cannot run scripts or execute gateway functions

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
- Start fresh conversations to avoid token limit issues
- Report patterns to help improve system prompt

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
2. Add to `gateway/src/main/java/com/iai/ignition/gateway/tools/`
3. Register in `ToolRegistry.java`

See `TODO_TOOLS.md` for planned tools.

## Architecture Notes

- System prompt in Gateway settings (leave empty for default)
- Conversation history truncated to prevent token overflow
- Tool results sanitized and size-limited
- Component uses HTTP POST endpoints (not ModelDelegate pattern)

See `TODO_ARCHITECTURE.md` for planned improvements.

## License

MIT License - See LICENSE file

## Credits

Built using the Ignition SDK 8.1.16 and Claude API.

## Support

This is an open-source project. For issues and feature requests, use the GitHub issue tracker.
