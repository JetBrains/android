/*
 * Copyright (C) 2025 The Android Open Source Project
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class ShellCommandUiDumpProviderTest {

  private val projectRule = ProjectRule()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdbSessionRule)

  private val deviceServices = fakeAdbSessionRule.adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private var uiDumpProvider = ShellCommandUiDumpProvider()
  private val project
    get() = projectRule.project

  companion object {
    private const val UI_DUMP_OUTPUT = "<node>THINGS</node>"
  }

  @Before
  fun setUp() {
    deviceServices.configureShellCommand(device, COMMANDLINE, UI_DUMP_OUTPUT)
  }

  @Test
  fun simple() {
    val dump = runBlockingWithTimeout { uiDumpProvider.uiDump(project, serialNumber) }
    assertEquals(UI_DUMP_OUTPUT, dump)
  }
}