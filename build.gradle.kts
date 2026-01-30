plugins {
    base
    id("io.ia.sdk.modl") version("0.1.1")
}

allprojects {
    version = "1.0.0-SNAPSHOT"
    group = "com.iai.ignition"
}

ignitionModule {
    // name of the .modl file to build
    fileName.set("IgnitionAI")

    // module xml configuration
    name.set("Ignition AI")
    id.set("com.iai.ignition.ai")
    moduleVersion.set("${project.version}")
    moduleDescription.set("AI assistant for understanding and exploring Ignition systems via natural language queries.")
    requiredIgnitionVersion.set("8.1.8")
    license.set("license.html")

    // Module dependencies - depends on Perspective
    moduleDependencies.put("com.inductiveautomation.perspective", "DG")

    // Map Gradle Project Path to Ignition Scope
    projectScopes.putAll(
        mapOf(
            ":gateway" to "G",
            ":designer" to "D",
            ":common" to "GD"
        )
    )

    // Hook classes loaded by Ignition in each scope
    hooks.putAll(
        mapOf(
            "com.iai.ignition.gateway.GatewayHook" to "G",
            "com.iai.ignition.designer.DesignerHook" to "D"
        )
    )

    // Skip module signing for development
    skipModlSigning.set(true)
}

val deepClean by tasks.registering {
    dependsOn(allprojects.map { "${it.path}:clean" })
    description = "Executes clean tasks and removes node plugin caches."
    doLast {
        delete(file(".gradle"))
    }
}
