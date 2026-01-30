plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":common"))

    compileOnly(libs.ignition.common)
    compileOnly(libs.ignition.designer.api)
    implementation(libs.ignition.perspective.designer)
    implementation(libs.ignition.perspective.common)
}
