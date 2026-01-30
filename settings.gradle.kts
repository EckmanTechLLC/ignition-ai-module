pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://nexus.inductiveautomation.com/repository/public/")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenLocal()
        maven {
            url = uri("https://nexus.inductiveautomation.com/repository/public/")
        }

        // Node.js download repository for web builds
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
}

// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")  // Disabled due to project name with dots

rootProject.name = "ignition-ai-module"

// Link subprojects
include(":common", ":gateway", ":designer")
