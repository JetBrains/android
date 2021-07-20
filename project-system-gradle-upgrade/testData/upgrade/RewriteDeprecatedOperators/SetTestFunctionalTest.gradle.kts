android {
  defaultConfig {
    setTestFunctionalTest(true)
  }
  productFlavors {
    create("foo") {
      setTestFunctionalTest(false)
    }
  }
}
