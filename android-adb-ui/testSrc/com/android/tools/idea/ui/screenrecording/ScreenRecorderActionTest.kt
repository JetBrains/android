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
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [ScreenRecorderAction]. */
// TODO(b/235094713): Add tests for action execution. Only updating is currently tested.
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
    userData[ScreenRecordingParameters.DATA_KEY.name] =
        ScreenRecordingParameters("device", "My device", 30, testRootDisposable, null)
  }

  @Test
  fun update_noSerial_disabled() {
    userData[ScreenRecordingParameters.DATA_KEY.name] = null
    val event = TestActionEvent.createTestEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesNotSupportScreenRecording_disabled() = runTest {
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported(any())).thenReturn(false)
    val event = TestActionEvent.createTestEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_deviceDoesSupportScreenRecording_enabled() = runTest {
    whenever(mockScreenRecordingSupportedCache.isScreenRecordingSupported("device")).thenReturn(true)
    val event = TestActionEvent.createTestEvent { userData[it] }

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }
}