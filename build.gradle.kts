plugins {
    java
}

group = "dev.anchorstack"
// Die Release-Pipeline setzt die Version aus dem Tag, lokal gilt der Standardwert
version = providers.gradleProperty("pluginVersion").getOrElse("0.1.0")

repositories {
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
