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
import com.android.tools.idea.wearwhs.communication.OnDeviceCapabilityState
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.junit.Ignore

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

@Ignore("b/316071318")
class WearHealthServicesToolWindowStateManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val deviceManager by lazy { FakeDeviceManager(capabilities) }
  private val stateManager by lazy { WearHealthServicesToolWindowStateManagerImpl(deviceManager) }

  @Before
  fun setUp() {
    Disposer.register(projectRule.testRootDisposable, stateManager)
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

  @Test
  fun `test reset sets the preset to standard and removes overrides`() = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setPreset(Preset.ALL)

    stateManager.setOverrideValue(capabilities[1], 3f)

    stateManager.reset()

    stateManager.getPreset().waitForValue(Preset.STANDARD)
    stateManager.getCapabilityEnabled(capabilities[2]).waitForValue(false)
    stateManager.getOverrideValue(capabilities[1]).waitForValue(null)
  }

  @Test
  fun `test applyChanges sends synced and status updates`(): Unit = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setCapabilityEnabled(capabilities[0], false)
    stateManager.setCapabilityEnabled(capabilities[1], true)
    stateManager.setOverrideValue(capabilities[1], 3f)
    stateManager.setCapabilityEnabled(capabilities[2], true)

    stateManager.getSynced(capabilities[0]).waitForValue(false)
    stateManager.getSynced(capabilities[1]).waitForValue(false)
    stateManager.getSynced(capabilities[2]).waitForValue(false)

    stateManager.applyChanges()

    stateManager.getStatus().waitForValue(WhsStateManagerStatus.Idle)

    stateManager.getSynced(capabilities[0]).waitForValue(true)
    stateManager.getSynced(capabilities[1]).waitForValue(true)
    stateManager.getSynced(capabilities[2]).waitForValue(true)

    assertThat(deviceManager.loadCurrentCapabilityStates()).containsExactly(
      capabilities[0], OnDeviceCapabilityState(false, null),
      capabilities[1], OnDeviceCapabilityState(true, 3f),
      capabilities[2], OnDeviceCapabilityState(true, null)
    )
  }

  @Test
  fun `test applyChanges sends error status update`(): Unit = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    deviceManager.failState = true
    stateManager.setCapabilityEnabled(capabilities[0], false)

    stateManager.applyChanges()

    stateManager.getStatus().waitForValue(WhsStateManagerStatus.ConnectionLost)
  }

  @Test
  fun `test applyChanges sends idle status update when retry succeeds`(): Unit = runBlocking {
    stateManager.getCapabilitiesList().waitForValue(capabilities)

    stateManager.setCapabilityEnabled(capabilities[0], false)

    deviceManager.failState = true

    stateManager.applyChanges()
    stateManager.getStatus().waitForValue(WhsStateManagerStatus.ConnectionLost)

    deviceManager.failState = false

    stateManager.applyChanges()
    stateManager.getStatus().waitForValue(WhsStateManagerStatus.Idle)
  }

  private suspend fun <T> StateFlow<T>.waitForValue(value: T, timeout: Long = 1000) {
    val received = mutableListOf<T>()
    try {
      withTimeout(timeout) { takeWhile { it != value }.collect { received.add(it) } }
    } catch (ex: TimeoutCancellationException) {
      Assert.fail("Timed out waiting for value $value. Received values so far $received")
    }
  }
}
