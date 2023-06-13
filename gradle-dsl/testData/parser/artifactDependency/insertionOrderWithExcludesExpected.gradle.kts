dependencies {
  "feature"("com.example.libs3:lib3:3.0"){
    exclude(mapOf("group" to "com.example.libs2", "module" to "lib2"))
  }
  api("com.example.libs1:lib1:1.0") {
    exclude(mapOf("group" to "a", "module" to "b"))
  }
  implementation("androidx.constraintlayout:constraintlayout:1.1.3")
}
