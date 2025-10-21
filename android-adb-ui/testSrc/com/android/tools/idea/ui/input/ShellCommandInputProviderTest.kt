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
package com.android.tools.idea.ui.input

import com.android.adblib.DeviceSelector
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.tools.idea.adblib.testing.FakeAdbSessionRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test


internal class ShellCommandInputProviderTest {

  private val projectRule = ProjectRule()
  private val fakeAdbSessionRule = FakeAdbSessionRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdbSessionRule)

  private val deviceServices = fakeAdbSessionRule.adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private var shellInputProvider = ShellCommandInputProvider()
  private val project
    get() = projectRule.project

  @Test
  fun canHandleTextCommand() {
    deviceServices.configureShellCommand(device, "input keyboard -d -1 text Testing", "done")
    val result = runBlockingWithTimeout { shellInputProvider.input(
      project = project,
      serialNumber = serialNumber,
      source = "keyboard",
      displayID = -1,
      command = "text",
      args = listOf("Testing")) }
    assertEquals("done", result)
  }

  @Test
  fun canHandleKeyEvent() {
    deviceServices.configureShellCommand(device, "input touchscreen -d 5 motionevent DOWN 500 200", "done")
    val result = runBlockingWithTimeout { shellInputProvider.input(
      project = project,
      serialNumber = serialNumber,
      source = "touchscreen",
      displayID = 5,
      command = "motionevent",
      args = listOf("DOWN", "500", "200")) }
    assertEquals("done", result)
  }

  @Test
  fun textCommandEncodesSpaces() {
    var commandLine = getCommandLine("text", listOf("What", "is up", "people?"))
    assertEquals("text What%sis%sup%speople?", commandLine)

    commandLine = getCommandLine("text", listOf("\"What is up people?\""))
    assertEquals("text \"What is up people?\"", commandLine)

    commandLine = getCommandLine("text", listOf("'What is up people?'"))
    assertEquals("text 'What is up people?'", commandLine)

    commandLine = getCommandLine("text", listOf("\\\"What is up people?\\\""))
    assertEquals("text \\\"What%sis%sup%speople?\\\"", commandLine)
  }
}