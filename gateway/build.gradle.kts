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
    compileOnly(libs.ignition.gateway.api)
    implementation(libs.ignition.perspective.gateway)
    implementation(libs.ignition.perspective.common)
    compileOnly(libs.ia.gson)
}
