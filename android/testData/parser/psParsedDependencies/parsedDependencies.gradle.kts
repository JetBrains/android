dependencies {
  api("com.android.support:appcompat-v7:+")
  api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
  implementation("com.example.libs:lib1:1.0")
  debugImplementation("com.example.libs:lib1:1.0")
  releaseImplementation("com.example.libs:lib1:0.9.1")
}
