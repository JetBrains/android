android {
  kotlinOptions {
    jvmTarget = "9"
    freeCompilerArgs = listOf("-XX:1", "-XX:2")
    useIR = true
  }
}
