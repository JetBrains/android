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
package com.android.tools.idea.compose.preview.essentials

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

class ComposePreviewEssentialsModeManagerTest {

  @get:Rule val essentialsModeFlagRule = FlagRule(StudioFlags.PREVIEW_ESSENTIALS_MODE)
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var settings: GlobalState

  @Before
  fun setUp() {
    settings = AndroidEditorSettings.getInstance().globalState
  }

  @Test
  fun essentialsModeIsOnlyEnabledIfFlagIsEnabled() {
    StudioFlags.PREVIEW_ESSENTIALS_MODE.override(false)
    assertFalse(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)

    settings.isPreviewEssentialsModeEnabled = true
    // Even with the essentials mode enabled in the settings panel, we shouldn't be in
    // essentials mode if the flag is disabled.
    assertFalse(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)
  }

  @Test
  fun essentialsModeIsControlledViaSettingsIfFlagIsEnabled() {
    StudioFlags.PREVIEW_ESSENTIALS_MODE.override(true)
    settings.isPreviewEssentialsModeEnabled = false
    assertFalse(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)

    settings.isPreviewEssentialsModeEnabled = true
    assertTrue(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)
  }

  @Test
  fun previewEssentialsModeIsEnabledIfStudioEssentialsModeIsEnabled() {
    StudioFlags.PREVIEW_ESSENTIALS_MODE.override(true)
    try {
      settings.isPreviewEssentialsModeEnabled = false
      assertFalse(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)

      // Enable Android Studio essentials mode. Note that preview essentials mode is still disabled
      // in settings.
      EssentialsMode.setEnabled(true, projectRule.project)
      assertTrue(ComposePreviewEssentialsModeManager.isEssentialsModeEnabled)
    } finally {
      EssentialsMode.setEnabled(false, projectRule.project)
    }
  }
}
