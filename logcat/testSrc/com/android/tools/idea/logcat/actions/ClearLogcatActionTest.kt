/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ClearLogcatAction]
 */
class ClearLogcatActionTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val fakeLogcatPresenter = FakeLogcatPresenter()
  private val event by lazy(TestActionEvent::createTestEvent)

  @Test
  fun presentation() {
    val action = newClearLogcatAction()

    assertThat(action.templatePresentation.text).isEqualTo("Clear Logcat")
    assertThat(action.templatePresentation.icon).isSameAs(AllIcons.Actions.GC)
  }

  @Test
  fun update_notEmptyAndAttached() {
    fakeLogcatPresenter.attachedDevice = mock()
    fakeLogcatPresenter.appendMessage("not-empty")
    val action = newClearLogcatAction(fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun update_notEmptyAndNotAttached() {
    fakeLogcatPresenter.attachedDevice = null
    fakeLogcatPresenter.appendMessage("not-empty")
    val action = newClearLogcatAction(fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_isEmptyAndAttached() {
    fakeLogcatPresenter.attachedDevice = mock()
    fakeLogcatPresenter.appendMessage("not-empty")
    fakeLogcatPresenter.clearMessageView()
    val action = newClearLogcatAction(fakeLogcatPresenter)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun actionPerformed() {
    fakeLogcatPresenter.appendMessage("message")
    val action = newClearLogcatAction(fakeLogcatPresenter)

    action.actionPerformed(event)

    assertThat(fakeLogcatPresenter.isLogcatEmpty()).isTrue()
  }

  private fun newClearLogcatAction(logcatPresenter: LogcatPresenter = fakeLogcatPresenter) = ClearLogcatAction(logcatPresenter)
}