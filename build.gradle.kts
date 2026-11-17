import com.github.gradle.node.npm.task.NpmTask
import proguard.gradle.ProGuardTask

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.node-gradle.node") version "7.0.2"
}

group = "org.thingai.app.scoringsystem"
version = "2.0"
description = "Scoring System for Robotics Competitions"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "org.thingai.app.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.43.2.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("io.javalin:javalin:6.3.0")
    implementation(fileTree("libs") { include("*.jar") })
    implementation("com.formdev:flatlaf:3.2.5")
}

tasks.test {
    useJUnitPlatform()
}

/* ==== Jars ==== */
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveFileName = "scoring-system.jar"
    archiveClassifier = ""
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "org.thingai.app.Main",
            "Implementation-Title" to "Scoring System",
            "Implementation-Version" to project.version,
        )
    }
}

/* ==== Native packages (jpackage) ==== */
// jpackage can't cross-build: Windows hosts produce exe/msi, Linux hosts deb/rpm
val isWindows = System.getProperty("os.name").startsWith("Windows")
val packageOs = if (isWindows) "windows" else "linux"
val packageTypes = if (isWindows) listOf("exe", "msi") else listOf("deb", "rpm")
val jpackageJdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }

// jpackage input: the app jar plus this OS's MatchMaker binary, which ends up next to the jar
val jpackageInput by tasks.registering(Sync::class) {
    from(tasks.shadowJar)
    from("vendor/binary") { include(if (isWindows) "MatchMaker.exe" else "MatchMaker") }
    into(layout.buildDirectory.dir("jpackage/input"))
}

val jpackage by tasks.registering {
    group = "distribution"
    description = "Build native installers for the current OS (${packageTypes.joinToString()})"
}

packageTypes.forEach { type ->
    val task = tasks.register<Exec>("jpackage${type.replaceFirstChar { it.uppercase() }}") {
        dependsOn(jpackageInput)
        args(
            "@distribution/config/jpackage-common.txt",
            "@distribution/config/jpackage-$packageOs.txt",
            "--app-version", project.version,
            "--type", type,
        )
        // Resolved at execution time so configuring the build doesn't provision JDK 21
        doFirst {
            executable = jpackageJdk.get().metadata.installationPath
                .file(if (isWindows) "bin/jpackage.exe" else "bin/jpackage").asFile.path
        }
    }
    jpackage { dependsOn(task) }
}

/* ==== Frontend ==== */
val webUiDir = file("webui")
val hasWebUi = file("webui/package.json").exists()
val skipWebuiBuild = findProperty("skipWebuiBuild") == "true"

// Use a Gradle-managed Node so the build doesn't depend on PATH or NVM
node {
    version = "24.9.0"
    download = true
    workDir = layout.buildDirectory.dir("nodejs")
    npmWorkDir = layout.buildDirectory.dir("npm")
    nodeProjectDir = webUiDir
}

val npmBuild by tasks.registering(NpmTask::class) {
    dependsOn(tasks.npmInstall)
    args = listOf("run", "build")
}

// Bundles the web UI into the jar's static/ folder. processResources must do the copy itself: it deletes files
// in its output folder that it didn't produce, which wiped a separate copy task's output.
// With -PskipWebuiBuild=true, bundles the existing webui/dist build (if any) without rebuilding.
tasks.processResources {
    if (hasWebUi && !skipWebuiBuild) dependsOn(npmBuild)
    from("webui/dist/webui/browser") { into("static") }
}

/* ==== Obfuscation ==== */
tasks.register<ProGuardTask>("proguard") {
    group = "build"
    description = "Obfuscate the application classes using ProGuard"
    configuration("proguard-rules.pro")
    injars(sourceSets.main.get().output)
    outjars(layout.buildDirectory.file("libs/scoring-system-obfuscated.jar"))
    libraryjars(sourceSets.main.get().compileClasspath)
    libraryjars("${System.getProperty("java.home")}/jmods")
}
