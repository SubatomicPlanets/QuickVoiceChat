plugins {
    id("java")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

group = "com.subatomicplanets.quickvoicechat"
version = "1.0.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("io.netty:netty-all:4.1.136.Final")

    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.subatomicplanets.quickvoicechat.QuickVoiceChat"
    }
    archiveFileName.set("QuickVoiceChat.jar")
}

tasks.test {
    useJUnitPlatform()
}