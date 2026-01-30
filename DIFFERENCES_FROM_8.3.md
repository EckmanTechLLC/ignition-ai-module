# Differences Between 8.1 and 8.3 Templates

This document outlines the key differences between the Ignition 8.1 and 8.3 module templates.

## SDK and Tooling

### Gradle Plugin Version
- **8.1:** `io.ia.sdk.modl` version `0.1.1`
- **8.3:** `io.ia.sdk.modl` version `0.3.0+`

The 8.1 plugin is more mature but lacks some newer features. The 8.3 plugin includes improvements like better build caching and enhanced module signing.

### SDK Version
- **8.1:** SDK version `8.1.16`
- **8.3:** SDK version `8.3.x` (typically 8.3.0 or higher)

Newer SDKs may include additional APIs and breaking changes.

### Perspective API Version
- **8.1:** `@inductiveautomation/perspective-client` version `^2.1.16`
- **8.3:** `@inductiveautomation/perspective-client` version `^2.3.x`

The 2.3.x API includes new component capabilities and may have different patterns for state management.

## Build Configuration

### Type-Safe Project Accessors
- **8.1:** Disabled by default (project names with dots cause issues)
- **8.3:** Enabled by default

**8.1 approach:**
```kotlin
implementation(project(":common"))
```

**8.3 approach:**
```kotlin
implementation(projects.common)
```

### Project Naming
- **8.1:** Project names can include dots but type-safe accessors must be disabled
- **8.3:** Recommended to use naming convention `[a-zA-Z]([A-Za-z0-9\-_])*` for accessors

## Web Frontend Build

### This Template's Approach (Simplified)
- **8.1 Template:** Uses plain JavaScript without TypeScript/Webpack
- **Rationale:** Simplifies the build process and reduces dependencies
- **Tradeoff:** Less type safety, no modern JS features

### Full Production Approach
Both 8.1 and 8.3 can use the full TypeScript/Webpack build system:
- TypeScript 4.2.3+
- Webpack 5.x
- Yarn workspaces with Lerna
- Sass/SCSS support

The main difference is the `@inductiveautomation/perspective-client` API version.

## API Changes

### Component Registration (Minimal Changes)
The core component registration APIs remain similar:
```java
// Both 8.1 and 8.3 use similar patterns
ComponentDescriptorImpl.ComponentBuilder.newBuilder()
    .setId(COMPONENT_ID)
    .setModuleId(MODULE_ID)
    .setSchema(SCHEMA)
    // ...
    .build();
```

### Hook Classes
Hook classes are nearly identical between versions:
```java
// AbstractGatewayModuleHook
// AbstractDesignerModuleHook
```

Minor method signature changes may exist in specific APIs.

## Module Structure

### File Organization
Both versions use the same multi-project structure:
```
├── common/        # Shared code
├── gateway/       # Gateway scope
├── designer/      # Designer scope
└── web/          # Frontend (optional in this template)
```

### Resource Mounting
Both use the same resource mounting mechanism:
```java
@Override
public Optional<String> getMountedResourceFolder() {
    return Optional.of("mounted");
}
```

## Compatibility Considerations

### Forward Compatibility
- **8.1 modules MAY work on 8.3** if they don't use APIs removed in 8.3
- Testing required for each specific case

### Backward Compatibility
- **8.3 modules WILL NOT work on 8.1** if they use newer APIs
- Must target 8.1 SDK explicitly for 8.1 compatibility

## Upgrading from 8.1 to 8.3

To upgrade this template to 8.3:

1. **Update Gradle Plugin:**
   ```kotlin
   id("io.ia.sdk.modl") version("0.3.0")  // or latest
   ```

2. **Update SDK Version:**
   ```toml
   [versions]
   ignition = "8.3.0"
   ```

3. **Update Required Ignition Version:**
   ```kotlin
   requiredIgnitionVersion.set("8.3.0")
   ```

4. **Enable Type-Safe Accessors:**
   - Rename project to use compatible naming
   - Uncomment in `settings.gradle.kts`:
     ```kotlin
     enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
     ```
   - Update project references to use `projects.x` syntax

5. **Update Perspective Dependencies (if using web build):**
   ```json
   "@inductiveautomation/perspective-client": "^2.3.0"
   "@inductiveautomation/perspective-designer": "^2.3.0"
   ```

6. **Test Thoroughly:**
   - Some APIs may have changed
   - Component behavior may differ
   - Build any deprecated API warnings

## Recommendations

### Use 8.1 Template When:
- Your target Ignition version is 8.1.x
- You need to support older installations
- You want maximum compatibility with existing systems

### Upgrade to 8.3 When:
- Your minimum Ignition version is 8.3.0+
- You need new 8.3 Perspective features
- You want the latest SDK improvements

## Additional Notes

### Node.js Versions
- **8.1:** Node 16.15.0, Yarn 1.22.18, npm 8.5.5
- **8.3:** May use newer Node versions

### Java Toolchain
- Both use Java 17 toolchain
- Newer 8.3 SDKs may require Java 17+

### Repository URLs
Both versions use the same IA Nexus repositories:
- Maven/Gradle: `https://nexus.inductiveautomation.com/repository/public/`
- npm packages: `https://nexus.inductiveautomation.com/repository/node-packages/`
