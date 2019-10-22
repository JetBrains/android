android {
  // TODO(b/143193213): MutableSet
  dynamicFeatures = listOf(":f1", ":g2")
  flavorDimensions("xyz", "version")
}
