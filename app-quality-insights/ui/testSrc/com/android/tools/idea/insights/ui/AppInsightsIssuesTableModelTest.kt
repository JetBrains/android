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

import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppInsightsIssuesTableModelTest {

  @Test
  fun `model creates correct column infos`() {
    val model = AppInsightsIssuesTableModel(AppInsightsIssuesTableCellRenderer)

    assertThat(model.columnCount).isEqualTo(3)

    with(model.columnInfos[0]) {
      assertThat(valueOf(ISSUE1)).isEqualTo(ISSUE1)
      assertThat(comparator!!.compare(ISSUE1, ISSUE2)).isEqualTo(0)
      assertThat(getRenderer(ISSUE1)).isEqualTo(AppInsightsIssuesTableCellRenderer)
    }

    with(model.columnInfos[1]) {
      assertThat(valueOf(ISSUE1)).isEqualTo("50,000,000")
      assertThat(comparator!!.compare(ISSUE1, ISSUE2)).isEqualTo(1)
    }

    with(model.columnInfos[2]) {
      assertThat(valueOf(ISSUE1)).isEqualTo("3,000")
      assertThat(comparator!!.compare(ISSUE1, ISSUE2)).isEqualTo(1)
    }
  }

  @Test
  fun `sorts issues column by display title`() {
    val model = AppInsightsIssuesTableModel(AppInsightsIssuesTableCellRenderer)

    val crash1 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "stack.Crash1"))
    val crash2 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "Stack.Crash2"))
    val crash3 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "Stack.Crash1"))
    val crash4 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "Stack.Crash3"))
    val crash5 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "A.A.Stack.Crash4"))
    val crash6 = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(subtitle = "Z.Z.Stack.Crash0"))
    val crash7 =
      ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(title = "stack", subtitle = "illegal"))

    val crashes = listOf(crash1, crash2, crash3, crash4, crash5, crash6, crash7)

    // The display title is the last 2 dot separated words in the subtitle, or if there are less
    // than 2 words, the title of the issue
    assertThat(crashes.sortedWith(model.columnInfos[0].comparator!!))
      .containsExactly(crash6, crash3, crash2, crash4, crash5, crash7, crash1)
      .inOrder()
  }
}
