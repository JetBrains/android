/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wear.preview.essentials

import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTilePreviewEssentialsModeManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var settings: AndroidEditorSettings.GlobalState

  @Before
  fun setUp() {
    settings = AndroidEditorSettings.getInstance().globalState
  }

  @After
  fun cleanup() {
    EssentialsMode.setEnabled(false, projectRule.project)
  }

  @Test
  fun essentialsModeIsControlledViaSettings() {
    settings.isPreviewEssentialsModeEnabled = false
    assertFalse(WearTilePreviewEssentialsModeManager.isEssentialsModeEnabled)

    settings.isPreviewEssentialsModeEnabled = true
    assertTrue(WearTilePreviewEssentialsModeManager.isEssentialsModeEnabled)
  }

  @Test
  fun previewEssentialsModeIsEnabledIfStudioEssentialsModeIsEnabled() {
    settings.isPreviewEssentialsModeEnabled = false
    assertFalse(WearTilePreviewEssentialsModeManager.isEssentialsModeEnabled)

    // Enable Android Studio essentials mode. Note that preview essentials mode is still disabled
    // in settings.
    EssentialsMode.setEnabled(true, projectRule.project)
    assertTrue(WearTilePreviewEssentialsModeManager.isEssentialsModeEnabled)
  }
}
