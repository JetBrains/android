val commonLibs by extra(mapOf(
  "kotlin_stdlib" to "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
  "testlibs" to "junit:junit:4.8"
))

dependencies {
    implementation(commonLibs["kotlin_stdlib"])
}
