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
package com.android.tools.idea.common.surface

import com.android.tools.idea.common.model.scaleBy
import java.awt.Dimension

data class LayoutData(val scale: Double, val x: Int, val y: Int, val scaledSize: Dimension) {

  // Used to avoid extra allocations in isValidFor calls
  private val cachedDimension = Dimension()

  /**
   * Returns whether this [LayoutData] is still valid (has not changed) for the given [SceneView]
   */
  fun isValidFor(sceneView: SceneView): Boolean =
    scale == sceneView.scale &&
      x == sceneView.x &&
      y == sceneView.y &&
      scaledSize == sceneView.getContentSize(cachedDimension).scaleBy(sceneView.scale)

  companion object {
    fun fromSceneView(sceneView: SceneView): LayoutData =
      LayoutData(
        sceneView.scale,
        sceneView.x,
        sceneView.y,
        sceneView.getContentSize(null).scaleBy(sceneView.scale),
      )
  }
}
