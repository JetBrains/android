android {
  lint {
    abortOnError = true
    checkOnly += setOf("check-id-1")
    error += setOf("error-id-1")
    informational += setOf("informational-id-1", "informational-id-2")
    noLines = false
    xmlOutput = file("xmlOutput.xml")
  }
}
