android {
  aidlPackagedList += listOf("src/main/aidl/quux.aidl", "src/main/aidl/bar.aidl")
  assetPacks += setOf(":a1", ":b2")
  dynamicFeatures = mutableSetOf(":f1", ":g2")
  flavorDimensions("xyz", "version")
}
