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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.AdbServiceRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class DebugViewAttributesTest {
  private val commandHandler = FakeShellCommandHandler()
  private val projectRule = AndroidProjectRule.inMemory()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private val adbService = AdbServiceRule(projectRule::project, adbRule)
  lateinit var process: ProcessDescriptor

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(adbService)!!

  @Before
  fun before() {
    process = MODERN_DEVICE.createProcess()
    val device = process.device
    adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())
  }

  @Test
  fun testSetAndClear_perAppSetting() = runWithFlagState(false) {
    val debugViewAttributes = DebugViewAttributes()

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsZero_perAppSetting() = runWithFlagState(false) {
    val debugViewAttributes = DebugViewAttributes()
    commandHandler.debugViewAttributes = "0"

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsSet_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes()
    commandHandler.debugViewAttributes = "1"

    assertThat(debugViewAttributes.set(projectRule.project, process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSet_perAppSetting() = runWithFlagState(false) {
    val debugViewAttributes = DebugViewAttributes()
    commandHandler.debugViewAttributesApplicationPackage = process.name

    assertThat(debugViewAttributes.set(projectRule.project, process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSetToDifferentProcess_perAppSetting() = runWithFlagState(false) {
    val debugViewAttributes = DebugViewAttributes()
    commandHandler.debugViewAttributesApplicationPackage = "com.example.MyOtherApp"

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testClearWhenPerAppIsSetToDifferentProcess_perAppSetting() = runWithFlagState(false) {
    val debugViewAttributes = DebugViewAttributes()

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    commandHandler.debugViewAttributesApplicationPackage = "com.example.MyOtherApp"

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo("com.example.MyOtherApp")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testSetAndClear_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes { true }

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsZero_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes { true }
    commandHandler.debugViewAttributes = "0"

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsSet_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes { true }
    commandHandler.debugViewAttributes = "1"

    assertThat(debugViewAttributes.set(projectRule.project, process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSet_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes { true }
    commandHandler.debugViewAttributesApplicationPackage = process.name

    assertThat(debugViewAttributes.set(projectRule.project, process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(projectRule.project, process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }
}

private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
  val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
  val flagPreviousState = flag.get()
  flag.override(desiredFlagState)

  task()

  // restore flag state
  flag.override(flagPreviousState)
}

