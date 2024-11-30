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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.actions.ZoomToSelectionAction
import com.android.tools.idea.compose.preview.util.getDeepestViewInfos
import com.intellij.openapi.diagnostic.Logger
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

fun zoomTargetProvider(sceneView: SceneView, x: Int, y: Int, logger: Logger): Rectangle {
  val deepestViewInfos = sceneView.getDeepestViewInfos(x, y, logger)
  if (deepestViewInfos.isNullOrEmpty()) {
    // This is expected for example when the Preview contains showSystemUi=true
    // and the "systemUi" is where the right-click happens.
    logger.info("Could not find the view to zoom to, zooming to the whole Preview.")
    return Rectangle(Point(0, 0), sceneView.scaledContentSize)
  }
  if (deepestViewInfos!!.size > 1) {
    logger.warn(
      "Expected 1 view to zoom to, but found ${deepestViewInfos.size}, choosing the last one."
    )
  }
  return findZoomTarget(deepestViewInfos.last(), sceneView)
}

fun findZoomTarget(deepestViewInfo: ComposeViewInfo, sceneView: SceneView): Rectangle =
  deepestViewInfo.bounds.let {
    val topLeftCorner =
      Point(
        Coordinates.getSwingDimension(sceneView, it.left),
        Coordinates.getSwingDimension(sceneView, it.top),
      )
    val size =
      Dimension(
        Coordinates.getSwingDimension(sceneView, it.width),
        Coordinates.getSwingDimension(sceneView, it.height),
      )
    return java.awt.Rectangle(topLeftCorner, size)
  }
