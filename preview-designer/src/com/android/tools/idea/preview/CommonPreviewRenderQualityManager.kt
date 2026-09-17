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
package com.android.tools.idea.preview

import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager

/**
 * A [RenderQualityManager] tailored for Previews that have a PreviewModeManager.
 *
 * This manager delegates the actual render quality handling to a default manager, but disables quality changes when [PreviewMode] is
 * [PreviewMode.Interactive].
 *
 * @param previewModeManager Provides the current [PreviewMode] to determine if quality changes are allowed in the current [PreviewMode].
 * @param delegateRenderQualityManager The default [RenderQualityManager] delegate that handles the underlying logic.
 */
class CommonPreviewRenderQualityManager(
  private val previewModeManager: PreviewModeManager,
  private val delegateRenderQualityManager: RenderQualityManager,
) : RenderQualityManager by delegateRenderQualityManager {
  override fun needsQualityChange(sceneManager: LayoutlibSceneManager): Boolean {
    // Applying quality changes breaks the navigation through the Previews when [PreviewMode] is [Interactive].
    //
    // For example, actions like zooming or navigating within the interactive preview might trigger a refresh by the render quality manager.
    // This refresh can lead to losing the current navigation stack, breaking the flow of interaction, and interfering with features like
    // navigation3 predictive back.
    return previewModeManager.mode.value !is PreviewMode.Interactive && delegateRenderQualityManager.needsQualityChange(sceneManager)
  }
}
