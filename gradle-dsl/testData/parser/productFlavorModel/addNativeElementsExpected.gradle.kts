android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters += listOf("abiFilterX")
        arguments += listOf("argumentX")
        cFlags += listOf("cFlagX")
        cppFlags += listOf("cppFlagX")
        targets += listOf("targetX")
      }
      ndkBuild {
        abiFilters += listOf("abiFilterY")
        arguments += listOf("argumentY")
        cFlags += listOf("cFlagY")
        cppFlags += listOf("cppFlagY")
        targets += listOf("targetY")
      }
    }
    ndk {
      abiFilters("abiFilterZ")
    }
  }
}
