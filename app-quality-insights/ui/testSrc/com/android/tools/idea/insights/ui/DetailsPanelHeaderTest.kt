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

import com.android.tools.idea.insights.FakeAppInsightsProjectLevelController
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE_VARIANT
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import kotlin.test.assertFailsWith
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class DetailsPanelHeaderTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun `header updates with issue`() {
    val detailsPanelHeader = DetailsPanelHeader(FakeAppInsightsProjectLevelController(), true)

    detailsPanelHeader.size = Dimension(500, 200)

    detailsPanelHeader.updateWithIssue(ISSUE1)

    assertThat(detailsPanelHeader.titleLabel.text).isEqualTo("<html>crash.<B>Crash</B></html>")
    assertThat(detailsPanelHeader.eventsCountLabel.text).isEqualTo("50,000,000")
    assertThat(detailsPanelHeader.usersCountLabel.text).isEqualTo("3,000")

    detailsPanelHeader.clear()

    assertThat(detailsPanelHeader.titleLabel.text).isNull()
    assertThat(detailsPanelHeader.titleLabel.icon).isNull()
  }

  @Test
  fun `header is shown with bottom border`() {
    val detailsPanelHeader = DetailsPanelHeader(FakeAppInsightsProjectLevelController(), true)

    assertThat(detailsPanelHeader.border.getBorderInsets(detailsPanelHeader).bottom).isEqualTo(1)
  }

  @Test
  fun `header passes issue updates to combobox state flow`() {
    val detailsPanelHeader = DetailsPanelHeader(FakeAppInsightsProjectLevelController(), true)
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()

    detailsPanelHeader.updateWithIssue(ISSUE1)
    assertThat(detailsPanelHeader.variantPanel.isVisible).isTrue()
    assertThat(detailsPanelHeader.comboBoxStateFlow.value).isEqualTo(DisabledComboBoxState.loading)

    detailsPanelHeader.updateComboBox(ISSUE1, LoadingState.Ready(Selection.emptySelection()))
    assertThat(detailsPanelHeader.variantPanel.isVisible).isTrue()
    assertThat(detailsPanelHeader.comboBoxStateFlow.value).isEqualTo(DisabledComboBoxState.empty)

    val selection = Selection(null, listOf(ISSUE_VARIANT))
    detailsPanelHeader.updateComboBox(ISSUE1, LoadingState.Ready(selection))
    assertThat(detailsPanelHeader.variantPanel.isVisible).isTrue()
    assertThat(detailsPanelHeader.comboBoxStateFlow.value)
      .isEqualTo(PopulatedComboBoxState(ISSUE1, selection))

    detailsPanelHeader.updateComboBox(ISSUE1, LoadingState.NetworkFailure("failed"))
    assertThat(detailsPanelHeader.variantPanel.isVisible).isTrue()
    assertThat(detailsPanelHeader.comboBoxStateFlow.value).isEqualTo(DisabledComboBoxState.failure)

    detailsPanelHeader.clear()
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()
  }

  @Ignore("b/303112785")
  @Test
  fun `header width affects class name and method name in title label`() {
    val detailsPanelHeader = DetailsPanelHeader(FakeAppInsightsProjectLevelController(), true)

    detailsPanelHeader.size = Dimension(300, 200)
    assertThat(detailsPanelHeader.generateTitleLabelText("DetailsPanelTest", "testMethod"))
      .isEqualTo("<html><B>...ethod</B></html>")

    detailsPanelHeader.size = Dimension(350, 200)
    assertThat(detailsPanelHeader.generateTitleLabelText("DetailsPanelTest", "testMethod"))
      .isEqualTo("<html>...st.<B>testMethod</B></html>")
  }

  @Test
  fun `header should not show variants if variants not supported`() {
    val detailsPanelHeader = DetailsPanelHeader(FakeAppInsightsProjectLevelController(), false)
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()

    detailsPanelHeader.updateWithIssue(ISSUE1)
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()

    assertFailsWith<IllegalArgumentException> {
      detailsPanelHeader.updateComboBox(ISSUE1, LoadingState.Ready(Selection.emptySelection()))
    }
  }
}
