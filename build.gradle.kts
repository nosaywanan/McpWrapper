plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)

}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    api("io.modelcontextprotocol:kotlin-sdk:0.7.2")
    api("io.ktor:ktor-server-cio:3.3.0")
}
