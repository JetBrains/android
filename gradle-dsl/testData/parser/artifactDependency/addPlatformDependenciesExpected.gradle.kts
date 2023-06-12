val map by extra(mapOf("group" to "mapGroup", "name" to "mapName", "version" to "3.0"))
val string by extra("stringGroup:stringName:3.1")
val version by extra("3.14")

dependencies {
  implementation(enforcedPlatform(map))
  implementation(platform(string))
  implementation(platform("group:name:$version"))
  implementation(platform(mapOf("group" to "argGroup", "name" to "argName", "version" to "3.141")))
  implementation(enforcedPlatform("group:name:3.1415"))
  implementation(platform("androidx.compose:compose-bom:2022.10.0"))
  implementation(enforcedPlatform("org.springframework:spring-framework-bom:5.1.9.RELEASE"))
  implementation(platform("com.example:foo:$version"))
}