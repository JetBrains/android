/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.surface.SceneView
import com.intellij.openapi.Disposable

/**
 * Navigation helper for when the surface is clicked.
 */
interface NavigationHandler : Disposable {
  /**
   * Triggered when preview in the design surface is clicked, returns true if the navigation was handled by this handler.
   * This method receives the x and y coordinates of the click. You will usually only need the coordinates if your navigation can
   * be different within a same [SceneComponent].
   *
   * @param sceneView [SceneView] for which the navigation request is being issued
   * @param x X coordinate within the [SceneView] where the click action was initiated
   * @param y y coordinate within the [SceneView] where the click action was initiated
   * @param requestFocus true if the navigation should focus the editor
   */
  suspend fun handleNavigate(
    sceneView: SceneView,
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
    requestFocus: Boolean
  ): Boolean

  /**
   *  Triggered when need to perform a navigation associated to the [sceneView] as a whole,
   *  but not any of its components. This could happen for example when the [sceneView]'s
   *  name/title shown in the design surface is clicked.
   *  Returns true if the navigation was handled by this handler.
   *
   *  @param sceneView [SceneView] for which the navigation request is being issued
   *  @param requestFocus true if the navigation should focus the editor
   */
  suspend fun handleNavigate(sceneView: SceneView, requestFocus: Boolean): Boolean
}