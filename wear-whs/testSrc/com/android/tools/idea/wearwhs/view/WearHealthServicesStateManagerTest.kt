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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.CapabilityState
import com.android.tools.idea.wearwhs.communication.FakeDeviceManager
import com.android.tools.idea.wearwhs.logger.WearHealthServicesEventLogger
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearHealthServicesEvent
import com.intellij.collaboration.async.mapState
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val capabilities =
  listOf(
    WhsCapability(
      WhsDataType.HEART_RATE_BPM,
      "wear.whs.capability.heart.rate.label",
      "wear.whs.capability.heart.rate.unit",
      isOverrideable = true,
      isStandardCapability = true,
    ),
    WhsCapability(
      WhsDataType.LOCATION,
      "wear.whs.capability.location.label",
      "wear.whs.capability.unit.none",
      isOverrideable = false,
      isStandardCapability = true,
    ),
    WhsCapability(
      WhsDataType.STEPS,
      "wear.whs.capability.steps.label",
      "wear.whs.capability.steps.unit",
      isOverrideable = true,
      isStandardCapability = false,
    ),
  )

class WearHealthServicesStateManagerTest {
  companion object {
    const val TEST_MAX_WAIT_TIME_SECONDS = 5L
    const val TEST_POLLING_INTERVAL_MILLISECONDS = 100L
  }

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val loggedEvents = mutableListOf<AndroidStudioEvent.Builder>()
  private val logger = WearHealthServicesEventLogger { loggedEvents.add(it) }
  private val deviceManager by lazy { FakeDeviceManager(capabilities) }
  private val stateManager by lazy {
    WearHealthServicesStateManagerImpl(deviceManager, logger, TEST_POLLING_INTERVAL_MILLISECONDS)
      .also { it.serialNumber = "test" }
  }

