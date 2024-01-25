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

import com.android.tools.idea.wearwhs.BUNDLE_NAME
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.communication.CapabilityState
import com.android.tools.idea.wearwhs.view.Preset.ALL
import com.android.tools.idea.wearwhs.view.Preset.CUSTOM
import com.android.tools.idea.wearwhs.view.Preset.STANDARD
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.PropertyKey

/***
 * Interface for managing the state of the Wear Health Services tool window.
 */
internal interface WearHealthServicesToolWindowStateManager {
  /**
   * State flow for all capabilities of WHS, used to display a list of capabilities.
   */
  fun getCapabilitiesList(): StateFlow<List<WhsCapability>>

  /**
   * State flow for the current preset.
   */
  fun getPreset(): StateFlow<Preset>

  /**
   * Sets the current preset.
   */
  suspend fun setPreset(preset: Preset)

  /**
   * Sets the current capability enabled state.
   */
  suspend fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean)

  /**
   * Sets the overridden sensor value for the given capability. Null value here means there's no
   * overridden value and WHS should use the default value.
   */
  suspend  fun setOverrideValue(capability: WhsCapability, value: Float?)

  fun getState(capability: WhsCapability): StateFlow<CapabilityUIState>

    /**
   * Applies the changes on current device.
   */
  suspend fun applyChanges()

  /**
   * Resets the state to the defaults, in this case, to selected preset.
   */
  suspend fun reset()

  /**
   * State flow for the ongoing status updates, it's an instance of [WhsStateManagerStatus]
   * which could be the syncing state, containing the capability that is currently being synced
   * across to the device, an error state, or an idle state.
   */
  fun getStatus(): StateFlow<WhsStateManagerStatus?>

  /**
   * Returns if the current WHS version is supported or not, so an error can be displayed by
   * the UI.
   */
  suspend fun isWhsVersionSupported(): Boolean

  /**
   * State flow for the ongoing exercise status, emits a single boolean, true if there's an
   * ongoing exercise on the device, false otherwise.
   */
  fun getOngoingExercise(): StateFlow<Boolean>

  /**
   * Triggers given event on the device.
   */
  suspend fun triggerEvent(eventTrigger: EventTrigger)

  /**
   * Used to get/set the serial number of the currently running emulator.
   */
  var serialNumber: String?
}

/**
 * Presets for the Wear Health Services capability settings.
 *
 * [STANDARD] corresponds to a basic set of sensors which are likely to be supported by most
 * devices, e.g. heart rate, location. [ALL] includes less common capabilities as well, such as
 * elevation gain. [CUSTOM] allows the user to select which capabilities to enable.
 */
internal enum class Preset(@PropertyKey(resourceBundle = BUNDLE_NAME) val labelKey: String) {
  STANDARD("wear.whs.panel.capabilities.standard"),
  ALL("wear.whs.panel.capabilities.all"),
  CUSTOM( "wear.whs.panel.capabilities.custom");

  override fun toString() = message(labelKey)
}

/**
 * Progress state of the Wear Health Services tool window, tells the user what is currently
 * being synced across to the device, if there's an error, or if the state is idle.
 */
internal sealed class WhsStateManagerStatus {
  object Ready : WhsStateManagerStatus()
  object Syncing : WhsStateManagerStatus()
  object ConnectionLost : WhsStateManagerStatus()
  object Idle : WhsStateManagerStatus()
}

/**
 * Data class representing current state of a WHS capability.
 */
internal data class CapabilityUIState(
  val synced: Boolean = false,
  val capabilityState: CapabilityState = CapabilityState(false, null)
)
