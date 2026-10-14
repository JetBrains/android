val serviceVersion = "1.0"

object Versions {
  const val qualified = "2.0"
}

dependencies {
  runtime("org.gradle.test.classifiers", "service", serviceVersion)
  compile("com.google.code.guice", "guice", version = "1.0")
  testCompile("org.gradle.test", "unversioned")
  implementation("com.h2database", name = "h2", version = "1.4.199")
  api("org.gradle.test", "qualified", Versions.qualified)
  add("implementation", "com.google.guava:guava:18.0")
}

