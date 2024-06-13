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

import com.android.testutils.MockitoKt
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.analysis.Cause
import com.android.tools.idea.insights.ui.AppInsightsGutterRenderer
import com.android.tools.idea.insights.ui.AppInsightsTabProvider
import com.google.common.truth.Truth
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

data class LineToInsights(val line: Int, val insights: List<AppInsight>)

internal fun buildIssue(appVcsInfo: AppVcsInfo): AppInsightsIssue {
  return AppInsightsIssue(
    issueDetails = MockitoKt.mock(),
    sampleEvent = Event(appVcsInfo = appVcsInfo),
  )
}

internal fun buildAppInsight(frame: Frame, issue: AppInsightsIssue): AppInsight {
  return AppInsight(
    line = frame.line.toInt() - 1,
    issue = issue,
    stackFrame = frame,
    cause = MockitoKt.mock<Cause.Frame>(),
    provider = MockitoKt.mock(),
    markAsSelectedCallback = MockitoKt.mock(),
  )
}

internal fun withFakedInsights(
  expectedInsightsFromTabProvider1: List<AppInsight>,
  expectedInsightsFromTabProvider2: List<AppInsight> = emptyList(),
) {
  AppInsightsTabProvider.EP_NAME.extensionList.filterIsInstance<TestTabProvider>().forEachIndexed {
    index,
    tabProvider ->
    if (index == 0) {
      tabProvider.returnInsights(expectedInsightsFromTabProvider1)
    } else {
      tabProvider.returnInsights(expectedInsightsFromTabProvider2)
    }
  }
}

internal fun Document.assertHighlightResults(
  results: List<HighlightInfo>,
  expectedLineToInsights: List<LineToInsights>,
) {
  Truth.assertThat(results.size).isEqualTo(expectedLineToInsights.size)

  results.mapIndexed { index, highlightInfo ->
    Truth.assertThat(highlightInfo.startOffset)
      .isEqualTo(getLineStartOffset(expectedLineToInsights[index].line))
    Truth.assertThat(highlightInfo.severity).isEqualTo(HighlightSeverity.INFORMATION)
    Truth.assertThat((highlightInfo.gutterIconRenderer as AppInsightsGutterRenderer).insights)
      .isEqualTo(expectedLineToInsights[index].insights)
  }
}

internal fun Document.updateFileContentWithoutSaving(text: String, project: Project) {
  ApplicationManager.getApplication().invokeAndWait {
    val runnable = {
      setText(text)
      PsiDocumentManager.getInstance(project).commitDocument(this)
    }

    WriteAction.run<RuntimeException>(runnable)
  }
}
