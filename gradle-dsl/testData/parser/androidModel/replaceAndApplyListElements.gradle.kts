android {
  aidlPackagedList += listOf("src/main/aidl/foo.aidl", "src/main/aidl/bar.aidl")
  assetPacks += setOf(":a1", ":a2")
  dynamicFeatures = mutableSetOf(":f1", ":f2")
  flavorDimensions("abi", "version")
}
