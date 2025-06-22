paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

plugins {
    `java-library`
    `maven-publish`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        name = "enginehub-maven"
        url = uri("https://maven.enginehub.org/repo/")
    }
}

dependencies {
    implementation(libs.org.javatuples.javatuples)
    implementation(libs.com.mysql.mysql.connector.j)
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly(libs.com.github.koca2000.noteblockapi)
    implementation("com.sk89q.worldedit:worldedit-bukkit:7.4.0-SNAPSHOT")

    paperweight.paperDevBundle("1.21.5-R0.1-SNAPSHOT")
}

group = "com.articreep"
version = "1.1"
description = "FillInTheWall"
java.sourceCompatibility = JavaVersion.VERSION_21

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

// Always build the shadow jar
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Disable default jar
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveFileName.set("FillInTheWall.jar")
}
