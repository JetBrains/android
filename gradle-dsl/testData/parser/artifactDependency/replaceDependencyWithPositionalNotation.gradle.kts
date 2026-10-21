dependencies {
  compile("com.google.code.guice", "guice", "1.0")
  runtime("org.gradle.test.classifiers", "service", "1.0")
  testCompile("org.gradle.test", "removed", "1.0")
  compile("com.google", "guava", "1.0")
  api("org.gradle.test", "grouped", "1.0")
}

