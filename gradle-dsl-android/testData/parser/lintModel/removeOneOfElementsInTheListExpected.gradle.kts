android {
  lint {
    checkOnly += setOf("checkOnly-id-2")
    disable += setOf("disable-id-1")
    enable += setOf("enable-id-2")
    error += setOf("error-id-1")
    fatal += setOf("fatal-id-2")
    ignore += setOf("ignore-id-1")
    informational += setOf("informational-id-2")
    warning += setOf("warning-id-2")
  }
}
