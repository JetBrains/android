dependencies {
  compile("com.android.support:appcompat-v7:22.1.1")
  runtime("com.google.guava:guava:18.0")
  test(mapOf("extension" to "kotlin", "module" to "test"))
}
