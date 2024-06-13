/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.templates.diff

import com.google.common.io.Files
import java.io.File
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Path

class LintReportParser(private val out: PrintStream) {
  private val endOfReportRegex = Regex("(\\d+) errors?, \\d+ warnings?.*")
  private val issueRegex = Regex(".*: Error:.*\\[(.*?)]")

  fun parseLintReportInProject(projectDir: Path): Int {
    // We could parse the exact path from the lint task's build error output, but the path is only
    // displayed when there isn't a baseline file. Otherwise, the build is aborted because a new
    // baseline file is generated.
    val lintDebugDir =
      projectDir.resolve(
        "Template test module/build/intermediates/lint_intermediate_text_report/debug"
      )
    var reportPath = lintDebugDir.resolve("lintReportDebug/lint-results-debug.txt")
    if (!reportPath.toFile().exists()) {
      // The lint output structure may have changed. This seems to be the older structure
      reportPath = lintDebugDir.resolve("lint-results-debug.txt")
    }

    out.println("Parsing lint report at: $reportPath")

    return parseLintReport(projectDir, reportPath)
  }

  internal fun parseLintReport(projectDir: Path, reportPath: Path): Int {
    // The collected results, keyed by the issue type, e.g. FragmentTagUsage. The values are a list
    // of the full line from the report, which includes the line number of the issue
    val issues = mutableMapOf<String, MutableList<String>>()
    val lines = Files.readLines(reportPath.toFile(), Charset.defaultCharset())

    lines.forEach { line ->
      if (line == "No issues found.") {
        out.println(line)
        return 0
      }
      // At the end of the lint report, there is a line that says X errors, Y warnings. Use this as
      // the final tally to make sure we parsed the right number of issues, and also to print out
      // our
      // summary.
      val endOfReportMatch = endOfReportRegex.matchEntire(line)
      if (endOfReportMatch != null) {
        printIssues(issues, projectDir)
        out.println()

        val numErrorsReported = Integer.parseInt(endOfReportMatch.groups[1]!!.value)
        var numMatchedIssues = 0
        issues.values.forEach { numMatchedIssues += it.size }
        if (numErrorsReported != numMatchedIssues) {
          out.println(
            "WARNING: End of report summary doesn't match number of errors found ($numMatchedIssues). A problem with the regex?"
          )
        }
        out.println(line)

        return numMatchedIssues
      }

      // We only care about the lines that say the issue message and line number. Other lines are
      // just
      // explanations for the issues.
      val issueMatch = issueRegex.matchEntire(line)
      if (issueMatch != null) {
        val issueType = issueMatch.groups[1]!!.value
        issues.putIfAbsent(issueType, mutableListOf())
        issues[issueType]!!.add(line)
      }
    }

    throw RuntimeException("End of report summary not found! A problem with the regex?")
  }

  /** Prints out all the issues in a more readable summary */
  private fun printIssues(issues: Map<String, List<String>>, projectDir: Path) {
    val basePath = projectDir.toString().replace("/", File.separator)
    issues.forEach { (issueType, issueList) ->
      out.println("${issueList.size} $issueType issues:")
      issueList.forEach {
        // Replace the project path because it's long and the same for all issues
        val shortenedIssue = it.replace(basePath, "[...]", ignoreCase = false)
        out.println("    $shortenedIssue")
      }
    }
  }
}
