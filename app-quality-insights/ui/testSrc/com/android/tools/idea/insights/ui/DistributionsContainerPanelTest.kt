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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.insights.FAKE_6_DAYS_AGO
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.TEST_FILTERS
import com.android.tools.idea.insights.Timed
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class DistributionsContainerPanelTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `show proper empty state message upon receiving exceptional state`() = runBlocking {
    val initialState =
      AppInsightsState(
        Selection(CONNECTION1, listOf(CONNECTION1)),
        TEST_FILTERS,
        LoadingState.Ready(Timed(Selection(null, emptyList()), FAKE_6_DAYS_AGO)),
        currentIssueDetails = LoadingState.Loading,
      )
    val flow = MutableSharedFlow<AppInsightsState>()
    val panel =
      DistributionsContainerPanel(
        AndroidCoroutineScope(projectRule.disposable, AndroidDispatchers.uiThread),
        flow,
      )

    withContext(AndroidDispatchers.uiThread) {
      flow.emit(initialState)
      assertThat(panel.emptyText.text).isEqualTo("Loading...")

      flow.emit(
        initialState.copy(currentIssueDetails = LoadingState.NetworkFailure("network failed"))
      )
      assertThat(panel.emptyText.text).isEqualTo("Data not available while offline.")

      flow.emit(
        initialState.copy(
          currentIssueDetails = LoadingState.UnknownFailure("unknown failure occurred")
        )
      )
      assertThat(panel.emptyText.text).isEqualTo("unknown failure occurred")
    }
  }
}
