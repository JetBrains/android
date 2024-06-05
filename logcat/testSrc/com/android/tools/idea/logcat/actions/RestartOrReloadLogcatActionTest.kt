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

import com.android.testutils.waitForCondition
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter.Companion.LOGCAT_PRESENTER_ACTION
import com.android.tools.idea.logcat.devices.Device
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Tests for [RestartOrReloadLogcatAction] */
class RestartOrReloadLogcatActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val device = Device.createPhysical("device", false, "11", 30, "Google", "Pixel 2")

  private val fakeLogcatPresenter = FakeLogcatPresenter()

  @After
  fun tearDown() {
    Disposer.dispose(fakeLogcatPresenter)
  }

  @Test
  fun update_withoutConnectedDevice_disabled() {
    fakeLogcatPresenter.attachedDevice = null
    val event = testEvent(fakeLogcatPresenter)
    val action = RestartOrReloadLogcatAction()

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_withConnectedDevice_enabled() {
    fakeLogcatPresenter.attachedDevice = device
    fakeLogcatPresenter.device = device
    val event = testEvent(fakeLogcatPresenter)
    val action = RestartOrReloadLogcatAction()

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed_callsRestartLogcat() {
    fakeLogcatPresenter.device = device
    val event = testEvent(fakeLogcatPresenter)
    val action = RestartOrReloadLogcatAction()

    action.actionPerformed(event)

    // Use waitForCondition rather than assertThat because the action launches a coroutine. Timeout
    // represents failure.
    waitForCondition(1, SECONDS) { fakeLogcatPresenter.logcatRestartedCount == 1 }
  }
}

private fun testEvent(logcatPresenter: LogcatPresenter) =
  TestActionEvent.createTestEvent(
    SimpleDataContext.builder().add(LOGCAT_PRESENTER_ACTION, logcatPresenter).build()
  )
