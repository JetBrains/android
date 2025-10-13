android {
  aidlPackagedList += listOf("src/main/aidl/foo.aidl")
  assetPacks += setOf(":a1")
  dynamicFeatures += setOf(":f")
  flavorDimensions += listOf("xyz")
}
