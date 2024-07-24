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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
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
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val heartRateBpmCapability =
  WhsCapability(
    WhsDataType.HEART_RATE_BPM,
    "wear.whs.capability.heart.rate.label",
    "wear.whs.capability.heart.rate.unit",
    isOverrideable = true,
    isStandardCapability = true,
  )
private val locationCapability =
  WhsCapability(
    WhsDataType.LOCATION,
    "wear.whs.capability.location.label",
    "wear.whs.capability.unit.none",
    isOverrideable = false,
    isStandardCapability = true,
  )
private val stepsCapability =
  WhsCapability(
    WhsDataType.STEPS,
    "wear.whs.capability.steps.label",
    "wear.whs.capability.steps.unit",
    isOverrideable = true,
    isStandardCapability = false,
  )

private val capabilities = listOf(heartRateBpmCapability, locationCapability, stepsCapability)

class WearHealthServicesStateManagerTest {
  companion object {
    const val TEST_MAX_WAIT_TIME_SECONDS = 5L
    const val TEST_POLLING_INTERVAL_MILLISECONDS = 100L
    val TEST_STATE_STALENESS_THRESHOLD = 2.seconds
  }

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val loggedEvents = mutableListOf<AndroidStudioEvent.Builder>()
  private val logger = WearHealthServicesEventLogger { loggedEvents.add(it) }
  private lateinit var deviceManager: FakeDeviceManager
  private lateinit var stateManager: WearHealthServicesStateManager

