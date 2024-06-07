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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.devices.Device
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

/** Tests for [PauseLogcatAction] */
class PauseLogcatActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val device = Device.createPhysical("device", true, "10", 30, "Google", "Pixel")
  private val mockLogcatPresenter = mock<LogcatPresenter>()

  @Test
  fun update_text_isNotPaused() {
    whenever(mockLogcatPresenter.isLogcatPaused()).thenReturn(false)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.update(event)

    assertThat(event.presentation.text).isEqualTo("Pause Logcat")
  }

  @Test
  fun update_text_isPaused() {
    whenever(mockLogcatPresenter.isLogcatPaused()).thenReturn(true)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.update(event)

    assertThat(event.presentation.text).isEqualTo("Resume Logcat")
  }

  @Test
  fun update_connected_enabled() {
    whenever(mockLogcatPresenter.getConnectedDevice()).thenReturn(device)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun update_notConnected_disabled() {
    whenever(mockLogcatPresenter.getConnectedDevice()).thenReturn(null)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun actionPerformed_pause() {
    whenever(mockLogcatPresenter.isLogcatPaused()).thenReturn(false)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.actionPerformed(event)

    verify(mockLogcatPresenter).pauseLogcat()
  }

  @Test
  fun actionPerformed_resume() {
    whenever(mockLogcatPresenter.isLogcatPaused()).thenReturn(true)
    val event = testEvent(mockLogcatPresenter)
    val action = PauseLogcatAction()

    action.actionPerformed(event)

    verify(mockLogcatPresenter).resumeLogcat()
  }
}

private fun testEvent(logcatPresenter: LogcatPresenter) =
  TestActionEvent.createTestEvent(
    SimpleDataContext.builder().add(LOGCAT_PRESENTER_ACTION, logcatPresenter).build()
  )
