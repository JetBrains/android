android {
  lint {
    checkOnly += setOf("checkOnly-id")
    disable += setOf("disable-id")
    enable += setOf("enable-id")
    error += setOf("error-id")
    fatal += setOf("fatal-id")
    ignore += setOf("ignore-id")
    informational += setOf("informational-id")
    warning += setOf("warning-id")
  }
}
