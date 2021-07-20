android {
  defaultConfig {
    setTestHandleProfiling(true)
  }
  productFlavors {
    create("foo") {
      setTestHandleProfiling(false)
    }
  }
}
