android {
  lint {
    abortOnError = false
    absolutePaths = true
    baselineFile = file("other-baseline.xml")
    checkOnly += setOf("checkOnly-id-1", "checkOnly-id-3")
    checkAllWarnings = false
    checkDependencies = true
    checkGeneratedSources = false
    checkReleaseBuilds = true
    checkTestSources = false
    disable += setOf("disable-id-1", "disable-id-3")
    enable += setOf("enable-id-1", "enable-id-3")
    error += setOf("error-id-3", "error-id-2")
    explainIssues = false
    fatal += setOf("fatal-id-1", "fatal-id-3")
    htmlOutput = file("other-html.output")
    htmlReport = false
    ignore += setOf("ignore-id-1", "ignore-id-3")
    ignoreTestSources = true
    ignoreWarnings = false
    informational += setOf("informational-id-3", "informational-id-2")
    lintConfig = file("other-lint.config")
    noLines = true
    quiet = false
    showAll = true
    textOutput = file("other-text.output")
    textReport = false
    warning += setOf("warning-id-1", "warning-id-3")
    warningsAsErrors = true
    xmlOutput = file("other-xml.output")
    xmlReport = false
  }
}
