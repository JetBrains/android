dependencies {
  compile("org.gradle.test.classifiers:service:${rootProject.extra["version"]}")
  compile("com.google.guava:${rootProject.extra["name"]}:+")
}