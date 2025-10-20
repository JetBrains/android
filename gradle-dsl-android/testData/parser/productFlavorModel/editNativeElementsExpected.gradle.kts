android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilter1", "abiFilterX")
        arguments += listOf("argument1", "argumentX")
        cFlags("cFlag1", "cFlagX")
        cppFlags += listOf("cppFlag1", "cppFlagX")
        targets("target1", "targetX")
      }
      ndkBuild {
        abiFilters += setOf("abiFilter3", "abiFilterY")
        arguments("argument3", "argumentY")
        cFlags += listOf("cFlag3", "cFlagY")
        cppFlags("cppFlag3", "cppFlagY")
        targets += setOf("target3", "targetY")
      }
    }
    ndk {
      abiFilters += setOf("abiFilter5")
      abiFilters += setOf("abiFilterZ")
      abiFilters += setOf("abiFilter7")
      cFlags = "-DcFlagZ"
      jobs = 26
      ldLibs += listOf("ldLibs8", "ldLibsZ", "ldLibs10")
      moduleName = "ZModule"
      stl = "ztlport"
    }
  }
}
