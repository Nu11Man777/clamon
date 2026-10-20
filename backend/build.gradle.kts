plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "mx.ipn.cic.clamon"
version = "0.1.0"
description = "Backend de CLAMON"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["awsSdkVersion"] = "2.56.0"
extra["googleAuthVersion"] = "1.54.0"

dependencyManagement {
	imports {
		mavenBom("software.amazon.awssdk:bom:${property("awsSdkVersion")}")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("software.amazon.awssdk:s3")
	implementation("software.amazon.awssdk:apache5-client")
	implementation("com.google.auth:google-auth-library-oauth2-http:${property("googleAuthVersion")}")
	runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
	archiveFileName = "app.jar"
}

tasks.named<Jar>("jar") {
	enabled = false
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:deprecation")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	val archivo = file(".env")
	if (archivo.exists()) {
		archivo.readLines()
			.map { it.trim() }
			.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
			.forEach { linea ->
				val clave = linea.substringBefore("=").trim()
				val valor = linea.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
				if (System.getenv(clave) == null) {
					environment(clave, valor)
				}
			}
	}
}
