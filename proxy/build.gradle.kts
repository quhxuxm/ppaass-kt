dependencies {
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-log4j2")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation(group = "org.springframework.boot", name = "spring-boot-autoconfigure")
    implementation(group = "org.springframework.boot", name = "spring-boot-autoconfigure-processor")
    implementation(group = "io.netty", name = "netty-all")
    implementation(group = "org.slf4j", name = "slf4j-api")
    implementation(project(":common"))
}
