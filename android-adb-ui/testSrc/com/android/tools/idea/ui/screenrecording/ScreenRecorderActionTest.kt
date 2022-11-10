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

import com.android.adblib.testing.FakeAdbSession
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.Companion.SCREEN_RECORDER_PARAMETERS_KEY
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
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

  private val mockScreenRecordingSupportedCache = mock<ScreenRecordingSupportedCache>()

  @get:Rule
  val rule = RuleChain(
    projectRule,
    ProjectServiceRule(projectRule, AdbLibService::class.java, TestAdbLibService(FakeAdbSession())),
    ProjectServiceRule(projectRule, ScreenRecordingSupportedCache::class.java, mockScreenRecordingSupportedCache),
  )

  private val project get() = projectRule.project

  @Suppress("UnstableApiUsage")
  private val testRootDisposable get() = project.earlyDisposable
  private val userData = mutableMapOf<String, Any?>()
  private val action = ScreenRecorderAction()

  @Before
  fun setUp() {
    userData[CommonDataKeys.PROJECT.name] = project
    userData[SCREEN_RECORDER_PARAMETERS_KEY.name] = ScreenRecorderAction.Parameters("device", 30, null, testRootDisposable)
  }

  @Test
  fun update_noSerial_disabled() {
    userData[SCREEN_RECORDER_PARAMETERS_KEY.name] = null
    val event = TestActionEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesNotSupportScreenRecording_disabled() = runBlockingTest {
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported(any(), anyInt())).thenReturn(false)
    val event = TestActionEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesSupportScreenRecording_enabled() = runBlockingTest {
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported("device", 30)).thenReturn(true)
    val event = TestActionEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }
}