  @Before
  fun setUp() = runBlocking {
    loggedEvents.clear()
    Disposer.register(projectRule.testRootDisposable, stateManager)
    // Wait until the state manager is idle
    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)
  }

  @Test
  fun `test state manager has the correct list of capabilities`() = runBlocking {
    assertThat(stateManager.capabilitiesList).isEqualTo(capabilities)
  }

  @Test
  fun `test state manager has the correct list of capabilities enabled when preset is selected`() =
    runBlocking {
      stateManager.preset.value = Preset.STANDARD

      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)
    }

  @Test
  fun `test state manager reports to the subscribers when all capabilities preset is selected`() =
    runBlocking {
      stateManager.preset.value = Preset.STANDARD

      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)

      stateManager.preset.value = Preset.ALL

      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
    }

  @Test
  fun `test state manager initialises all capabilities to synced, enabled and no override`() =
    runBlocking {
      stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
      stateManager.getState(capabilities[1]).mapState { it.synced }.waitForValue(true)
      stateManager.getState(capabilities[2]).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(null)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(null)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(null)
    }

  @Test
  fun `test state manager sets capabilities that are not returned by device manager to default state`() =
    runBlocking {
      stateManager.setCapabilityEnabled(capabilities[0], false)
      stateManager.setOverrideValue(capabilities[0], 3f)

      stateManager.applyChanges()

      stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(3f)

      deviceManager.clearContentProvider()

      stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[0])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(null)
    }

  @Test
  fun `test getCapabilityEnabled has the correct value`() = runBlocking {
    stateManager.setCapabilityEnabled(capabilities[0], false)
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
  }

  @Test
  fun `test getOverrideValue has the correct value`() = runBlocking {
    stateManager.setOverrideValue(capabilities[1], 3f)

    stateManager
      .getState(capabilities[1])
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(3f)
    stateManager.getState(capabilities[1]).mapState { it.synced }.waitForValue(false)
  }

  @Test
  fun `test reset sets the preset to all, removes overrides and invokes device manager`() =
    runBlocking {
      stateManager.preset.value = Preset.STANDARD

      stateManager.setOverrideValue(capabilities[1], 3f)

      assertEquals(0, deviceManager.clearContentProviderInvocations)

      stateManager.reset()

      stateManager.preset.waitForValue(Preset.ALL)
      stateManager
        .getState(capabilities[2])
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(capabilities[1])
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(null)
      stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)

      assertEquals(1, deviceManager.clearContentProviderInvocations)
    }

  @Test
  fun `when an exercise is ongoing reset only clears the overridden values`(): Unit = runBlocking {
    stateManager.preset.value = Preset.STANDARD

    stateManager.setOverrideValue(capabilities[1], 3f)

    assertEquals(0, deviceManager.overrideValuesInvocations)

    stateManager.setOngoingExerciseForTest(true)
    stateManager.reset()

    stateManager.preset.waitForValue(Preset.STANDARD)
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(capabilities[1])
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(null)
    stateManager
      .getState(capabilities[2])
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
    assertEquals(1, deviceManager.overrideValuesInvocations)
  }

  @Test
  fun `test applyChanges sends synced and status updates`(): Unit = runBlocking {
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(capabilities[1])
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(capabilities[2])
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
    stateManager.getState(capabilities[1]).mapState { it.synced }.waitForValue(true)
    stateManager.getState(capabilities[2]).mapState { it.synced }.waitForValue(true)

    stateManager.setCapabilityEnabled(capabilities[0], false)
    stateManager.setCapabilityEnabled(capabilities[1], true)
    stateManager.setOverrideValue(capabilities[1], 3f)
    stateManager.setCapabilityEnabled(capabilities[2], true)

    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(false)
    stateManager.getState(capabilities[1]).mapState { it.synced }.waitForValue(false)
    stateManager.getState(capabilities[2]).mapState { it.synced }.waitForValue(true)

    stateManager.applyChanges()

    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)

    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
    stateManager.getState(capabilities[1]).mapState { it.synced }.waitForValue(true)
    stateManager.getState(capabilities[2]).mapState { it.synced }.waitForValue(true)

    assertThat(deviceManager.loadCurrentCapabilityStates().getOrThrow())
      .containsEntry(capabilities[0].dataType, CapabilityState(false, null))
    assertThat(deviceManager.loadCurrentCapabilityStates().getOrThrow())
      .containsEntry(capabilities[1].dataType, CapabilityState(true, 3f))

    assertThat(loggedEvents).hasSize(2)
    assertThat(loggedEvents[0].kind)
      .isEqualTo(AndroidStudioEvent.EventKind.WEAR_HEALTH_SERVICES_TOOL_WINDOW_EVENT)
    assertThat(loggedEvents[0].wearHealthServicesEvent.kind)
      .isEqualTo(WearHealthServicesEvent.EventKind.EMULATOR_BOUND)
    assertThat(loggedEvents[1].kind)
      .isEqualTo(AndroidStudioEvent.EventKind.WEAR_HEALTH_SERVICES_TOOL_WINDOW_EVENT)
    assertThat(loggedEvents[1].wearHealthServicesEvent.kind)
      .isEqualTo(WearHealthServicesEvent.EventKind.APPLY_CHANGES_SUCCESS)
  }

  @Test
  fun `test applyChanges sends error status update`(): Unit = runBlocking {
    deviceManager.failState = true
    stateManager.setCapabilityEnabled(capabilities[0], false)

    stateManager.applyChanges()

    stateManager.status.waitForValue(WhsStateManagerStatus.ConnectionLost)

    assertThat(loggedEvents).hasSize(2)
    assertThat(loggedEvents[0].kind)
      .isEqualTo(AndroidStudioEvent.EventKind.WEAR_HEALTH_SERVICES_TOOL_WINDOW_EVENT)
    assertThat(loggedEvents[0].wearHealthServicesEvent.kind)
      .isEqualTo(WearHealthServicesEvent.EventKind.EMULATOR_BOUND)
    assertThat(loggedEvents[1].kind)
      .isEqualTo(AndroidStudioEvent.EventKind.WEAR_HEALTH_SERVICES_TOOL_WINDOW_EVENT)
    assertThat(loggedEvents[1].wearHealthServicesEvent.kind)
      .isEqualTo(WearHealthServicesEvent.EventKind.APPLY_CHANGES_FAILURE)
  }

  @Test
  fun `test applyChanges sends idle status update when retry succeeds`(): Unit = runBlocking {
    stateManager.setCapabilityEnabled(capabilities[0], false)

    deviceManager.failState = true

    stateManager.applyChanges()
    stateManager.status.waitForValue(WhsStateManagerStatus.ConnectionLost)

    deviceManager.failState = false

    stateManager.applyChanges()
    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)
  }

  @Test
  fun `test stateManager periodically updates the values from the device`() = runBlocking {
    stateManager.applyChanges()

    // Disable value on-device
    deviceManager.setCapabilities(mapOf(capabilities[0].dataType to false))

    // Verify that the value is updated
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
  }

  @Test
  fun `test stateManager periodically updates the override values from the device`() = runBlocking {
    stateManager.applyChanges()

    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)

    // Enable sensor and override value on device
    deviceManager.setCapabilities(mapOf(capabilities[0].dataType to true))
    deviceManager.overrideValues(mapOf(capabilities[0].dataType to 10f))

    // Verify that the value is updated
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(capabilities[0])
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(10f)
  }

  @Test
  fun `test stateManager periodically updates the exercise status from the device`() = runBlocking {
    stateManager.ongoingExercise.waitForValue(false)
    deviceManager.activeExercise = true

    // Verify that the value is updated
    stateManager.ongoingExercise.waitForValue(true)
  }

  private suspend fun <T> StateFlow<T>.waitForValue(
    value: T,
    timeoutSeconds: Long = TEST_MAX_WAIT_TIME_SECONDS,
  ) {
    awaitStatus("Timeout waiting for value $value", timeoutSeconds.seconds) { it == value }
  }

  @Test
  fun `test isWhsVersionSupported fail state reports whs version as not supported`(): Unit =
    runBlocking {
      deviceManager.failState = true

      val isSupported = stateManager.isWhsVersionSupported()

      assertFalse(isSupported)
    }

  @Test
  fun `test triggered events are forwarded to device manager`(): Unit = runBlocking {
    stateManager.triggerEvent(EventTrigger("key", "label"))

    assertThat(deviceManager.triggeredEvents).hasSize(1)
    assertThat(deviceManager.triggeredEvents[0].eventKey).isEqualTo("key")
  }

  @Test
  fun `test triggered event failures are reflected in state manager`(): Unit = runBlocking {
    deviceManager.failState = true

    stateManager.triggerEvent(EventTrigger("key", "label"))

    stateManager.status.waitForValue(WhsStateManagerStatus.ConnectionLost)
  }

  @Test
  fun `reset clears the state successfully`(): Unit = runBlocking {
    stateManager.setCapabilityEnabled(capabilities[0], false)

    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(false)
    stateManager.reset()

    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
  }

  @Test
  fun `when there is no connection reset does not clear the state`(): Unit = runBlocking {
    stateManager.setCapabilityEnabled(capabilities[0], false)

    stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(false)
    deviceManager.failState = true
    stateManager.reset()

    try {
      stateManager.getState(capabilities[0]).mapState { it.synced }.waitForValue(true)
      fail("Value should not reset if the communication with the device is lost")
    } catch (_: AssertionError) {}
  }
}
