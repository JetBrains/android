android {
  defaultConfig {
    externalNativeBuild {
      cmake {
        abiFilters("abiFilter1", "abiFilter2")
        arguments += listOf("argument1", "argument2")
        cFlags("cFlag1", "cFlag2")
        cppFlags += listOf("cppFlag1", "cppFlag2")
        targets("target1", "target2")
      }
      ndkBuild {
        abiFilters += setOf("abiFilter3", "abiFilter4")
        arguments("argument3", "argument4")
        cFlags += listOf("cFlag3", "cFlag4")
        cppFlags("cppFlag3", "cppFlag4")
        targets += setOf("target3", "target4")
      }
    }
    ndk {
      abiFilters += setOf("abiFilter5")
      abiFilters += setOf("abiFilter6")
      abiFilters += setOf("abiFilter7")
      cFlags = "-DcFlags"
      jobs = 12
      ldLibs += listOf("ldLibs8", "ldLibs9", "ldLibs10")
      moduleName = "myModule"
      stl = "stlport"
    }
  }
}
