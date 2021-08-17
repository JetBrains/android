android {
  splits {
    abi {
      exclude += listOf("abi-exclude-2")
      include += listOf("abi-include-1")
    }
    density {
      compatibleScreens("screen2")
      exclude += listOf("density-exclude-1")
      include += listOf("density-include-2")
    }
    language {
      include("language-include-1")
    }
  }
}
