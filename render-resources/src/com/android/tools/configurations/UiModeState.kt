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

/**
 * Encapsulates the bitwise logic for parsing Android UI Mode and Night Mode flags.
 */
class UiModeState {
  var uiMode: UiMode = UiMode.NORMAL
    private set
  var nightMode: NightMode = NightMode.NOTNIGHT
    private set
  var uiModeFlagValue: Int = 0
    private set

  /**
   * Sets the night mode property and calculates the corresponding raw flag value.
   *
   * @param night The new [NightMode] to apply.
   * @return A bitmask containing [ConfigurationListener.CFG_NIGHT_MODE] if the state changed,
   *         or 0 if the state remained the same. This is used by the Configuration facade
   *         to notify listeners of specific changes.
   */
  fun setNightMode(night: NightMode): Int {
    if (this.nightMode != night) {
      return setUiModeFlagValue(
        (this.uiModeFlagValue and UI_MODE_TYPE_MASK) or
          (if (night == NightMode.NIGHT) UI_MODE_NIGHT_YES else UI_MODE_NIGHT_NO)
      )
    }
    return 0
  }

  /**
   * Sets the UI mode property and calculates the corresponding raw flag value.
   *
   * @param uiMode The new [UiMode] to apply.
   * @return A bitmask containing [ConfigurationListener.CFG_UI_MODE] if the state changed,
   *         or 0 if the state remained the same. This is used by the Configuration facade
   *         to notify listeners of specific changes.
   */
  fun setUiMode(uiMode: UiMode): Int {
    if (this.uiMode != uiMode) {
      var newUiTypeFlags = 0
      when (uiMode) {
        UiMode.NORMAL -> newUiTypeFlags = UI_MODE_TYPE_NORMAL
        UiMode.DESK -> newUiTypeFlags = UI_MODE_TYPE_DESK
        UiMode.WATCH -> newUiTypeFlags = UI_MODE_TYPE_WATCH
        UiMode.TELEVISION -> newUiTypeFlags = UI_MODE_TYPE_TELEVISION
        UiMode.APPLIANCE -> newUiTypeFlags = UI_MODE_TYPE_APPLIANCE
        UiMode.CAR -> newUiTypeFlags = UI_MODE_TYPE_CAR
        UiMode.VR_HEADSET -> newUiTypeFlags = UI_MODE_TYPE_VR_HEADSET
      }
      return setUiModeFlagValue((this.uiModeFlagValue and UI_MODE_NIGHT_MASK) or newUiTypeFlags)
    }
    return 0
  }

  /**
   * Sets the raw bitwise flag value for the UI mode, which may update both the
   * [uiMode] and [nightMode] properties simultaneously.
   *
   * @param uiMode The raw integer flag value.
   * @return A bitmask of [ConfigurationListener] dirty flags (e.g.,[ConfigurationListener.CFG_UI_MODE]
   *         and/or[ConfigurationListener.CFG_NIGHT_MODE]) representing exactly which properties
   *         were modified. Returns 0 if no changes occurred.
   */
  fun setUiModeFlagValue(uiMode: Int): Int {
    val modifiedElements = this.uiModeFlagValue xor uiMode
    this.uiModeFlagValue = uiMode
    var updatedFlags = 0

    if ((modifiedElements and UI_MODE_NIGHT_MASK) != 0) {
      this.nightMode = if ((uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) NightMode.NIGHT else NightMode.NOTNIGHT
      updatedFlags = updatedFlags or ConfigurationListener.CFG_NIGHT_MODE
    }

    if ((modifiedElements and UI_MODE_TYPE_MASK) != 0) {
      when (uiMode and UI_MODE_TYPE_MASK) {
        UI_MODE_TYPE_APPLIANCE -> this.uiMode = UiMode.APPLIANCE
        UI_MODE_TYPE_CAR -> this.uiMode = UiMode.CAR
        UI_MODE_TYPE_TELEVISION -> this.uiMode = UiMode.TELEVISION
        UI_MODE_TYPE_WATCH -> this.uiMode = UiMode.WATCH
        UI_MODE_TYPE_DESK -> this.uiMode = UiMode.DESK
        UI_MODE_TYPE_VR_HEADSET -> this.uiMode = UiMode.VR_HEADSET
        else -> this.uiMode = UiMode.NORMAL
      }
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
    const val UI_MODE_TYPE_MASK: Int = 0x0000000f
    const val UI_MODE_NIGHT_YES: Int = 0x00000020
    const val UI_MODE_NIGHT_NO: Int = 0x00000010
    private const val UI_MODE_TYPE_APPLIANCE = 0x00000005
    private const val UI_MODE_TYPE_CAR = 0x00000003
    private const val UI_MODE_TYPE_DESK = 0x00000002
    private const val UI_MODE_TYPE_NORMAL = 0x00000001
    private const val UI_MODE_TYPE_TELEVISION = 0x00000004
    private const val UI_MODE_TYPE_VR_HEADSET = 0x00000007
    private const val UI_MODE_TYPE_WATCH = 0x00000006

    private const val UI_MODE_NIGHT_MASK = 0x00000030
  }
}