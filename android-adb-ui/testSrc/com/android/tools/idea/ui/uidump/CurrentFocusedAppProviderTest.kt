/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.ui.uidump

import com.android.adblib.DeviceSelector
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.tools.idea.adblib.testing.FakeAdbSessionRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class CurrentFocusedAppProviderTest {

  private val projectRule = ProjectRule()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)

  @get:Rule val rule = RuleChain(projectRule, fakeAdbSessionRule)

  private val deviceServices
    get() = fakeAdbSessionRule.adbSession.deviceServices

  private val serialNumber = "123"
  private val device: DeviceSelector = DeviceSelector.fromSerialNumber(serialNumber)
  private var provider = CurrentFocusedAppProvider()
  private val project
    get() = projectRule.project

  @Test
  fun canGetFocusedApp() {
    val shellOutput =
      """
      mCurrentFocus=Window{1234 u0 com.example.app/com.example.app.MainActivity}
      some other line
      mFocusedApp=ActivityRecord{1234 u0 com.example.app/com.example.app.MainActivity t17}
      some other line
      """
        .trimIndent()

    deviceServices.configureShellCommand(device, FOCUSED_APP_COMMAND, shellOutput)
    val result = runBlockingWithTimeout { provider.focusedApp(project, serialNumber) }
    val expected =
      """
      mCurrentFocus=Window{1234 u0 com.example.app/com.example.app.MainActivity}
      mFocusedApp=ActivityRecord{1234 u0 com.example.app/com.example.app.MainActivity t17}
      """
        .trimIndent()
    assertEquals(expected, result)
  }

  @Test
  fun handlesCommandFailure() {
    deviceServices.configureShellCommand(device, FOCUSED_APP_COMMAND, "", "error message", 1)
    val result = runBlockingWithTimeout { provider.focusedApp(project, serialNumber) }
    assertEquals("dumpsys window failed with exit code 1. error message", result)
  }
}
