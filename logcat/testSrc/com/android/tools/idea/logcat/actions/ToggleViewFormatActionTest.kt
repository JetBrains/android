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
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.messages.FormattingOptions
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ToggleViewFormatAction]
 */
class ToggleViewFormatActionTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val dataContext = MapDataContext()
  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @Test
  fun update_noLogcatPresenter_notVisible() {
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_notStandardOrCompact_notVisible() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    fakeLogcatPresenter.formattingOptions = FormattingOptions()
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_standardView_isVisible() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    fakeLogcatPresenter.formattingOptions = STANDARD.formattingOptions
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun update_compactView_isVisible() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    fakeLogcatPresenter.formattingOptions = COMPACT.formattingOptions
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun actionPerformed_notStandardOrCompact_doesNotUpdatePresenter() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    val formattingOptions = FormattingOptions()
    fakeLogcatPresenter.formattingOptions = formattingOptions
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.actionPerformed(event)

    assertThat(fakeLogcatPresenter.formattingOptions).isSameAs(formattingOptions)
  }

  @Test
  fun actionPerformed_standardView_updatesPresenter() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    fakeLogcatPresenter.formattingOptions = STANDARD.formattingOptions
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.actionPerformed(event)

    assertThat(fakeLogcatPresenter.formattingOptions).isSameAs(COMPACT.formattingOptions)
  }

  @Test
  fun actionPerformed_compactView_updatesPresenter() {
    dataContext.put(LOGCAT_PRESENTER_ACTION, fakeLogcatPresenter)
    fakeLogcatPresenter.formattingOptions = COMPACT.formattingOptions
    val event = TestActionEvent.createTestEvent(dataContext)
    val action = ToggleViewFormatAction()

    action.actionPerformed(event)

    assertThat(fakeLogcatPresenter.formattingOptions).isSameAs(STANDARD.formattingOptions)
  }
}
