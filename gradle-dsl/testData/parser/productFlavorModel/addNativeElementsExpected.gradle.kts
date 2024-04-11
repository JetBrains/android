android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters += setOf("abiFilterX")
        arguments += listOf("argumentX")
        cFlags += listOf("cFlagX")
        cppFlags += listOf("cppFlagX")
        targets += setOf("targetX")
      }
      ndkBuild {
        abiFilters += setOf("abiFilterY")
        arguments += listOf("argumentY")
        cFlags += listOf("cFlagY")
        cppFlags += listOf("cppFlagY")
        targets += setOf("targetY")
      }
    }
    ndk {
      abiFilters += setOf("abiFilterZ")
    }
  }
}
