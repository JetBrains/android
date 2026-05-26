/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.insights.InsightsProvider.Source
import com.android.tools.idea.insights.inspection.AppInsightsFilterSelector
import com.android.tools.idea.insights.inspection.ConnectionFilter
import com.android.tools.idea.insights.inspection.ConnectionPreference
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.replaceService
import java.awt.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FilterSelectorActionTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `action updates title when selection changes`() {
    runBlocking {
      val filtersFlow = MutableStateFlow<List<ConnectionFilter>>(emptyList())
      val mockSelector = mock(AppInsightsFilterSelector::class.java)
      `when`(mockSelector.filters).thenReturn(filtersFlow)
      `when`(mockSelector.scope).thenReturn(mock(CoroutineScope::class.java))
      projectRule.project.replaceService(AppInsightsFilterSelector::class.java, mockSelector, projectRule.disposable)

      val action = FilterSelectorAction(projectRule.project, mockSelector.scope) { Point() }

      val event = createTestEvent()
      action.update(event)
      assertThat(event.presentation.text).isEqualTo("No apps available")

      val filter1 = ConnectionFilter("app1", "app1", mapOf(Source.FIREBASE to ConnectionPreference.PREFERRED))
      filtersFlow.value = listOf(filter1)

      action.update(event)
      assertThat(event.presentation.text).isEqualTo("app1 [app1]")
    }
  }
}
