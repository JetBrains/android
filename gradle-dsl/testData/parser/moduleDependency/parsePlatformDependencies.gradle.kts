dependencies {
  implementation(enforcedPlatform(project(":foo")))
  implementation(platform(project(mapOf("path" to ":bar"))))
  implementation(enforcedPlatform(project(mapOf("path" to ":baz", "configuration" to "paidRelease"))))
}