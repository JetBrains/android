plugins {
  id("com.android.library")
}
android {
  namespace = "com.example.androidlib"

  compileSdk = 33

  defaultConfig {
    minSdk = 21
    targetSdk = 33
  }

  flavorDimensions("type", "mode")
  productFlavors {
    create("typeone") {
        dimension = "type"
    }
    create("typetwo") {
        dimension = "type"
    }
    create("modeone") {
        dimension = "mode"
    }
    create("modetwo") {
        dimension = "mode"
    }
  }
}
