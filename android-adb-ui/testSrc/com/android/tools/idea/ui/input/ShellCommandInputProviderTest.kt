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

  @get:Rule val rule = RuleChain(projectRule, fakeAdbSessionRule)

  private val deviceServices = fakeAdbSessionRule.adbSession.deviceServices
  private val serialNumber = "123"
  private val device = DeviceSelector.fromSerialNumber(serialNumber)
  private var shellInputProvider = ShellCommandInputProvider()
  private val project
    get() = projectRule.project

  @Test
  fun canHandleTextCommand() {
    deviceServices.configureShellCommand(device, "input keyboard -d -1 text Testing", "done")
    val result = runBlockingWithTimeout {
      shellInputProvider.input(
        project = project,
        serialNumber = serialNumber,
        source = "keyboard",
        displayID = -1,
        command = "text",
        args = listOf("Testing"),
      )
    }
    assertEquals("done", result)
  }

  @Test
  fun canHandleKeyEvent() {
    deviceServices.configureShellCommand(device, "input touchscreen -d 5 motionevent DOWN 500 200", "done")
    val result = runBlockingWithTimeout {
      shellInputProvider.input(
        project = project,
        serialNumber = serialNumber,
        source = "touchscreen",
        displayID = 5,
        command = "motionevent",
        args = listOf("DOWN", "500", "200"),
      )
    }
    assertEquals("done", result)
  }

  @Test
  fun textCommandEncodesSpaces() {
    var commandLine = getCommandLine("text", listOf("What", "is up", "people?"))
    assertEquals("text 'What%sis%sup%speople?'", commandLine)

    commandLine = getCommandLine("text", listOf("\"What is up people?\""))
    assertEquals("text 'What is up people?'", commandLine)

    commandLine = getCommandLine("text", listOf("'What is up people?'"))
    assertEquals("text 'What is up people?'", commandLine)

    commandLine = getCommandLine("text", listOf("\\\"What is up people?\\\""))
    assertEquals("text '\\\"What%sis%sup%speople?\\\"'", commandLine)
  }

  @Test
  fun commandInjectionIsNeutralized() {
    val commandLine =
      getCommandLine("keyevent", listOf("66; uiautomator dump /data/local/tmp/uidump.xml && cat /data/local/tmp/uidump.xml"))
    assertEquals("keyevent '66; uiautomator dump /data/local/tmp/uidump.xml && cat /data/local/tmp/uidump.xml'", commandLine)
  }

  @Test
  fun singleQuoteIsEscaped() {
    var commandLine = getCommandLine("keyevent", listOf("66'77"))
    assertEquals("keyevent '66'\\''77'", commandLine)

    commandLine = getCommandLine("text", listOf("hello'world"))
    assertEquals("text 'hello'\\''world'", commandLine)
  }

  @Test
  fun sourceIsEscaped() {
    deviceServices.configureShellCommand(
      deviceSelector = device,
      command = "input 'touchscreen; injection' -d 5 motionevent DOWN 500 200",
      stdout = "",
      stderr = "Error: Invalid source 'touchscreen; injection'",
      exitCode = 1,
    )
    val result = runBlockingWithTimeout {
      shellInputProvider.input(
        project = project,
        serialNumber = serialNumber,
        source = "touchscreen; injection",
        displayID = 5,
        command = "motionevent",
        args = listOf("DOWN", "500", "200"),
      )
    }
    assertEquals("Command exited with 1. Error: Invalid source 'touchscreen; injection'", result)
  }

  @Test
  fun commandIsEscaped() {
    deviceServices.configureShellCommand(
      deviceSelector = device,
      command = "input touchscreen -d 5 'tap; injection' 100 200",
      stdout = "",
      stderr = "Error: Invalid command 'tap; injection'",
      exitCode = 1,
    )
    val result = runBlockingWithTimeout {
      shellInputProvider.input(
        project = project,
        serialNumber = serialNumber,
        source = "touchscreen",
        displayID = 5,
        command = "tap; injection",
        args = listOf("100", "200"),
      )
    }
    assertEquals("Command exited with 1. Error: Invalid command 'tap; injection'", result)
  }

  @Test
  fun commandInjectionInCommandNameIsNeutralized() {
    val commandLine = getCommandLine("tap; uiautomator dump", listOf("100", "200"))
    assertEquals("'tap; uiautomator dump' 100 200", commandLine)
  }
}
