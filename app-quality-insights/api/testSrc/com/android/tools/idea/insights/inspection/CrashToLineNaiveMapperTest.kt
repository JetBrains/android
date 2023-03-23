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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueInFrame
import com.android.tools.idea.insights.analysis.Cause
import com.android.tools.idea.insights.analysis.CrashFrame
import com.google.common.truth.Truth
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class CrashToLineNaiveMapperTest {

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val edtRule = EdtRule()

  @Test
  fun `mapper on javaKotlinApp project finds expected matches`() {
    val file =
      PsiFileFactory.getInstance(projectRule.project)
        .createFileFromText(KotlinLanguage.INSTANCE, "\n".repeat(10))

    val issuesInFrame =
      listOf(
        buildIssueInFrameAtLine(10),
        buildIssueInFrameAtLine(1),
        buildIssueInFrameAtLine(123456),
        buildIssueInFrameAtLine(-9),
        buildIssueInFrameAtLine(0)
      )
    val crashToLineMapper = CrashToLineNaiveMapper({ issuesInFrame }, { true })

    val issues = crashToLineMapper.retrieve(file)
    Truth.assertThat(issues).hasSize(2)
    Truth.assertThat(issues[0].line).isEqualTo(9) // lines are offset by -1
  }

  private fun buildIssueInFrameAtLine(line: Long) =
    IssueInFrame(
      CrashFrame(Frame(line), Cause.Throwable("")),
      AppInsightsIssue(
        IssueDetails(
          IssueId("id"),
          "title",
          "subtitle",
          FailureType.FATAL,
          "sampleEvent",
          "firstSeenVersion",
          "lastSeenVersion",
          1000L,
          9999L,
          emptySet(),
          "https://console.firebas.com/0/fake/uri",
          0
        ),
        Event()
      )
    )
}
