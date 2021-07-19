android {
  lint {
    abortOnError = true
    absolutePaths = false
    baselineFile = file("baseline.xml")
    checkOnly += setOf("checkOnly-id-1")
    checkAllWarnings = true
    checkDependencies = false
    checkGeneratedSources = true
    checkReleaseBuilds = false
    checkTestSources = true
    disable += setOf("disable-id-1")
    enable += setOf("enable-id-1")
    error += setOf("error-id-1")
    explainIssues = true
    fatal += setOf("fatal-id-1")
    htmlOutput = file("html.output")
    htmlReport = false
    ignore += setOf("ignore-id-1")
    ignoreTestSources = false
    ignoreWarnings = true
    informational += setOf("informational-id-1")
    lintConfig = file("lint.config")
    noLines = false
    quiet = true
    sarifOutput = file("sarif.output")
    sarifReport = true
    showAll = false
    textOutput = file("text.output")
    textReport = true
    warning += setOf("warning-id-1")
    warningsAsErrors = false
    xmlOutput = file("xml.output")
    xmlReport = true
  }
}
