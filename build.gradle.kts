import com.github.gradle.node.npm.task.NpmTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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
    distribution
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

// Desktop launcher, built separately into scoring-launcher.jar (sources in src/desktop/java)
val desktop by sourceSets.creating

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.43.2.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("io.javalin:javalin:6.3.0")
    implementation(fileTree("libs") { include("*.jar") })

    "desktopImplementation"("com.formdev:flatlaf:3.2.5")
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

val desktopJar by tasks.registering(ShadowJar::class) {
    group = "build"
    description = "Build the desktop launcher JAR"
    archiveFileName = "scoring-launcher.jar"
    from(desktop.output)
    configurations = listOf(project.configurations["desktopRuntimeClasspath"])
    manifest {
        attributes(
            "Main-Class" to "org.thingai.app.desktop.DesktopLauncher",
            "Implementation-Title" to "Scoring System Desktop Launcher",
            "Implementation-Version" to project.version,
        )
    }
}

/* ==== Distributions ==== */
fun CopySpec.appContents() {
    from(tasks.shadowJar) { into("app") }
    from(desktopJar) { into("app") }
    from("vendor/binary") { into("binary") }
    from("distribution/scripts")
    from("docs/user-guide.md")
}

distributions {
    main {
        contents { appContents() }
    }
    // Platform zips bundle the matching JRE: distribution name to vendor/jre folder
    mapOf("windows" to "windows", "mac" to "macos", "linux" to "linux").forEach { (name, jreDir) ->
        create(name) {
            contents {
                appContents()
                from("vendor/jre/$jreDir") { into("jre") }
            }
        }
        tasks.register("package${name.replaceFirstChar { it.uppercase() }}") {
            group = "distribution"
            description = "Build $name distribution zip with bundled JRE"
            dependsOn("${name}DistZip")
        }
    }
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
}

tasks.npmInstall {
    workingDir = webUiDir
}

val npmBuild by tasks.registering(NpmTask::class) {
    dependsOn(tasks.npmInstall)
    workingDir = webUiDir
    args = listOf("run", "build")
}

// With -PskipWebuiBuild=true, copies the existing webui/dist build (if any) without rebuilding
val copyWebUi by tasks.registering(Copy::class) {
    if (!skipWebuiBuild) dependsOn(npmBuild)
    from("webui/dist/webui/browser")
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.processResources {
    if (hasWebUi) dependsOn(copyWebUi)
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
