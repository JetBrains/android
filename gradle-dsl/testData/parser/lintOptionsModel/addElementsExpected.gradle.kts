android {
  lintOptions {
    isAbortOnError = true
    isAbsolutePaths = false
    baselineFile = file("baseline.xml")
    check("check-id-1")
    isCheckAllWarnings = true
    isCheckDependencies = false
    isCheckGeneratedSources = true
    isCheckReleaseBuilds = false
    isCheckTestSources = true
    disable("disable-id-1")
    enable("enable-id-1")
    error("error-id-1")
    isExplainIssues = true
    fatal("fatal-id-1")
    htmlOutput = file("html.output")
    htmlReport = false
    ignore("ignore-id-1")
    isIgnoreTestSources = false
    isIgnoreWarnings = true
    informational("informational-id-1")
    lintConfig = file("lint.config")
    isNoLines = false
    isQuiet = true
    isShowAll = false
    textOutput = file("text.output")
    textReport = true
    warning("warning-id-1")
    isWarningsAsErrors = false
    xmlOutput = file("xml.output")
    xmlReport = true
  }
}
