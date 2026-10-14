dependencies {
  compile("com.google.guava", "guava", "18.0", ext = "aar", classifier = "class")
  runtime("org.gradle.test.classifiers", "service")
  compile(name = "guava", version = "18.0")
  api(name = "grouped", version = "1.0")
}

