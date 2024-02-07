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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WHS_CAPABILITIES
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.CapabilityState
import com.android.tools.idea.wearwhs.communication.ConnectionLostException
import com.android.tools.idea.wearwhs.communication.WearHealthServicesDeviceManager
import com.android.tools.idea.wearwhs.logger.WearHealthServicesEventLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default polling interval for updating the state manager with values from [WearHealthServicesDeviceManager].
 */
private const val POLLING_INTERVAL_MILLISECONDS: Long = 10000

internal class WearHealthServicesToolWindowStateManagerImpl(
  private val deviceManager: WearHealthServicesDeviceManager,
  private val eventLogger: WearHealthServicesEventLogger = WearHealthServicesEventLogger(),
  @VisibleForTesting private val pollingIntervalMillis: Long = POLLING_INTERVAL_MILLISECONDS)
  : WearHealthServicesToolWindowStateManager, Disposable {

  private val logger: Logger = Logger.getInstance(WearHealthServicesToolWindowStateManagerImpl::class.java)
  private val currentPreset = MutableStateFlow(Preset.ALL)
  private val capabilitiesList = MutableStateFlow(emptyList<WhsCapability>())
  private val capabilityToState = ConcurrentMap<WhsCapability, MutableStateFlow<CapabilityUIState>>()
  private val progress = MutableStateFlow<WhsStateManagerStatus>(WhsStateManagerStatus.Idle)
  private val workerScope = AndroidCoroutineScope(this)

  private val ongoingExercise = MutableStateFlow(false)

  override var serialNumber: String? = null
    set(value) {
      // Only accept non-null values to avoid tool window unbinding completely
      value?.let {
        eventLogger.logBindEmulator()
        deviceManager.setSerialNumber(it)
        field = value
      }
    }

  init {
    workerScope.launch {
      setCapabilities(deviceManager.loadCapabilities())
      while (true) {
        updateState()
        delay(pollingIntervalMillis.milliseconds)
      }
    }
  }

  private suspend fun updateState() {
    if (serialNumber == null) {
      // Panel is not bound to an emulator yet
      return
    }
    try {
      ongoingExercise.emit(deviceManager.loadActiveExercise())
      val currentStates = deviceManager.loadCurrentCapabilityStates()
      currentStates.forEach { (dataType, state) ->
        // Update values only if they're synced through and got changed in the background
        capabilityToState[dataType.toCapability()]?.let { stateFlow ->
          if (stateFlow.value.synced) {
            stateFlow.emit(
              stateFlow.value.copy(
                capabilityState = CapabilityState(state.enabled, state.overrideValue),
                synced = true
              )
            )
          }
        }
      }
    }
    catch (e: ConnectionLostException) {
      logger.warn(e)
    }
  }

  private suspend fun setCapabilities(whsCapabilities: List<WhsCapability>) {
    capabilityToState.clear()
    capabilityToState.putAll(whsCapabilities.associateWith { MutableStateFlow(CapabilityUIState()) })
    setPreset(currentPreset.value)
    capabilitiesList.emit(whsCapabilities)
  }

  override fun getStatus(): StateFlow<WhsStateManagerStatus> = progress.asStateFlow()
  override fun getOngoingExercise(): StateFlow<Boolean> = ongoingExercise.asStateFlow()

  override suspend fun isWhsVersionSupported(): Boolean {
    return try {
      deviceManager.isWhsVersionSupported()
    }
    catch (exception: ConnectionLostException) {
      logger.warn(exception)
      false
    }
  }

  override fun getCapabilitiesList(): StateFlow<List<WhsCapability>> = capabilitiesList.asStateFlow()

  override suspend fun triggerEvent(eventTrigger: EventTrigger) {
    try {
      deviceManager.triggerEvent(eventTrigger)
    }
    catch (exception: ConnectionLostException) {
      progress.emit(WhsStateManagerStatus.ConnectionLost)
    }
  }

  override fun getPreset(): StateFlow<Preset> = currentPreset.asStateFlow()

  override suspend fun setPreset(preset: Preset) {
    when (preset) {
      Preset.STANDARD -> for (capability in capabilityToState.keys) {
        setCapabilityEnabled(capability, capability.isStandardCapability)
      }

      Preset.ALL -> for (capability in capabilityToState.keys) {
        setCapabilityEnabled(capability, true)
      }

      Preset.CUSTOM -> {}
    }
    currentPreset.emit(preset)
  }

  override fun getState(capability: WhsCapability): StateFlow<CapabilityUIState> =
    capabilityToState[capability]?.asStateFlow() ?: throw IllegalArgumentException()

  override suspend fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean) {
    val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
    val newState = stateFlow.value.copy(capabilityState = CapabilityState(enabled, stateFlow.value.capabilityState.overrideValue), synced = false)
    stateFlow.emit(newState)
  }

  override suspend fun setOverrideValue(capability: WhsCapability, value: Float?) {
    val stateFlow = capabilityToState[capability] ?: throw IllegalArgumentException()
    val newState = stateFlow.value.copy(capabilityState = CapabilityState(stateFlow.value.capabilityState.enabled, value), synced = false)
    stateFlow.emit(newState)
  }

  override suspend fun applyChanges() {
    progress.emit(WhsStateManagerStatus.Syncing)
    val capabilityUpdates = capabilityToState.entries.associate { it.key.dataType to it.value.value.capabilityState.enabled }
    val overrideUpdates = capabilityToState.entries.associate { it.key.dataType to it.value.value.capabilityState.overrideValue }
    try {
      deviceManager.setCapabilities(capabilityUpdates)
      deviceManager.overrideValues(overrideUpdates)
    }
    catch (exception: ConnectionLostException) {
      logger.warn(exception)
      eventLogger.logApplyChangesFailure()
      progress.emit(WhsStateManagerStatus.ConnectionLost)
      return
    }
    capabilityToState.entries.forEach {
      val stateFlow = it.value
      val state = stateFlow.value
      stateFlow.emit(state.copy(synced = true))
    }
    eventLogger.logApplyChangesSuccess()
    progress.emit(WhsStateManagerStatus.Idle)
  }

  override suspend fun reset() {
    setPreset(Preset.ALL)
    for (entry in capabilityToState.keys) {
      setOverrideValue(entry, null)
    }
    try {
      deviceManager.clearContentProvider()
    } catch (exception: ConnectionLostException) {
      logger.warn(exception)
      progress.emit(WhsStateManagerStatus.ConnectionLost)
    }
  }

  override fun dispose() { // Clear all callbacks to avoid memory leaks
    capabilityToState.clear()
  }

  @TestOnly
  internal suspend fun forceUpdateState() {
    updateState()
  }
}

private fun WhsDataType.toCapability(): WhsCapability {
  return WHS_CAPABILITIES.single {
    it.dataType == this
  }
}
