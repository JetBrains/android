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
  fun testSetAndClear_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsZero_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)
    commandHandler.debugViewAttributes = "0"

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("0")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsSet_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)
    commandHandler.debugViewAttributes = "1"

    assertThat(debugViewAttributes.set(process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSet_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)
    commandHandler.debugViewAttributesApplicationPackage = process.name

    assertThat(debugViewAttributes.set(process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSetToDifferentProcess_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)
    commandHandler.debugViewAttributesApplicationPackage = "com.example.MyOtherApp"

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testClearWhenPerAppIsSetToDifferentProcess_perAppSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project)

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    commandHandler.debugViewAttributesApplicationPackage = "com.example.MyOtherApp"

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo("com.example.MyOtherApp")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testSetAndClear_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project, true)

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsZero_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project, true)
    commandHandler.debugViewAttributes = "0"

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsSet_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project, true)
    commandHandler.debugViewAttributes = "1"

    assertThat(debugViewAttributes.set(process)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isNull()
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }

  @Test
  fun testSetAndClearWhenPerAppIsSet_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes(projectRule.project, true)
    commandHandler.debugViewAttributesApplicationPackage = process.name

    assertThat(debugViewAttributes.set(process)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)

    debugViewAttributes.clear(process)
    assertThat(commandHandler.debugViewAttributes).isNull()
    assertThat(commandHandler.debugViewAttributesApplicationPackage).isEqualTo(process.name)
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(2)
  }
}
