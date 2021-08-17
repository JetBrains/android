plugins {
  id("com.android.application")
}

android {
  buildTypes {
    getByName("release") {
      isDebuggable = false
    }
  }
  flavorDimensions("tier")
  productFlavors {
    create("paid") {
      setDimension("tier")
    }
    free {
      setDimension("tier")
    }
  }
}

dependencies {
  implementation("junit:junit:4.13")
}
