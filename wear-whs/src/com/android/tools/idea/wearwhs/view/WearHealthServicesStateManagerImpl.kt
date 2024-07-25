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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.WhsDataValue
import com.android.tools.idea.wearwhs.communication.CapabilityState
import com.android.tools.idea.wearwhs.communication.ConnectionLostException
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.android.tools.idea.wearwhs.logger.WearHealthServicesEventLogger
import com.intellij.openapi.Disposable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val MAX_WAIT_TIME_FOR_POLL_UPDATE = 5.seconds
private val MAX_WAIT_TIME_FOR_MODIFICATION = 10.seconds

internal class WearHealthServicesStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager,
  private val eventLogger: WearHealthServicesEventLogger = WearHealthServicesEventLogger(),
  private val workerScope: CoroutineScope,
  @VisibleForTesting
  private val pollingIntervalMillis: Long =
    StudioFlags.WEAR_HEALTH_SERVICES_POLLING_INTERVAL_MS.get(),
) : WearHealthServicesStateManager, Disposable {

  override val preset: MutableStateFlow<Preset> = MutableStateFlow(Preset.ALL)

  override val capabilitiesList = deviceManager.getCapabilities()

  private val capabilityToState =
    capabilitiesList.associateWith {
      MutableStateFlow(CapabilityUIState(true, CapabilityState(true, it.dataType.noValue())))
    }

  private val _status = MutableStateFlow<WhsStateManagerStatus>(WhsStateManagerStatus.Initializing)
  override val status = _status

  private val _ongoingExercise = MutableStateFlow(false)
  override val ongoingExercise = _ongoingExercise

  override var serialNumber: String? = null
    set(value) {
      // Only accept non-null values to avoid tool window unbinding completely
      value?.let {
        eventLogger.logBindEmulator()
        deviceManager.setSerialNumber(it)
        field = value
      }
    }

  /** Lock guarding [capabilityToState] updates and reads to make them consistent. */
  private val capabilityUpdatesLock = Mutex()

  init {
    workerScope.launch {
      while (true) {
        updateState()
        delay(pollingIntervalMillis.milliseconds)
      }
    }
    workerScope.launch {
      preset.collect {
        when (it) {
          Preset.STANDARD ->
            for (capability in capabilityToState.keys) {
              setCapabilityEnabled(capability, capability.isStandardCapability)
            }
          Preset.ALL ->
            for (capability in capabilityToState.keys) {
              setCapabilityEnabled(capability, true)
            }
          Preset.CUSTOM -> {}
        }
        // First time this runs, make status idle
        if (_status.value == WhsStateManagerStatus.Initializing) {
          _status.value = WhsStateManagerStatus.Idle
        }
      }
    }
  }

  private suspend fun updateState() {
    runWithStatus(WhsStateManagerStatus.Busy, MAX_WAIT_TIME_FOR_POLL_UPDATE) {
      val activeExerciseResult =
        deviceManager.loadActiveExercise().map { activeExercise ->
          _ongoingExercise.value = activeExercise
        }
      if (activeExerciseResult.isFailure) {
        // Return early on failure
        activeExerciseResult
      } else {
        deviceManager.loadCurrentCapabilityStates().map { deviceStates ->
          // Go through all capabilities, not just the ones returned by the device
          capabilityUpdatesLock.withLock {
            capabilityToState.forEach { (capability, currentState) ->
              if (currentState.value.synced) {
                currentState.value =
                  currentState.value.copy(
                    // If content provider doesn't return a capability, that means the capability is
                    // enabled with no overrides
                    capabilityState =
                      deviceStates[capability.dataType]
                        ?: CapabilityState.enabled(capability.dataType)
                  )
              }
            }
          }
        }
      }
    }
  }

  /**
   * Waits until state manager is idle, then executes the given block after setting the state
   * manager status to [status]. It restores the status to [WhsStateManagerStatus.Idle] after the
   * block is executed, or [WhsStateManagerStatus.ConnectionLost] if the block throws a
   * [ConnectionLostException].
   */
  private suspend fun runWithStatus(
    status: WhsStateManagerStatus,
    timeout: Duration,
    block: suspend () -> Result<Unit>,
  ): Result<Unit> {
    return try {
      withTimeout(timeout) {
        _status.takeWhile { !it.idle }.collect {}
        _status.value = status
        block()
          .onSuccess { _status.value = WhsStateManagerStatus.Idle }
          .onFailure {
            _status.value = WhsStateManagerStatus.ConnectionLost
          }
      }
    } catch (exception: TimeoutCancellationException) {
      _status.value = WhsStateManagerStatus.Timeout
      Result.failure(exception)
    }
  }

  override suspend fun triggerEvent(eventTrigger: EventTrigger) =
    runWithStatus(WhsStateManagerStatus.Syncing, MAX_WAIT_TIME_FOR_MODIFICATION) {
      deviceManager.triggerEvent(eventTrigger)
    }

  override fun getState(capability: WhsCapability): StateFlow<CapabilityUIState> =
    capabilityToState[capability]?.asStateFlow() ?: throw IllegalArgumentException()

  override suspend fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean) =
    capabilityUpdatesLock.withLock {
      val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
      if (enabled == stateFlow.value.capabilityState.enabled) {
        return
      }
      val newState =
        stateFlow.value.copy(
          capabilityState =
            if (enabled) stateFlow.value.capabilityState.enable()
            else stateFlow.value.capabilityState.disable(),
          synced = false,
        )
      stateFlow.value = newState
    }

  override suspend fun setOverrideValue(capability: WhsCapability, value: Number) =
    capabilityUpdatesLock.withLock {
      val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
      val dataValue = capability.dataType.value(value)
      if (dataValue == stateFlow.value.capabilityState.overrideValue) {
        return
      }
      val newState =
        stateFlow.value.copy(
          capabilityState = stateFlow.value.capabilityState.override(dataValue),
          synced = false,
        )
      stateFlow.value = newState
    }

  override suspend fun clearOverrideValue(capability: WhsCapability) =
    capabilityUpdatesLock.withLock {
      val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
      if (stateFlow.value.capabilityState.overrideValue is WhsDataValue.NoValue) {
        return
      }
      val newState =
        stateFlow.value.copy(
          capabilityState = stateFlow.value.capabilityState.clearOverride(),
          synced = false,
        )
      stateFlow.value = newState
    }

  override suspend fun applyChanges() =
    runWithStatus(WhsStateManagerStatus.Syncing, MAX_WAIT_TIME_FOR_MODIFICATION) {
      val capabilityUpdates: Map<WhsDataType, Boolean>
      val overrideUpdates: List<WhsDataValue>

      capabilityUpdatesLock.withLock {
        capabilityUpdates =
          capabilityToState.entries.associate {
            it.key.dataType to it.value.value.capabilityState.enabled
          }
        overrideUpdates =
          capabilityToState.entries.map { it.value.value.capabilityState.overrideValue }
      }

      // Return early if any of the updates fail
      deviceManager.setCapabilities(capabilityUpdates).onFailure {
        eventLogger.logApplyChangesFailure()
        return@runWithStatus Result.failure(it)
      }
      deviceManager.overrideValues(overrideUpdates).onFailure {
        eventLogger.logApplyChangesFailure()
        return@runWithStatus Result.failure(it)
      }
      capabilityUpdatesLock.withLock {
        capabilityToState.entries.forEach {
          val stateFlow = it.value
          val state = stateFlow.value
          stateFlow.value = state.copy(synced = true)
        }
      }
      eventLogger.logApplyChangesSuccess()
      Result.success(Unit)
    }

  private fun resetUiState() =
    capabilityToState.forEach { (_, state) ->
      val previousCapabilityState = state.value.capabilityState
      state.value =
        CapabilityUIState(
          synced = true,
          capabilityState =
            if (ongoingExercise.value) previousCapabilityState.clearOverride()
            else previousCapabilityState.enable(),
        )
    }

  private suspend fun resetOverrides(): Result<Unit> {
    val capabilities =
      capabilityUpdatesLock.withLock { capabilityToState.entries }.map { it.key.dataType.noValue() }
    return deviceManager.overrideValues(capabilities)
  }

  override suspend fun reset() =
    runWithStatus(WhsStateManagerStatus.Syncing, MAX_WAIT_TIME_FOR_MODIFICATION) {
      val reset =
        if (!ongoingExercise.value) {
          preset.value = Preset.ALL
          deviceManager.clearContentProvider()
        } else {
          resetOverrides()
        }

      return@runWithStatus reset.also { if (it.isSuccess) resetUiState() }
    }

  override fun dispose() {}

  @TestOnly
  internal suspend fun forceUpdateState() {
    updateState()
  }
}
