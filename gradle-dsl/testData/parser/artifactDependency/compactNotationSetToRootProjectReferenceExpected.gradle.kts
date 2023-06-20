dependencies {
  testCompile("org.gradle.test.classifiers:service:${rootProject.extra["version"]}")
  testCompile("com.google.guava:${rootProject.extra["name"]}:+")
}
