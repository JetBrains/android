android {
  aidlPackagedList += listOf("src/main/aidl/foo.aidl")
  assetPacks += setOf(":a1")
  dynamicFeatures = mutableSetOf(":f")
  flavorDimensions("xyz")
}
