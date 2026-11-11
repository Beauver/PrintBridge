plugins {
    java
    jacoco
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.quarkus:quarkus-hibernate-validator")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-hibernate-orm")
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-elytron-security-common")
    testImplementation("io.quarkus:quarkus-junit")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-jacoco")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

group = "com.beauver.printbridge"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Coverage: plain unit tests via the Gradle JaCoCo agent, @QuarkusTest tests via quarkus-jacoco,
// both written to the same data file and reported together.
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    extensions.configure(JacocoTaskExtension::class) {
        excludeClassLoaders = listOf("*QuarkusClassLoader")
        includes = listOf("com.beauver.*")
        setDestinationFile(layout.buildDirectory.file("jacoco-quarkus.exec").get().asFile)
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    executionData(layout.buildDirectory.file("jacoco-quarkus.exec"))
    reports {
        xml.required = true
        html.required = true
    }
}
