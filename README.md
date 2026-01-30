# Ignition 8.1 Module Template

A properly structured Ignition 8.1 module template based on the official SDK examples master branch. This template includes a working "Hello World" Perspective component and provides a solid foundation for building custom Ignition modules.

## Key Features

- **SDK Version:** 8.1.16 (compatible with Ignition 8.1.8+)
- **Gradle Build System:** Uses Gradle 7.4.2 with the ignition-module-tools plugin v0.1.1
- **Java 17:** Modern Java toolchain support
- **Simple JavaScript Approach:** No complex TypeScript/Webpack build - uses plain JavaScript for quick development
- **Working Hello World Component:** Fully functional Perspective component ready to customize
- **Multi-Scope Structure:** Properly organized common, gateway, and designer modules

## Requirements

- **JDK 17** - Required for building the module
- **Ignition 8.1.8+** - For testing the module
- **Git** - For version control (optional)

No additional tools needed! The Gradle wrapper handles all build dependencies automatically.

## Quick Start

### 1. Build the Module

```bash
cd /home/etl/projects/ignition-module-template-8.1
./gradlew build
```

The module file will be created at: `build/HelloWorldModule.unsigned.modl`

### 2. Install in Ignition

1. Open your Ignition Gateway webpage (typically `http://localhost:8088`)
2. Navigate to **Config → Modules**
3. Scroll down and click **Install or Upgrade a Module**
4. Upload `build/HelloWorldModule.unsigned.modl`
5. Restart the gateway when prompted

### 3. Use the Component

1. Open the Designer
2. Create a new Perspective view
3. Find "Hello World" in the component palette under the "Hello World" category
4. Drag it onto your view
5. Customize the properties:
   - `text`: The message to display
   - `fontSize`: Font size in pixels
   - `color`: Text color

## Project Structure

```
ignition-module-template-8.1/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Project structure and repositories
├── gradle.properties             # Gradle settings
├── gradle/
│   ├── libs.versions.toml        # SDK dependency versions
│   └── wrapper/                  # Gradle wrapper files
├── common/                       # Shared code between scopes
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/
│       │   └── com/example/ignition/common/
│       │       ├── HelloWorldModule.java       # Module metadata
│       │       └── HelloWorldComponent.java    # Component descriptor
│       └── resources/
│           └── helloworld.props.json           # Property schema
├── gateway/                      # Gateway scope code
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/
│       │   └── com/example/ignition/gateway/
│       │       └── GatewayHook.java            # Gateway lifecycle hook
│       └── resources/mounted/js/
│           └── helloworld.js                   # Browser component (plain JS)
├── designer/                     # Designer scope code
│   ├── build.gradle.kts
│   └── src/main/java/
│       └── com/example/ignition/designer/
│           └── DesignerHook.java               # Designer lifecycle hook
└── license.html                  # Module license
```

## Customizing the Template

### Change Module Identity

Edit `build.gradle.kts`:

```kotlin
allprojects {
    version = "1.0.0-SNAPSHOT"
    group = "com.yourcompany.ignition"  // Change this
}

ignitionModule {
    fileName.set("YourModuleName")      // Change this
    name.set("Your Module Name")        // Change this
    id.set("com.yourcompany.ignition.yourmodule")  // Change this
    moduleDescription.set("Your module description")  // Change this
    // ...
}
```

### Change Package Names

1. Update Java package structure in `common/`, `gateway/`, and `designer/` directories
2. Update package names in all `.java` files
3. Update the hook class names in `build.gradle.kts`:

```kotlin
hooks.putAll(
    mapOf(
        "com.yourcompany.ignition.gateway.GatewayHook" to "G",
        "com.yourcompany.ignition.designer.DesignerHook" to "D"
    )
)
```

### Change Component Identity

Edit `common/src/main/java/com/example/ignition/common/HelloWorldComponent.java`:

```java
public static final String COMPONENT_ID = "com.yourcompany.ignition.yourcomponent";
```

Also update the same ID in `gateway/src/main/resources/mounted/js/helloworld.js`:

```javascript
getComponentType() {
    return "com.yourcompany.ignition.yourcomponent";
}
```

### Add More Components

1. Create a new ComponentDescriptor in the `common` module
2. Create a new `.js` file in `gateway/src/main/resources/mounted/js/`
3. Register the component in both `GatewayHook.java` and `DesignerHook.java`
4. Add the new JavaScript resource to `HelloWorldModule.BROWSER_RESOURCES`

## 8.1 vs 8.3 Differences

This template is specifically for Ignition 8.1. Key differences from 8.3:

| Feature | 8.1 (This Template) | 8.3 |
|---------|---------------------|-----|
| **SDK Version** | 8.1.16 | 8.3.x |
| **Gradle Plugin** | io.ia.sdk.modl v0.1.1 | io.ia.sdk.modl v0.3.x+ |
| **Java Version** | 17 | 17+ |
| **Perspective API** | 2.1.x | 2.3.x+ |
| **Project Accessors** | Disabled (name compatibility) | Enabled by default |

### Why Use 8.1 Template?

- Your Ignition installation is 8.1.x
- You need to support older Ignition versions
- You want maximum compatibility with existing 8.1 deployments

## Development Tips

### Enable Unsigned Modules

For development, allow unsigned modules in Ignition:

1. Edit `<ignition>/data/ignition.conf`
2. Add to the `wrapper.java.additional` section:
   ```
   wrapper.java.additional.N=-Dignition.allowunsignedmodules=true
   ```
   (Replace N with the next available number)
3. Restart Ignition

### Clean Build

```bash
./gradlew clean build
```

### View Build Info

```bash
./gradlew tasks --all
```

### Deep Clean (removes all caches)

```bash
./gradlew deepClean
```

## Common Build Tasks

- `./gradlew build` - Build the module
- `./gradlew clean` - Clean build artifacts
- `./gradlew assemble` - Assemble the module without running tests
- `./gradlew tasks` - List available tasks

## Troubleshooting

### Build Fails with "Cannot generate project dependency accessors"

This is due to project naming with dots. Type-safe project accessors have been disabled in this template. Use `project(":name")` syntax instead of `projects.name`.

### Module Won't Load

1. Check Ignition gateway logs at `<ignition>/logs/wrapper.log`
2. Verify the module is compatible with your Ignition version
3. Ensure unsigned modules are enabled for development
4. Check for conflicting module IDs

### Component Doesn't Appear in Palette

1. Verify the component is registered in both Gateway and Designer hooks
2. Check that the component ID matches in Java and JavaScript
3. Restart the Designer
4. Check Designer console for errors

## Resources

- [Ignition SDK Examples (Master Branch)](https://github.com/inductiveautomation/ignition-sdk-examples/tree/master)
- [Ignition Module Tools](https://github.com/inductiveautomation/ignition-module-tools)
- [Ignition SDK Documentation](https://docs.inductiveautomation.com/display/SE/Ignition+SDK+Programmers+Guide)
- [Perspective Component API](https://docs.inductiveautomation.com/display/SE/Perspective+Component+API)

## License

Replace `license.html` with your actual license terms.

## Support

This template is based on the official Ignition SDK examples from Inductive Automation.
For Ignition-specific questions, consult the [Inductive Automation forums](https://forum.inductiveautomation.com/).
