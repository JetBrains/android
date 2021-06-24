android {
  lint {
    abortOnError = true
    absolutePaths = false
    baselineFile = file("baseline.xml")
    checkOnly += setOf("checkOnly-id-1", "checkOnly-id-2")
    checkAllWarnings = true
    checkDependencies = false
    checkGeneratedSources = true
    checkReleaseBuilds = false
    checkTestSources = true
    disable += setOf("disable-id-1", "disable-id-2")
    enable += setOf("enable-id-1", "enable-id-2")
    error += setOf("error-id-1", "error-id-2")
    explainIssues = true
    fatal += setOf("fatal-id-1", "fatal-id-2")
    htmlOutput = file("html.output")
    htmlReport = false
    ignore += setOf("ignore-id-1", "ignore-id-2")
    ignoreTestSources = false
    ignoreWarnings = true
    informational += setOf("informational-id-1", "informational-id-2")
    lintConfig = file("lint.config")
    noLines = false
    quiet = true
    showAll = false
    textOutput = file("text.output")
    textReport = true
    warning += setOf("warning-id-1", "warning-id-2")
    warningsAsErrors = false
    xmlOutput = file("xml.output")
    xmlReport = true
  }
}
