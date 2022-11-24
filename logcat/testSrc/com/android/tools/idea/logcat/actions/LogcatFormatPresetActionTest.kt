/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [LogcatFormatPresetAction]
 */
class LogcatFormatPresetActionTest {
  @get:Rule
  val rule = RuleChain(ProjectRule())

  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @Test
  fun presentation_standard() {
    val presentation = LogcatFormatPresetAction.Standard(fakeLogcatPresenter).templatePresentation

    assertThat(presentation.text).isEqualTo("Standard View")
    assertThat(presentation.description).isNull()
    assertThat(presentation.icon).isNull()
  }

  @Test
  fun presentation_compact() {
    val presentation = LogcatFormatPresetAction.Compact(fakeLogcatPresenter).templatePresentation

    assertThat(presentation.text).isEqualTo("Compact View")
    assertThat(presentation.description).isNull()
    assertThat(presentation.icon).isNull()
  }

  @Test
  fun isSelected_standard() {
    fakeLogcatPresenter.formattingOptions = STANDARD.formattingOptions
    val action = LogcatFormatPresetAction.Standard(fakeLogcatPresenter)

    assertThat(action.isSelected()).isTrue()
  }

  @Test
  fun isSelected_standard_compact() {
    fakeLogcatPresenter.formattingOptions = COMPACT.formattingOptions
    val action = LogcatFormatPresetAction.Standard(fakeLogcatPresenter)

    assertThat(action.isSelected()).isFalse()
  }

  @Test
  fun isSelected_compact() {
    fakeLogcatPresenter.formattingOptions = COMPACT.formattingOptions
    val action = LogcatFormatPresetAction.Compact(fakeLogcatPresenter)

    assertThat(action.isSelected()).isTrue()
  }

  @Test
  fun isSelected_compact_standard() {
    fakeLogcatPresenter.formattingOptions = STANDARD.formattingOptions
    val action = LogcatFormatPresetAction.Compact(fakeLogcatPresenter)

    assertThat(action.isSelected()).isFalse()
  }

  @Test
  fun actionPerformed_standard() {
    val action = LogcatFormatPresetAction.Standard(fakeLogcatPresenter)

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeLogcatPresenter.formattingOptions).isEqualTo(STANDARD.formattingOptions)
  }

  @Test
  fun actionPerformed_compact() {
    val action = LogcatFormatPresetAction.Compact(fakeLogcatPresenter)

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeLogcatPresenter.formattingOptions).isEqualTo(COMPACT.formattingOptions)
  }
}