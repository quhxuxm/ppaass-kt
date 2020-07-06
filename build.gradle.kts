import org.jetbrains.kotlin.gradle.tasks.KotlinCompile;

buildscript {
    repositories {
        mavenLocal()
        maven("http://maven.aliyun.com/nexus/content/groups/public/")
        maven("http://maven.aliyun.com/repository/google/")
        maven("http://maven.aliyun.com/repository/jcenter/")
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.3.72"))
        classpath(kotlin("allopen", version = "1.3.72"))
    }
}

group = "com.ppaass.kt"
version = "1.0-SNAPSHOT"

plugins {
    java
    kotlin("jvm") version "1.3.72"
    id("org.springframework.boot") version "2.3.1.RELEASE"
    id("io.spring.dependency-management") version "1.0.6.RELEASE"
    kotlin("kapt") version "1.3.72"
}

allprojects {
    apply {
        plugin("io.spring.dependency-management")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("kotlin-allopen")
        plugin("org.jetbrains.kotlin.plugin.spring")
        plugin("kotlin-kapt")
    }
    repositories {
        mavenLocal()
        maven("http://maven.aliyun.com/nexus/content/groups/public/")
        maven("http://maven.aliyun.com/repository/google/")
        maven("http://maven.aliyun.com/repository/jcenter/")
        mavenCentral()
    }
    dependencyManagement {
        dependencies {
            imports {
                mavenBom("org.springframework.boot:spring-boot-dependencies:2.3.1.RELEASE")
            }
            dependency("io.netty:netty-all:4.1.49.Final")
            dependency("org.lz4:lz4-java:1.7.1")
            dependency("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "11"
    }
}

subprojects {
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}

