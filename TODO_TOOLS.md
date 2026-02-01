# Tools to Implement

## Tier 1: Critical
- [ ] **ListTagProvidersTool** - List all tag providers ([default], [System], [edge], etc.)
- [ ] **ListGatewayModulesTool** - List installed modules from module-info directory
- [ ] **ListProjectsTool** - List all Ignition projects in gateway

## Tier 2: High Value
- [ ] **GetGatewayInfoTool** - Gateway version, uptime, memory, license info
- [ ] **ListAlarmPipelinesTool** - List configured alarm pipelines
- [ ] **ListSchedulesTool** - List Gateway scheduled tasks

## Tier 3: Nice to Have
- [ ] **SearchGatewayResourcesTool** - Search across all gateway resources (files, configs, scripts)
- [ ] **GetDeviceConnectionsTool** - List OPC/device connections and status
- [ ] **ListWebDevEndpointsTool** - List custom web endpoints (if WebDev installed)

## Notes
- Implement Tier 1 first to fix immediate exploration failures
- Each tool should follow IAITool interface pattern (see existing tools in gateway/src/main/java/com/iai/ignition/gateway/tools/)
- Register in ToolRegistry.java after creation
