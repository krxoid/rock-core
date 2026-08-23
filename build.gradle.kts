plugins {
    java
    application
    id("com.gradleup.shadow") version "9.2.2"
}

group = "com.krxoid"
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.krxoid.Main")
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
    }

    startScripts {
        enabled = false
    }

    distZip {
        enabled = false
    }

    distTar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}