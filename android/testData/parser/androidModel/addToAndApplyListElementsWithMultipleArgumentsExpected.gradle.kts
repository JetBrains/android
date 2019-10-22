android {
  // TODO(b/143193213): MutableSet
  dynamicFeatures = listOf(":f1", ":f2", ":f")
  flavorDimensions("abi", "version", "xyz")
}
