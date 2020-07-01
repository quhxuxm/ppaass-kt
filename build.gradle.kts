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
}

allprojects {
    apply {
        plugin("io.spring.dependency-management")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("kotlin-allopen")
        plugin("org.jetbrains.kotlin.plugin.spring")
    }
    repositories {
        mavenLocal();
        maven("http://maven.aliyun.com/nexus/content/groups/public/")
        maven("http://maven.aliyun.com/repository/google/")
        maven("http://maven.aliyun.com/repository/jcenter/")
        mavenCentral();
    }
    dependencyManagement {
        dependencies {
            imports {
                mavenBom("org.springframework.boot:spring-boot-dependencies:2.3.1.RELEASE")
            }
            dependency("io.netty:netty-all:4.1.49.Final")
        }
    }
}

subprojects {
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }
}

