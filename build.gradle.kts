plugins {
  id("org.jetbrains.kotlin.jvm").version("1.3.21")

  application
}

repositories {
  jcenter()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("io.arrow-kt:arrow-core-data:0.9.0")
  implementation("io.arrow-kt:arrow-core-extensions:0.9.0")
  implementation("com.google.api-client:google-api-client:1.23.0")
  implementation("com.google.oauth-client:google-oauth-client-jetty:1.23.0")
  implementation("com.google.apis:google-api-services-tasks:v1-rev49-1.23.0")

  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
  mainClassName = "com.grysz.AppKt"
}
