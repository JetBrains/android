/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType.PLAY_SDK_INDEX_ISSUE
import com.android.tools.idea.gradle.structure.model.PsPath
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IssuesBySeverityPathTextComparatorTest {
  private val errorIssue = generateIssue(severity = PsIssue.Severity.ERROR)
  private val warningIssue = generateIssue(severity = PsIssue.Severity.WARNING)
  private val updateIssue = generateIssue(severity = PsIssue.Severity.UPDATE)
  private val infoIssue = generateIssue(severity = PsIssue.Severity.INFO)
  private val pathIssue = generateIssue(path = "_path")
  private val highPriorityIssue = generateIssue(priority = PsIssue.Priority.HIGH_PRIORITY)
  private val textIssue = generateIssue(text = "_text")

  private class TestPsPath(val path: String): PsPath {
    override fun getHyperlinkDestination(context: PsContext): String? {
      return null
    }

    override fun compareTo(other: PsPath): Int {
      if (other is TestPsPath) {
        return path.compareTo(other.path)
      }
      return super.compareTo(other)
    }
  }

  @Test
  fun `verify compare is by severity, path, priority and text`() {
    val expectedOrder = listOf(
      errorIssue,
      pathIssue,
      highPriorityIssue,
      textIssue,
      warningIssue,
      updateIssue,
      infoIssue,
    )
    val comparator = IssuesBySeverityPathTextComparator.INSTANCE
    val failures = mutableListOf<String>()
    for ((index1, issue1) in expectedOrder.withIndex()) {
      for ((index2, issue2) in expectedOrder.withIndex()) {
        val compExpected = sign(index1 - index2)
        val compResult = sign(comparator.compare(issue1, issue2))
        if (compResult != compExpected) {
          failures.add("The expected result when comparing ${issueToText(issue1)} and ${issueToText(issue2)} was $compExpected, but was $compResult")
        }
      }
    }
    assertThat(failures).isEmpty()
  }

  private fun generateIssue(severity: PsIssue.Severity = PsIssue.Severity.WARNING, path: String = "path", priority: PsIssue.Priority = PsIssue.Priority.NORMAL_PRIORITY, text: String = "text"): PsGeneralIssue {
    return PsGeneralIssue(text, description = "description", TestPsPath(path), PLAY_SDK_INDEX_ISSUE, severity, priority = priority )
  }

  private fun sign(value: Int) =
    if (value < 0) -1
    else if (value > 0) 1
    else 0

  private fun issueToText(issue: PsIssue) = "[${issue.severity}, ${(issue.path as TestPsPath).path}, ${issue.priority}, ${issue.text}]"
}