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

import com.android.sdklib.AndroidCoordinate
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneContext

/**
 * The [SceneContext] based on a [SceneView].
 *
 * TODO: b/140160277 For historical reason we put the Coordinate translation in [SceneContext]
 *   instead of using [SceneView] directly. Maybe we can remove [SceneContext] and just use
 *   [SceneView] only.
 */
class SceneViewTransform(private val sceneView: SceneView) : SceneContext() {
  override fun getColorSet() = sceneView.colorSet

  override fun getSurface() = sceneView.surface

  override fun getScale() = sceneView.scale

  @SwingCoordinate
  override fun getSwingXDip(@AndroidDpCoordinate x: Float) =
    Coordinates.getSwingX(sceneView, Coordinates.dpToPx(sceneView, x))

  @SwingCoordinate
  override fun getSwingYDip(@AndroidDpCoordinate y: Float) =
    Coordinates.getSwingY(sceneView, Coordinates.dpToPx(sceneView, y))

  @SwingCoordinate
  override fun getSwingX(@AndroidCoordinate x: Int) = Coordinates.getSwingX(sceneView, x)

  @SwingCoordinate
  override fun getSwingY(@AndroidCoordinate y: Int) = Coordinates.getSwingY(sceneView, y)

  @AndroidDpCoordinate
  override fun pxToDp(@AndroidCoordinate px: Int) = Coordinates.pxToDp(sceneView, px).toFloat()

  @SwingCoordinate
  override fun getSwingDimensionDip(@AndroidDpCoordinate dim: Float) =
    Coordinates.getSwingDimension(sceneView, Coordinates.dpToPx(sceneView, dim))

  @SwingCoordinate
  override fun getSwingDimension(@AndroidCoordinate dim: Int) =
    Coordinates.getSwingDimension(sceneView, dim)

  override fun repaint() {
    surface.needsRepaint()
  }
}
