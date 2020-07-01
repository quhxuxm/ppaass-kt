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