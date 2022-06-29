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
package com.android.tools.idea.ui.screenrecording

import com.android.adblib.testing.FakeAdbLibSession
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.registerServiceInstance
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.AVD_NAME_KEY
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.SDK_KEY
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.SERIAL_NUMBER_KEY
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyInt

/**
 * Tests for [ScreenRecorderAction]
 *
 * Based on com.android.tools.idea.ddms.actions.ScreenRecorderActionTest.
 * We only include tests that exist in the above file, so we don't lose coverage. Tests of getTemporaryVideoPathForVirtualDevice() are not
 * included because the new impl of this function is simpler and doesn't require these tests.
 *
 * The getEmulatorScreenRecorderOptions() has been moved to EmulatorConsoleRecordingProviderTest
 */
@Suppress("OPT_IN_USAGE") // runBlockingTest is experimental
class ScreenRecorderActionTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule)

  private val project get() = projectRule.project
  private val userData = mutableMapOf<String, Any?>()
  private val mockScreenRecordingSupportedCache = mock<ScreenRecordingSupportedCache>()

  @Before
  fun setUp() {
    project.registerServiceInstance(ScreenRecordingSupportedCache::class.java, mockScreenRecordingSupportedCache, project)

    userData[SERIAL_NUMBER_KEY.name] = "device"
    userData[SDK_KEY.name] = 30
  }

  @Test
  fun update_noSerial_disabled() {
    userData[SERIAL_NUMBER_KEY.name] = null
    val event = TestActionEvent { userData[it] }
    val action = ScreenRecorderAction(project, project, FakeAdbLibSession())

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesNotSupportScreenRecording_disabled() = runBlockingTest {
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported(any(), anyInt())).thenReturn(false)
    userData[SERIAL_NUMBER_KEY.name] = "device"
    val event = TestActionEvent { userData[it] }
    val action = ScreenRecorderAction(project, project, FakeAdbLibSession(), coroutineContext = coroutineContext)

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesSupportScreenRecording_enabled() = runBlockingTest {
    userData[SERIAL_NUMBER_KEY.name] = "device"
    userData[SDK_KEY.name] = 30
    userData[AVD_NAME_KEY.name] = null
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported("device", 30)).thenReturn(true)
    val event = TestActionEvent { userData[it] }
    val action = ScreenRecorderAction(project, project, FakeAdbLibSession(), coroutineContext = coroutineContext)

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }
}