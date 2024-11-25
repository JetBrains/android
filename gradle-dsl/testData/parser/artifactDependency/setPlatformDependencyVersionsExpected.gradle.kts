val map by extra(mapOf("group" to "mapGroup", "name" to "mapName", "version" to "2.0"))
val string by extra("stringGroup:stringName:2.7")
val version by extra("3.14")

dependencies {
  implementation(enforcedPlatform("group:name:2.0-RC1"))
  implementation(enforcedPlatform(map))
  implementation(platform(string))
  implementation(platform("group:name:2.71"))
  implementation(platform(mapOf("group" to "argGroup", "name" to "argName", "version" to "2.718")))
}