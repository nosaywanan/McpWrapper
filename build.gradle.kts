plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("com.vanniktech.maven.publish") version "0.34.0"
//    id("maven-publish")
    id("signing")
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
mavenPublishing {
    publishToMavenCentral()

    signAllPublications()
}
mavenPublishing {
    coordinates("io.github.nosaywanan", "mcp-wrapper", "0.7.2")

    pom {
        name.set("MCP Android")
        description.set("A Wrapped MCP-kotlin SDK in Android")
        inceptionYear.set("2025")
        url.set("https://github.com/nosaywanan/McpWrapper/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("nosaywanan")
                name.set("nosaywanan")
                url.set("https://github.com/nosaywanan/")
            }
        }
        scm {
            url.set("https://github.com/nosaywanan/McpWrapper/")
            connection.set("scm:git:git://github.com/nosaywanan/McpWrapper.git")
            developerConnection.set("scm:git:ssh://git@github.com/nosaywanan/McpWrapper.git")
        }
    }
}
signing {
    useInMemoryPgpKeys(
        project.findProperty("signing.keyId") as String?,
        project.findProperty("signing.key") as String?,
        project.findProperty("signing.password") as String?
    )
//    sign(publishing.publications["release"])
}
