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
package com.android.tools.idea.insights.ui

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.table.JBTable
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.table.TableColumn
import org.junit.Test

class AppInsightsIssuesTableCellRendererTest {

  @Test
  fun testIcons() {
    val table = JBTable()
    val detailsTemplate =
      IssueDetails(
        IssueId("1"),
        "Issue1",
        "com.google.crash.Crash",
        FailureType.FATAL,
        "Sample Event",
        "1.2.3",
        "1.2.3",
        5L,
        10L,
        emptySet(),
        "https://url.for-crash.com",
        0
      )

    table.columnModel.addColumn(TableColumn(0).apply { width = 200 })
    fun getIcons(issue: AppInsightsIssue): Collection<Icon> {
      val renderer =
        AppInsightsIssuesTableCellRenderer.getTableCellRendererComponent(
          table,
          issue,
          false,
          false,
          0,
          0
        )
      return renderer.flatten().mapNotNull {
        when (it) {
          is JLabel -> it.icon
          is SimpleColoredComponent -> it.icon
          else -> null
        }
      }
    }
    assertThat(getIcons(AppInsightsIssue(detailsTemplate, mock())))
      .containsExactly(StudioIcons.AppQualityInsights.FATAL)

    assertThat(
        getIcons(AppInsightsIssue(detailsTemplate.copy(fatality = FailureType.NON_FATAL), mock()))
      )
      .containsExactly(StudioIcons.AppQualityInsights.NON_FATAL)

    assertThat(getIcons(AppInsightsIssue(detailsTemplate.copy(notesCount = 4), mock())))
      .containsExactly(StudioIcons.AppQualityInsights.FATAL_WITH_NOTE)

    assertThat(
        getIcons(
          AppInsightsIssue(
            detailsTemplate.copy(
              signals = setOf(SignalType.SIGNAL_FRESH, SignalType.SIGNAL_REGRESSED)
            ),
            mock()
          )
        )
      )
      .containsExactly(
        StudioIcons.AppQualityInsights.FATAL,
        StudioIcons.AppQualityInsights.REGRESSED_SIGNAL,
        StudioIcons.AppQualityInsights.FRESH_SIGNAL
      )
  }
}
