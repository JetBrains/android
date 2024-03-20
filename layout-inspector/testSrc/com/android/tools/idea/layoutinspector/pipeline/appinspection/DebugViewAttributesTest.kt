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
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
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
  lateinit var device: DeviceDescriptor

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(adbService)!!

  @Before
  fun before() {
    val process = MODERN_DEVICE.createProcess()
    device = process.device
    adbRule.attachDevice(
      device.serial,
      device.manufacturer,
      device.model,
      device.version,
      device.apiLevel.toString(),
    )
  }

  @Test
  fun testSetAndClear_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes.getInstance()

    assertThat(debugViewAttributes.set(projectRule.project, device)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsZero_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes.getInstance()
    commandHandler.debugViewAttributes = "0"

    assertThat(debugViewAttributes.set(projectRule.project, device)).isTrue()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testSetAndClearWhenPerDeviceIsSet_perDeviceSetting() {
    val debugViewAttributes = DebugViewAttributes.getInstance()
    commandHandler.debugViewAttributes = "1"

    assertThat(debugViewAttributes.set(projectRule.project, device)).isFalse()
    assertThat(commandHandler.debugViewAttributes).isEqualTo("1")
    assertThat(commandHandler.debugViewAttributesChangesCount).isEqualTo(0)
  }
}
