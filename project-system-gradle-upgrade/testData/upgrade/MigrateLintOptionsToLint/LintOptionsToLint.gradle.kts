android {
  lintOptions {
    isAbortOnError = true
    check("check-id-1")
    error("error-id-1")
    informational("informational-id-1", "informational-id-2")
    isNoLines = false
    xmlOutput = file("xmlOutput.xml")
  }
}
