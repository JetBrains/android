android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilterX")
        arguments("argumentX")
        cFlags("cFlagX")
        cppFlags("cppFlagX")
        targets("targetX")
      }
      ndkBuild {
        abiFilters("abiFilterY")
        arguments("argumentY")
        cFlags("cFlagY")
        cppFlags("cppFlagY")
        targets("targetY")
      }
    }
    ndk {
      abiFilters += setOf("abiFilterZ")
      ldLibs += listOf("ldLibsZ")
    }
  }
}
