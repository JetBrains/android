val map by extra(mapOf("group" to "mapGroup", "name" to "mapName", "version" to "3.0"))
val string by extra("stringGroup:stringName:3.1")
val version by extra("3.14")

dependencies {
  implementation(enforcedPlatform(map))
}