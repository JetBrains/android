android {
  packagingOptions {
    jniLibs {
      excludes += "excludesX"
      pickFirsts += listOf("pickFirstsX")
      keepDebugSymbols += listOf("keepDebugSymbolsX", "keepDebugSymbols2")
    }
  }
}
