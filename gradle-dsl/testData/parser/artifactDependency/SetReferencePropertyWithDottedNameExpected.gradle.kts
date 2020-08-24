val versions by extra(mapOf("first" to "3.6", "second" to "3.5"))

dependencies {
  testCompile("org.gradle.test.classifiers:service:${versions["first"]}")
}
