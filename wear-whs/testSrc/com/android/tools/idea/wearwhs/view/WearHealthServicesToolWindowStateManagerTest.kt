/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.ibm.icu.impl.Assert
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val capabilities = listOf(WhsCapability(
  "wear.whs.capability.heart.rate.label",
  "wear.whs.capability.heart.rate.unit",
  isOverrideable = true,
  isStandardCapability = true,
), WhsCapability(
  "wear.whs.capability.location.label",
  "wear.whs.capability.unit.none",
  isOverrideable = false,
  isStandardCapability = true,
), WhsCapability(
  "wear.whs.capability.steps.label",
  "wear.whs.capability.steps.unit",
  isOverrideable = true,
  isStandardCapability = false,
))

class WearHealthServicesToolWindowStateManagerTest : LightPlatformTestCase() {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val deviceManager by lazy { FakeDeviceManager(capabilities) }
  private val stateManager by lazy { WearHealthServicesToolWindowStateManagerImpl(deviceManager) }

  @Before
  public override fun setUp() {
    super.setUp()
    disposeOnTearDown(stateManager)
  }

  @Test
  fun `test state manager has the correct list of capabilities`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)
  }

  @Test
  fun `test state manager has the correct list of capabilities enabled when preset is selected`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setPreset(Preset.STANDARD)

    stateManager.getCapabilityEnabled(capabilities[0]).waitForValue(true)
    stateManager.getCapabilityEnabled(capabilities[1]).waitForValue(true)
    stateManager.getCapabilityEnabled(capabilities[2]).waitForValue(false)
  }

  @Test
  fun `test state manager reports to the subscribers when all capabilities preset is selected`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setPreset(Preset.ALL)

    stateManager.getCapabilityEnabled(capabilities[0]).waitForValue(true)
    stateManager.getCapabilityEnabled(capabilities[1]).waitForValue(true)
    stateManager.getCapabilityEnabled(capabilities[2]).waitForValue(true)
  }

  @Test
  fun `test getCapabilityEnabled has the correct value`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setCapabilityEnabled(capabilities[0], false)

    stateManager.getCapabilityEnabled(capabilities[0]).waitForValue(false)
  }

  @Test
  fun `test getOverrideValue has the correct value`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setOverrideValue(capabilities[1], 3f)

    stateManager.getOverrideValue(capabilities[1]).waitForValue(3f)
  }
}

suspend fun <T> StateFlow<T>.waitForValue(value: T, timeout: Long = 1000) {
  val received = mutableListOf<T>()
  try {
    withTimeout(timeout) { takeWhile { it != value }.collect { received.add(it) } }
  } catch (ex: TimeoutCancellationException) {
    Assert.fail("Timed out waiting for value $value. Received values so far $received")
  }
}
