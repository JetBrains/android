extra["prop1"] = "value"
var prop2 by extra(25)
var prop3 by extra(true)
var prop4 by extra(mapOf("key" to "val"))
var prop5 by extra(listOf("val1", "val2", "val3"))
extra["prop6"] = 25.3

android {
  signingConfigs {
    create("release") {
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}