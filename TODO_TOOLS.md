# Tools to Implement

## Tier 1: Critical ✅ COMPLETE
- [x] **ListTagProvidersTool** - List all tag providers ([default], [System], [edge], etc.)
- [x] **SearchGatewayFilesTool** - Flexible filesystem search (replaces ListGatewayModulesTool - allows LLM to discover modules/configs/logs dynamically)
- [x] **ListProjectsTool** - List all Ignition projects in gateway

## Tier 2: Deferred
- [x] ~~**GetGatewayInfoTool**~~ - Not needed (LLM already retrieves via existing tools)
- [x] ~~**ListAlarmPipelinesTool**~~ - Not needed (LLM discovers via SearchGatewayFilesTool)
- [ ] ~~**ListSchedulesTool**~~ - Deferred (unclear scope - many schedule types, complex discovery)

## Tier 3: Deferred
- [ ] **GetDeviceConnectionsTool** - List OPC/device connections and status
- [ ] **ListWebDevEndpointsTool** - List custom web endpoints (if WebDev installed)

## System Function Execution (Jython) ✅ COMPLETE

- [x] **ListSystemFunctionsTool** - List all system.* functions with descriptions (DYNAMIC DISCOVERY via reflection)
- [x] **ExecuteSystemFunctionTool** - Execute any system.* function via Jython script execution
- [x] **ScriptExecutor** - Core executor using ScriptManager for 100% coverage

**Features:**
- **Dynamic discovery** - 256+ functions discovered via Java reflection (no hardcoded catalog)
- Introspects 13 system.* modules (tag, db, alarm, date, util, net, dataset, file, security, user, device, opc, project)
- Extracts metadata from @KeywordArgs and @ScriptFunction annotations
- Loads documentation from resource bundles automatically
- Filters artifacts (functions without @KeywordArgs are skipped)
- 100% coverage of all system.* functions (actual Jython execution)
- JSON ↔ Python parameter/result marshalling via TypeUtilities
- Configurable safety: DISABLED / READ_ONLY (default) / UNRESTRICTED
- Read-only whitelist (30+ safe functions)
- Timeout and size limits
- Project-scoped script execution

**Implementation:**
- Uses Java reflection to discover system.* utility classes
- Uses `ScriptManager.runCode()` for execution
- Uses `TypeUtilities.gsonToPy()` for JSON → Python
- Uses `TypeUtilities.pyToGson()` for Python → JSON
- Uses `ScriptManager.createLocalsMap()` to pre-import system.*
- Lazy initialization with thread-safe caching

## Notes
- **Tier 1 COMPLETE** - Core discovery tools working well
- **System Function Execution COMPLETE** - 100% coverage of system.* functions
- Flexible filesystem tools (SearchGatewayFilesTool, search_project_files, read_file_content) allow LLM to discover most resources dynamically
- Additional specialized tools can be added as specific needs arise
- Each tool should follow IAITool interface pattern (see existing tools in gateway/src/main/java/com/iai/ignition/gateway/tools/)
- Register in ToolRegistry.java after creation
