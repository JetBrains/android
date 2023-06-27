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
package com.android.tools.idea.compose.preview.lite

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.android.uipreview.AndroidEditorSettings.GlobalState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposePreviewLiteModeManagerTest {

  @get:Rule val liteModeFlagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_LITE_MODE)
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var settings: GlobalState

  @Before
  fun setUp() {
    settings = AndroidEditorSettings.getInstance().globalState
  }

  @Test
  fun liteModeIsOnlyEnabledIfFlagIsEnabled() {
    StudioFlags.COMPOSE_PREVIEW_LITE_MODE.override(false)
    assertFalse(ComposePreviewLiteModeManager.isLiteModeEnabled)

    settings.isComposePreviewLiteModeEnabled = true
    // Even with the lite mode enabled in the settings panel, we shouldn't be in
    // lite mode if the flag is disabled.
    assertFalse(ComposePreviewLiteModeManager.isLiteModeEnabled)
  }

  @Test
  fun liteModeIsControlledViaSettingsIfFlagIsEnabled() {
    StudioFlags.COMPOSE_PREVIEW_LITE_MODE.override(true)
    settings.isComposePreviewLiteModeEnabled = false
    assertFalse(ComposePreviewLiteModeManager.isLiteModeEnabled)

    settings.isComposePreviewLiteModeEnabled = true
    assertTrue(ComposePreviewLiteModeManager.isLiteModeEnabled)
  }

  @Test
  fun liteModeIsEnabledIfEssentialsModeIsEnabled() {
    StudioFlags.COMPOSE_PREVIEW_LITE_MODE.override(true)
    try {
      settings.isComposePreviewLiteModeEnabled = false
      assertFalse(ComposePreviewLiteModeManager.isLiteModeEnabled)

      // Enable Android Studio essentials mode. Note that preview lite mode is still disabled in
      // settings.
      EssentialsMode.setEnabled(true, projectRule.project)
      assertTrue(ComposePreviewLiteModeManager.isLiteModeEnabled)
    } finally {
      EssentialsMode.setEnabled(false, projectRule.project)
    }
  }
}
