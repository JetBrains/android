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
package com.android.tools.idea.configurations

import com.android.resources.NightMode
import com.android.resources.UiMode
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.UiModeState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiModeStateTest {
  @Test
  fun testBitwiseParsingIsolatesCorrectFlags() {
    val state = UiModeState()

    val flags = state.setUiModeFlagValue(UiModeState.UI_MODE_TYPE_WATCH or UiModeState.UI_MODE_NIGHT_YES)

    assertThat(state.uiMode).isEqualTo(UiMode.WATCH)
    assertThat(state.nightMode).isEqualTo(NightMode.NIGHT)
    assertThat(flags).isEqualTo(ConfigurationListener.CFG_UI_MODE or ConfigurationListener.CFG_NIGHT_MODE) // Verifies BOTH changed

    // Only change night mode back to NOTNIGHT while keeping WATCH
    val flags2 = state.setUiModeFlagValue(UiModeState.UI_MODE_TYPE_WATCH or UiModeState.UI_MODE_NIGHT_NO)

    assertThat(state.uiMode).isEqualTo(UiMode.WATCH)
    assertThat(state.nightMode).isEqualTo(NightMode.NOTNIGHT)
    assertThat(flags2).isEqualTo(ConfigurationListener.CFG_NIGHT_MODE) // Verifies ONLY the Night Mode dirty flag was triggered!
  }

  @Test
  fun testCopyFromClonesStateCorrectly() {
    val original = UiModeState()
    original.setUiModeFlagValue(UiModeState.UI_MODE_TYPE_TELEVISION or UiModeState.UI_MODE_NIGHT_YES) // TELEVISION + NIGHT

    val clone = UiModeState()
    clone.copyFrom(original)

    assertThat(clone.uiMode).isEqualTo(UiMode.TELEVISION)
    assertThat(clone.nightMode).isEqualTo(NightMode.NIGHT)
    assertThat(clone.uiModeFlagValue).isEqualTo(original.uiModeFlagValue)
  }

  @Test
  fun testEnumSettersUpdateBitmaskDirectly() {
    val state = UiModeState()

    // 0 represents the uninitialized/undefined state in Android's bitmask, which
    // downstream consumers (like the Layout Editor) rely on. Even though the default
    // enum is UiMode.NORMAL (1), we strictly preserve 0 until explicitly mutated.
    assertThat(state.uiModeFlagValue).isEqualTo(0)

    val uiFlags = state.setUiMode(UiMode.CAR)
    val nightFlags = state.setNightMode(NightMode.NIGHT)

    assertThat(uiFlags).isEqualTo(ConfigurationListener.CFG_UI_MODE)
    assertThat(nightFlags).isEqualTo(ConfigurationListener.CFG_NIGHT_MODE)

    val expectedRawInteger = UiModeState.UI_MODE_TYPE_CAR or UiModeState.UI_MODE_NIGHT_YES
    assertThat(state.uiModeFlagValue).isEqualTo(expectedRawInteger)
  }
}
