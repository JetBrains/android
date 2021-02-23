android {
  splits {
    abi {
      isEnable = true
      exclude += listOf("abi-exclude")
      include += listOf("abi-include")
      isUniversalApk = false
    }
    density {
      setAuto(false)
      compatibleScreens("screen")
      isEnable = true
      exclude += listOf("density-exclude")
      include += listOf("density-include", "density-include2")
    }
    language {
      isEnable = false
      include("language-include", "language-include2")
    }
  }
}
