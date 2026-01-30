import com.github.gradle.node.yarn.task.YarnTask
import com.github.gradle.node.npm.task.NpmTask

plugins {
    java
    id("com.github.node-gradle.node") version("3.2.1")
}

val projectOutput: String by extra("$buildDir/generated-resources/")

node {
    version.set("16.15.0")
    yarnVersion.set("1.22.18")
    npmVersion.set("8.5.5")
    download.set(true)
    nodeProjectDir.set(file(project.projectDir))
}

val yarnPackages by tasks.registering(YarnTask::class) {
    description = "Executes 'yarn' to install npm dependencies for the yarn workspace."
    args.set(listOf("install", "--verbose"))

    inputs.files(
        fileTree(project.projectDir).matching {
            include("**/package.json", "**/yarn.lock")
        }
    )

    outputs.dirs(
        file("node_modules"),
        file("packages/client/node_modules"),
        file("packages/designer/node_modules")
    )

    dependsOn("${project.path}:yarn", ":web:npmSetup")
}

val webpack by tasks.registering(NpmTask::class) {
    group = "Ignition Module"
    description = "Runs 'npm run build', executing the build script of the web project's root package.json"

    args.set(listOf("run", "build"))
    dependsOn(yarnPackages)

    inputs.files(project.fileTree("packages").matching {
        exclude("**/node_modules/**", "**/dist/**", "**/.awcache/**", "**/yarn-error.log")
    }.toList())

    outputs.files(fileTree(projectOutput))
}

val deleteDistFolders by tasks.registering(Delete::class) {
    delete(file("packages/designer/dist/"))
    delete(file("packages/client/dist/"))
}

tasks {
    processResources {
        dependsOn(webpack, yarnPackages)
    }

    clean {
        dependsOn(deleteDistFolders)
    }
}

val deepClean by tasks.registering {
    doLast {
        delete(file("packages/designer/node_modules"))
        delete(file("packages/designer/.gradle"))
        delete(file("packages/client/node_modules"))
        delete(file("packages/client/.gradle"))
        delete(file(".gradle"))
        delete(file("node_modules"))
    }
    dependsOn(project.tasks.named("clean"))
}

project(":gateway")?.tasks?.named("processResources")?.configure {
    dependsOn(webpack)
}

sourceSets {
    main {
        output.dir(projectOutput, "builtBy" to listOf(webpack))
    }
}
