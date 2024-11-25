val map by extra(mapOf("group" to "mapGroup", "name" to "mapName", "version" to "3.0"))
val string by extra("stringGroup:stringName:3.1")
val version by extra("3.14")

dependencies {
  implementation(enforcedPlatform("group:name:3.1415"))
  implementation(enforcedPlatform(map))
  implementation(platform(string))
  implementation(platform("group:name:$version"))
  implementation(platform(mapOf("group" to "argGroup", "name" to "argName", "version" to "3.141")))
}