/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations

import com.android.resources.NightMode
import com.android.resources.UiMode
import com.google.common.annotations.VisibleForTesting

/**
 * Encapsulates the logic for Android UI Mode and Night Mode state.
 */
class UiModeState {
  var uiMode: UiMode = UiMode.NORMAL
    private set

  var nightMode: NightMode = NightMode.NOTNIGHT
    private set

  /** The raw integer flag value representing the UI and Night mode combinations. */
  var uiModeFlagValue: Int = 0
    private set

  /**
   * Sets the night mode property.
   *
   * @param night The new [NightMode] to apply.
   * @return A bitmask containing [ConfigurationListener.CFG_NIGHT_MODE] if the state changed, or 0 if the state remained the same.
   */
  fun setNightMode(night: NightMode): Int {
    if (this.nightMode != night) {
      // Route through setUiModeFlagValue to ensure strict bitwise change detection
      val newFlags = (this.uiModeFlagValue and UI_MODE_TYPE_MASK) or night.flagValue
      return setUiModeFlagValue(newFlags)
    }
    return 0
  }

  /**
   * Sets the UI mode property.
   *
   * @param uiMode The new [UiMode] to apply.
   * @return A bitmask containing [ConfigurationListener.CFG_UI_MODE] if the state changed, or 0 if the state remained the same.
   */
  fun setUiMode(uiMode: UiMode): Int {
    if (this.uiMode != uiMode) {
      // Route through setUiModeFlagValue to ensure strict bitwise change detection
      val newFlags = (this.uiModeFlagValue and UI_MODE_NIGHT_MASK) or uiMode.flagValue
      return setUiModeFlagValue(newFlags)
    }
    return 0
  }

  /**
   * Sets the raw bitwise flag value for the UI mode, which may update both the [uiMode] and [nightMode] properties simultaneously.
   *
   * @param flags The raw integer flag value.
   * @return A bitmask of [ConfigurationListener] dirty flags representing exactly which properties were modified. Returns 0 if no changes
   *   occurred.
   */
  fun setUiModeFlagValue(flags: Int): Int {
    val modifiedElements = this.uiModeFlagValue xor flags
    this.uiModeFlagValue = flags
    var updatedFlags = 0

    if ((modifiedElements and UI_MODE_NIGHT_MASK) != 0) {
      this.nightMode = if ((flags and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) NightMode.NIGHT else NightMode.NOTNIGHT
      updatedFlags = updatedFlags or ConfigurationListener.CFG_NIGHT_MODE
    }

    if ((modifiedElements and UI_MODE_TYPE_MASK) != 0) {
      this.uiMode = uiModeFromFlag(flags)
      updatedFlags = updatedFlags or ConfigurationListener.CFG_UI_MODE
    }

    return updatedFlags
  }

  fun copyFrom(other: UiModeState) {
    this.uiMode = other.uiMode
    this.nightMode = other.nightMode
    this.uiModeFlagValue = other.uiModeFlagValue
  }

  companion object {
    const val UI_MODE_TYPE_MASK = 0x0000000f
    const val UI_MODE_NIGHT_YES = 0x00000020
    const val UI_MODE_NIGHT_NO = 0x00000010

    @VisibleForTesting const val UI_MODE_TYPE_APPLIANCE = 0x00000005
    @VisibleForTesting const val UI_MODE_TYPE_CAR = 0x00000003
    @VisibleForTesting const val UI_MODE_TYPE_DESK = 0x00000002
    @VisibleForTesting const val UI_MODE_TYPE_NORMAL = 0x00000001
    @VisibleForTesting const val UI_MODE_TYPE_TELEVISION = 0x00000004
    @VisibleForTesting const val UI_MODE_TYPE_VR_HEADSET = 0x00000007
    @VisibleForTesting const val UI_MODE_TYPE_WATCH = 0x00000006

    private const val UI_MODE_NIGHT_MASK = 0x00000030

    private val UiMode.flagValue: Int
      get() =
        when (this) {
          UiMode.NORMAL -> UI_MODE_TYPE_NORMAL
          UiMode.DESK -> UI_MODE_TYPE_DESK
          UiMode.WATCH -> UI_MODE_TYPE_WATCH
          UiMode.TELEVISION -> UI_MODE_TYPE_TELEVISION
          UiMode.APPLIANCE -> UI_MODE_TYPE_APPLIANCE
          UiMode.CAR -> UI_MODE_TYPE_CAR
          UiMode.VR_HEADSET -> UI_MODE_TYPE_VR_HEADSET
          else -> UI_MODE_TYPE_NORMAL
        }

    private val NightMode.flagValue: Int
      get() = if (this == NightMode.NIGHT) UI_MODE_NIGHT_YES else UI_MODE_NIGHT_NO

    private fun uiModeFromFlag(flag: Int): UiMode {
      return when (flag and UI_MODE_TYPE_MASK) {
        UI_MODE_TYPE_APPLIANCE -> UiMode.APPLIANCE
        UI_MODE_TYPE_CAR -> UiMode.CAR
        UI_MODE_TYPE_TELEVISION -> UiMode.TELEVISION
        UI_MODE_TYPE_WATCH -> UiMode.WATCH
        UI_MODE_TYPE_DESK -> UiMode.DESK
        UI_MODE_TYPE_VR_HEADSET -> UiMode.VR_HEADSET
        else -> UiMode.NORMAL
      }
    }
  }
}
