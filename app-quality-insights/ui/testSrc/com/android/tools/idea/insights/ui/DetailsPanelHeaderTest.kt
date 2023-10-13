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
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class DetailsPanelHeaderTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun `header updates with issue`() {
    val detailsPanelHeader = DetailsPanelHeader()

    detailsPanelHeader.size = Dimension(500, 200)

    detailsPanelHeader.updateWithIssue(ISSUE1.issueDetails)

    assertThat(detailsPanelHeader.titleLabel.text).isEqualTo("<html>crash.<B>Crash</B></html>")
    assertThat(detailsPanelHeader.eventsCountLabel.text).isEqualTo("50,000,000")
    assertThat(detailsPanelHeader.usersCountLabel.text).isEqualTo("3,000")

    detailsPanelHeader.clear()

    assertThat(detailsPanelHeader.titleLabel.text).isNull()
    assertThat(detailsPanelHeader.titleLabel.icon).isNull()
  }

  @Test
  fun `header is shown with bottom border`() {
    val detailsPanelHeader = DetailsPanelHeader()

    assertThat(detailsPanelHeader.border.getBorderInsets(detailsPanelHeader).bottom).isEqualTo(1)
  }

  @Ignore("b/303112785")
  @Test
  fun `header width affects class name and method name in title label`() {
    val detailsPanelHeader = DetailsPanelHeader()

    detailsPanelHeader.size = Dimension(300, 200)
    assertThat(detailsPanelHeader.generateTitleLabelText("DetailsPanelTest", "testMethod"))
      .isEqualTo("<html><B>...ethod</B></html>")

    detailsPanelHeader.size = Dimension(350, 200)
    assertThat(detailsPanelHeader.generateTitleLabelText("DetailsPanelTest", "testMethod"))
      .isEqualTo("<html>...st.<B>testMethod</B></html>")
  }

  @Test
  fun `header should not show variants if variants not supported`() {
    val detailsPanelHeader = DetailsPanelHeader()
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()

    detailsPanelHeader.updateWithIssue(ISSUE1.issueDetails)
    assertThat(detailsPanelHeader.variantPanel.isVisible).isFalse()
  }
}
