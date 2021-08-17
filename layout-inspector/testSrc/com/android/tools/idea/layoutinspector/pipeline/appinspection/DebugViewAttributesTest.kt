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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.net.Socket
import java.util.ArrayDeque

class DebugViewAttributesTest {
  private val commandHandler = ShellCommandHandler()
  private val projectRule = AndroidProjectRule.inMemory()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private lateinit var debugViewAttributes: DebugViewAttributes
  private lateinit var processName: String

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule)!!

  @Before
  fun before() {
    val process = MODERN_DEVICE.createProcess()
    val device = process.device
    adbRule.attachDevice(
      device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString(), DeviceState.HostConnectionType.USB)

    debugViewAttributes = DebugViewAttributes(adbRule.bridge, projectRule.project, process)
    processName = process.name
  }

  @Test
  fun testSetAndClear() {
    debugViewAttributes.set()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(processName)
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(1)

    debugViewAttributes.clear()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenGlobalIsZero() {
    commandHandler.debugViewAttributes = "0"

    debugViewAttributes.set()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(processName)
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(1)

    debugViewAttributes.clear()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenGlobalIsSet() {
    commandHandler.debugViewAttributes = "1"

    debugViewAttributes.set()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(0)

    debugViewAttributes.clear()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenAppIsSet() {
    commandHandler.debugViewAttributesApplicationPackage = processName

    debugViewAttributes.set()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(processName)
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(0)

    debugViewAttributes.clear()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(processName)
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenAppIsSetToDifferentProcess() {
    commandHandler.debugViewAttributesApplicationPackage = "com.example.MyOtherApp"

    debugViewAttributes.set()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(processName)
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(1)

    debugViewAttributes.clear()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChanges).isEqualTo(2)
  }

  private class ShellCommandHandler : DeviceCommandHandler("shell") {
    var debugViewAttributes: String? = null
    var debugViewAttributesApplicationPackage: String? = null
    var debugViewAttributesChanges = 0

    override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
      val response = when (command) {
        "shell" -> handleShellCommand(args) ?: return false
        else -> return false
      }
      writeOkay(socket.getOutputStream())
      writeString(socket.getOutputStream(), response)
      return true
    }

    private fun handleShellCommand(argsAsString: String): String? {
      val args = ArrayDeque(argsAsString.split(' '))
      if (args.poll() != "settings") {
        return null
      }
      val operation = args.poll()
      if (args.poll() != "global") {
        return null
      }
      val variable = when (args.poll()) {
        "debug_view_attributes" -> this::debugViewAttributes
        "debug_view_attributes_application_package" -> this::debugViewAttributesApplicationPackage
        else -> return null
      }
      val argument = if (args.isEmpty()) "" else args.poll()
      if (args.isNotEmpty()) {
        return null
      }
      return when (operation) {
        "get" -> { variable.get().toString() }
        "put" -> { variable.set(argument); debugViewAttributesChanges++; ""}
        "delete" -> { variable.set(null); debugViewAttributesChanges++; ""}
        else -> null
      }
    }
  }
}
