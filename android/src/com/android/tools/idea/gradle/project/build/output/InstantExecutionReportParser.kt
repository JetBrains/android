/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.util.function.Consumer

/**
 * Parser for Gradle Instant execution errors. It will look for the report file and if found
 * generate a new event that has a link to it.
 */
class InstantExecutionReportParser: BuildOutputParser {
  companion object {
    private const val REPORT_PREFIX = "See the complete report at "
    private const val REPORT_SUFFIX = "instant-execution-report.html"
    private const val INSTANT_EXECUTION_GROUP = "Gradle Instant Execution"
  }

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    if (line == null || reader == null || messageConsumer == null){
      return false
    }

    val messageStart = line.indexOfFirstNonWhitespace()
    if (!line.startsWith(REPORT_PREFIX, messageStart)) {
      return false
    }

    val urlStart = messageStart + REPORT_PREFIX.length
    val reportSuffixIndex = line.indexOf(REPORT_SUFFIX, urlStart)
    if (reportSuffixIndex <0) {
      return false
    }
    // All remaining characters must be white space
    val urlEnd = reportSuffixIndex + REPORT_SUFFIX.length
    for (i in urlEnd until line.length) {
      if (!line[i].isWhitespace()) {
        return false
      }
    }

    val reportUrl = line.substring(urlStart, urlEnd)
    val buildIssueComposer = BuildIssueComposer("Instant execution problems found in this build.", INSTANT_EXECUTION_GROUP)
    buildIssueComposer.addQuickFix(REPORT_PREFIX, reportUrl, "", OpenLinkQuickFix(reportUrl))
    messageConsumer.accept(BuildIssueEventImpl(reader.parentEventId, buildIssueComposer.composeBuildIssue(), MessageEvent.Kind.WARNING))
    return true
  }
}

/**
 * Returns the position of the first non white space character starting at startPosition
 * or -1 if all characters are whitespaces
 */
fun String.indexOfFirstNonWhitespace(startPosition: Int = 0): Int {
  var result = startPosition
  while (result < this.length) {
    if (!this[result].isWhitespace()) {
      return result
    }
    result++
  }
  return -1
}