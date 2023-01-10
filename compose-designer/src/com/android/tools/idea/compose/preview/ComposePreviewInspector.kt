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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.navigation.ComposeViewInfo
import com.android.tools.idea.compose.preview.navigation.findDeepestHits
import com.intellij.openapi.ui.popup.JBPopup

/**
 * Class to inspect the component in compose preview. [onInspected] is called when inspection is
 * done.
 */
class ComposePreviewInspector(
  private val surface: DesignSurface<*>,
  private val composeViewInfoProvider: (SceneView) -> List<ComposeViewInfo>,
  private val onInspected: (List<ComposeViewInfo>, Int, Int) -> Unit
) {

  private var currentViewInfo: ComposeViewInfo? = null
  private var currentTooltipPopup: JBPopup? = null

  /**
   * Show or hide the tooltips and its content according to the given mouse position ([x], [y]) in
   * the Swing coordinate.
   */
  fun inspect(@SwingCoordinate x: Int, @SwingCoordinate y: Int) {
    val inspectionEnabled =
      COMPOSE_PREVIEW_MANAGER.getData(surface)?.isInspectionTooltipEnabled ?: false
    if (!inspectionEnabled) {
      currentViewInfo = null
      currentTooltipPopup?.cancel()
      currentTooltipPopup = null
      return
    }

    val viewInfo = findComposeViewInfo(x, y)
    onInspected(viewInfo, x, y)
  }

  /** Find the [ComposeViewInfo] for the given position in Swing coordinate. */
  private fun findComposeViewInfo(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int
  ): List<ComposeViewInfo> {
    val sceneView = surface.getSceneViewAt(x, y) ?: return emptyList()
    @AndroidCoordinate val xPx = Coordinates.getAndroidX(sceneView, x)
    @AndroidCoordinate val yPx = Coordinates.getAndroidY(sceneView, y)
    for (composeViewInfo in composeViewInfoProvider(sceneView)) {
      val hits = composeViewInfo.findDeepestHits(xPx, yPx)
      if (hits.isNotEmpty()) {
        return hits.toList()
      }
    }
    return emptyList()
  }
}
