plugins {
    java
    application
}

group = "com.krxoid"
version = "1.0-RELEASE"

repositories {
    mavenCentral()
}

dependencies {
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

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.krxoid.Main"
    }
}