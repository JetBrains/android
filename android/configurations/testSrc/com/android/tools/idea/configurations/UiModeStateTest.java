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
package com.android.tools.idea.configurations;

import static com.android.tools.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.configurations.ConfigurationListener.CFG_UI_MODE;
import static org.junit.Assert.assertEquals;

import com.android.resources.NightMode;
import com.android.resources.UiMode;
import com.android.tools.configurations.UiModeState;
import org.junit.Test;

public class UiModeStateTest {

  @Test
  public void testBitwiseParsingIsolatesCorrectFlags() {
    UiModeState state = new UiModeState();

    // 0x00000006 = UI_MODE_TYPE_WATCH
    // 0x00000020 = UI_MODE_NIGHT_YES
    int flags = state.setUiModeFlagValue(0x00000006 | 0x00000020);

    assertEquals(UiMode.WATCH, state.getUiMode());
    assertEquals(NightMode.NIGHT, state.getNightMode());
    assertEquals(CFG_UI_MODE | CFG_NIGHT_MODE, flags); // Verifies BOTH changed

    // Only change night mode back to NOTNIGHT (0x00000010) while keeping WATCH
    int flags2 = state.setUiModeFlagValue(0x00000006 | 0x00000010);

    assertEquals(UiMode.WATCH, state.getUiMode());
    assertEquals(NightMode.NOTNIGHT, state.getNightMode());
    assertEquals(CFG_NIGHT_MODE, flags2); // Verifies ONLY the Night Mode dirty flag was triggered!
  }

  @Test
  public void testCopyFromClonesStateCorrectly() {
    UiModeState original = new UiModeState();
    original.setUiModeFlagValue(0x00000004 | 0x00000020); // TELEVISION + NIGHT

    UiModeState clone = new UiModeState();
    clone.copyFrom(original);

    assertEquals(UiMode.TELEVISION, clone.getUiMode());
    assertEquals(NightMode.NIGHT, clone.getNightMode());
    assertEquals(original.getUiModeFlagValue(), clone.getUiModeFlagValue());
  }
}
