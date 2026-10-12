plugins {
    `java-library`
}

group = "com.example.livestatus"
version = "1.0.0"
description = "Adds a LIVE indicator to an authorized player's nametag when streaming Minecraft on YouTube"

val paperVersion = project.findProperty("paperVersion") as? String ?: "1.21.4-R0.1-SNAPSHOT"

// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(21))
//     }
// }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("me.clip:placeholderapi:2.11.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("LiveStatus")
    archiveClassifier.set("")
    archiveVersion.set("1.0.0")
}
