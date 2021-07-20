android {
  defaultConfig {
    testHandleProfiling = true
  }
  productFlavors {
    create("foo") {
      testHandleProfiling = false
    }
  }
}
