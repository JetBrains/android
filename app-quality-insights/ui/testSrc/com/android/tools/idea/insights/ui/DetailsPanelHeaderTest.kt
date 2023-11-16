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
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class DetailsPanelHeaderTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun `header updates with issue`() {
    val detailsPanelHeader = DetailsPanelHeader(mock(Editor::class.java))

    detailsPanelHeader.updateWithIssue(ISSUE1)

    assertThat(detailsPanelHeader.titleLabel.toString()).isEqualTo("crash.Crash")
    assertThat(detailsPanelHeader.toolbar.component.isVisible).isTrue()

    detailsPanelHeader.updateWithIssue(null)

    assertThat(detailsPanelHeader.toolbar.component.isVisible).isFalse()
    assertThat(detailsPanelHeader.titleLabel.toString()).isEmpty()
  }

  @Test
  fun `header is shown with bottom border`() {
    val detailsPanelHeader = DetailsPanelHeader(mock(Editor::class.java))

    assertThat(detailsPanelHeader.border.getBorderInsets(detailsPanelHeader).bottom).isEqualTo(1)
  }
}
