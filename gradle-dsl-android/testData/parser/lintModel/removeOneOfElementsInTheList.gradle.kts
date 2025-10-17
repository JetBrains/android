android {
  lint {
    checkOnly += setOf("checkOnly-id-1", "checkOnly-id-2")
    disable += setOf("disable-id-1", "disable-id-2")
    enable += setOf("enable-id-1", "enable-id-2")
    error += setOf("error-id-1", "error-id-2")
    fatal += setOf("fatal-id-1", "fatal-id-2")
    ignore += setOf("ignore-id-1", "ignore-id-2")
    informational += setOf("informational-id-1", "informational-id-2")
    warning += setOf("warning-id-1", "warning-id-2")
  }
}
