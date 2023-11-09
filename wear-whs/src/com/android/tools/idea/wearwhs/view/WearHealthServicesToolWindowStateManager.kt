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
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
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
  fun setPreset(preset: Preset)

  /**
   * State flow for the enabled state of the capability.
   */
  fun getCapabilityEnabled(capability: WhsCapability): StateFlow<Boolean>

  /**
   * Sets the current capability enabled state.
   */
  fun setCapabilityEnabled(capability: WhsCapability, enabled: Boolean)

  /**
   * State flow for the overridden sensor value of the capability. Null value here means there's no
   * overridden value and WHS is using the default value.
   */
  fun getOverrideValue(capability: WhsCapability): StateFlow<Float?>

  /**
   * Sets the overridden sensor value for the given capability. Null value here means there's no
   * overridden value and WHS should use the default value.
   */
  fun setOverrideValue(capability: WhsCapability, value: Float?)


  /**
   * Applies the changes on current device.
   */
  fun applyChanges()

  /**
   * Resets the state to the defaults, in this case, to selected preset.
   */
  fun reset()
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
