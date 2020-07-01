buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.3.72"))
    }
}

group = "com.ppaass.kt"
version = "1.0-SNAPSHOT"

plugins {
    java
    kotlin("jvm") version "1.3.72"
}

allprojects {
    repositories {
        mavenLocal();
        mavenCentral();
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}

