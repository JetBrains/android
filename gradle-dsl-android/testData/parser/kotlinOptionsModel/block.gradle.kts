android {
  kotlinOptions {
    jvmTarget = "1.6"
    useIR = false
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
  }
}