  @Before
  fun setUp() = runBlocking {
    loggedEvents.clear()

    val testWorkerScope =
      AndroidCoroutineScope(projectRule.testRootDisposable, AndroidDispatchers.workerThread)
    deviceManager = FakeDeviceManager(capabilities)
    stateManager =
      WearHealthServicesStateManagerImpl(
          deviceManager = deviceManager,
          eventLogger = logger,
          workerScope = testWorkerScope,
          pollingIntervalMillis = TEST_POLLING_INTERVAL_MILLISECONDS,
          stateStalenessThreshold = TEST_STATE_STALENESS_THRESHOLD,
        )
        .also {
          it.serialNumber = "test"
          Disposer.register(projectRule.testRootDisposable, it)
        }
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
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)
    }

  @Test
  fun `test state manager reports to the subscribers when all capabilities preset is selected`() =
    runBlocking {
      stateManager.preset.value = Preset.STANDARD

      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)

      stateManager.preset.value = Preset.ALL

      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
    }

  @Test
  fun `test state manager initialises all capabilities to synced, enabled and no override`() =
    runBlocking {
      stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
      stateManager.getState(locationCapability).mapState { it.synced }.waitForValue(true)
      stateManager.getState(stepsCapability).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.noValue())
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.LOCATION.noValue())
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.STEPS.noValue())
    }

  @Test
  fun `test state manager sets capabilities that are not returned by device manager to default state`() =
    runBlocking {
      stateManager.setCapabilityEnabled(heartRateBpmCapability, false)
      stateManager.setOverrideValue(heartRateBpmCapability, 3f)

      stateManager.applyChanges()

      stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(false)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.value(3f))

      deviceManager.clearContentProvider()

      stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(heartRateBpmCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.HEART_RATE_BPM.noValue())
    }

  @Test
  fun `test getCapabilityEnabled has the correct value`() = runBlocking {
    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
  }

  @Test
  fun `test getOverrideValue has the correct value`() = runBlocking {
    stateManager.setOverrideValue(stepsCapability, 3)

    stateManager
      .getState(stepsCapability)
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(WhsDataType.STEPS.value(3))
    stateManager.getState(stepsCapability).mapState { it.synced }.waitForValue(false)
  }

  @Test
  fun `test reset sets the preset to all, removes overrides and invokes device manager`() =
    runBlocking {
      stateManager.preset.value = Preset.STANDARD

      stateManager.setOverrideValue(locationCapability, 3f)

      assertEquals(0, deviceManager.clearContentProviderInvocations)

      stateManager.reset()

      stateManager.preset.waitForValue(Preset.ALL)
      stateManager
        .getState(stepsCapability)
        .mapState { it.capabilityState.enabled }
        .waitForValue(true)
      stateManager
        .getState(locationCapability)
        .mapState { it.capabilityState.overrideValue }
        .waitForValue(WhsDataType.LOCATION.noValue())
      stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)

      assertEquals(1, deviceManager.clearContentProviderInvocations)
    }

  @Test
  fun `when an exercise is ongoing reset only clears the overridden values`(): Unit = runBlocking {
    stateManager.preset.value = Preset.STANDARD

    stateManager.setOverrideValue(locationCapability, 3f)
    deviceManager.activeExercise = true

    stateManager.ongoingExercise.waitForValue(true)

    assertEquals(0, deviceManager.overrideValuesInvocations)

    stateManager.reset()

    stateManager.preset.waitForValue(Preset.STANDARD)
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(locationCapability)
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(WhsDataType.LOCATION.noValue())
    stateManager
      .getState(stepsCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
    assertEquals(1, deviceManager.overrideValuesInvocations)
  }

  @Test
  fun `test applyChanges sends synced and status updates`(): Unit = runBlocking {
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(locationCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(stepsCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
    stateManager.getState(locationCapability).mapState { it.synced }.waitForValue(true)
    stateManager.getState(stepsCapability).mapState { it.synced }.waitForValue(true)

    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)
    stateManager.setCapabilityEnabled(locationCapability, true)
    stateManager.setOverrideValue(locationCapability, 3f)
    stateManager.setCapabilityEnabled(stepsCapability, true)
    stateManager.setOverrideValue(stepsCapability, 30)

    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(false)
    // Location capability can not be overridden (has no value) so it will remain synced
    stateManager.getState(locationCapability).mapState { it.synced }.waitForValue(true)
    stateManager.getState(stepsCapability).mapState { it.synced }.waitForValue(false)

    val result = stateManager.applyChanges()

    assertThat(result.isSuccess).isTrue()
    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)

    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
    stateManager.getState(locationCapability).mapState { it.synced }.waitForValue(true)
    stateManager.getState(stepsCapability).mapState { it.synced }.waitForValue(true)

    assertThat(deviceManager.loadCurrentCapabilityStates().getOrThrow())
      .containsEntry(
        heartRateBpmCapability.dataType,
        CapabilityState.disabled(WhsDataType.HEART_RATE_BPM),
      )
    assertThat(deviceManager.loadCurrentCapabilityStates().getOrThrow())
      .containsEntry(
        locationCapability.dataType,
        CapabilityState(true, WhsDataType.LOCATION.value(3f)),
      )

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
    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)

    val result = stateManager.applyChanges()

    assertThat(result.isSuccess).isFalse()
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
    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)

    deviceManager.failState = true

    var result = stateManager.applyChanges()
    assertThat(result.isSuccess).isFalse()
    stateManager.status.waitForValue(WhsStateManagerStatus.ConnectionLost)

    deviceManager.failState = false

    result = stateManager.applyChanges()
    assertThat(result.isSuccess).isTrue()
    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)
  }

  @Test
  fun `test stateManager periodically updates the values from the device`() = runBlocking {
    stateManager.applyChanges()

    // Disable value on-device
    deviceManager.setCapabilities(mapOf(WhsDataType.HEART_RATE_BPM to false))

    // Verify that the value is updated
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(false)
  }

  @Test
  fun `test stateManager periodically updates the override values from the device`() = runBlocking {
    stateManager.applyChanges()

    stateManager.status.waitForValue(WhsStateManagerStatus.Idle)

    // Enable sensor and override value on device
    deviceManager.setCapabilities(mapOf(WhsDataType.HEART_RATE_BPM to true))
    deviceManager.overrideValues(listOf(WhsDataType.HEART_RATE_BPM.value(10f)))

    // Verify that the value is updated
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.enabled }
      .waitForValue(true)
    stateManager
      .getState(heartRateBpmCapability)
      .mapState { it.capabilityState.overrideValue }
      .waitForValue(WhsDataType.HEART_RATE_BPM.value(10f))
  }

  @Test
  fun `test stateManager periodically updates the exercise status from the device`() = runBlocking {
    stateManager.ongoingExercise.waitForValue(false)
    deviceManager.activeExercise = true

    // Verify that the value is updated
    stateManager.ongoingExercise.waitForValue(true)
  }

  @Test
  fun `test triggered events are forwarded to device manager`(): Unit = runBlocking {
    val result = stateManager.triggerEvent(EventTrigger("key", "label"))

    assertThat(result.isSuccess).isTrue()
    assertThat(deviceManager.triggeredEvents).hasSize(1)
    assertThat(deviceManager.triggeredEvents[0].eventKey).isEqualTo("key")
  }

  @Test
  fun `test triggered event failures are reflected in state manager`(): Unit = runBlocking {
    deviceManager.failState = true

    val result = stateManager.triggerEvent(EventTrigger("key", "label"))

    assertThat(result.isSuccess).isFalse()
    stateManager.status.waitForValue(WhsStateManagerStatus.ConnectionLost)
  }

  @Test
  fun `reset clears the state successfully`(): Unit = runBlocking {
    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)

    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(false)
    val result = stateManager.reset()

    assertThat(result.isSuccess).isTrue()
    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
  }

  @Test
  fun `when there is no connection reset does not clear the state`(): Unit = runBlocking {
    stateManager.setCapabilityEnabled(heartRateBpmCapability, false)

    stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(false)
    deviceManager.failState = true
    val result = stateManager.reset()

    assertThat(result.isSuccess).isFalse()
    try {
      stateManager.getState(heartRateBpmCapability).mapState { it.synced }.waitForValue(true)
      fail("Value should not reset if the communication with the device is lost")
    } catch (_: AssertionError) {}
  }

  @Test
  fun `the state will become stale when sync fails`(): Unit = runBlocking {
    // after a successful sync the state should not be state
    deviceManager.failState = false
    stateManager.isStateStale.waitForValue(false)

    // when the state can't sync then it should eventually become stale
    deviceManager.failState = true
    stateManager.isStateStale.waitForValue(true)

    // once it's possible to sync again, then it the state should no longer be stale
    deviceManager.failState = false
    stateManager.isStateStale.waitForValue(false)
  }
}
