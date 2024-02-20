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
package com.android.tools.idea.naveditor.surface

import com.android.annotations.concurrency.UiThread
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceZoomController
import com.android.tools.idea.common.surface.SCALING_THRESHOLD
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.ScenesOwner
import com.android.tools.idea.common.surface.ZoomChange
import com.android.tools.idea.common.surface.ZoomListener
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.naveditor.scene.NavSceneManager
import java.awt.Dimension
import java.awt.Point
import kotlin.math.abs
import kotlin.math.min

/**
 * [ZoomController] for [NavDesignSurface]
 *
 * @param surfaceSize The size of [NavDesignSurface].
 * @param viewPort The [DesignSurfaceViewport] of [NavDesignSurface] where the zoom is applied.
 * @param sceneManager [SceneManager] for the navigation editor.
 * @param sceneViewDimensionProvider Provides the Dimension of a [SceneView] of [NavDesignSurface].
 * @param analyticsManager Manager to track analytics in [DesignSurface]s
 * @param navSelectionModel The components contained in [NavDesignSurface]
 * @param scenesOwner The of the [Scene]s of the current view. In this is [NavDesignSurface] itself,
 *   such value is used to find the focused scene within the surface.
 */
class NavDesignSurfaceZoomController(
  private val surfaceSize: Dimension,
  private val viewPort: DesignSurfaceViewport,
  private val sceneManager: () -> NavSceneManager?,
  private val sceneViewDimensionProvider: (SceneView) -> Dimension,
  analyticsManager: DesignerAnalyticsManager?,
  navSelectionModel: SelectionModel?,
  scenesOwner: ScenesOwner,
) : DesignSurfaceZoomController(analyticsManager, navSelectionModel, scenesOwner) {

  override val minScale: Double
    get() = if (isEmpty()) 1.0 else 0.1

  override val maxScale: Double
    get() = if (isEmpty()) 1.0 else 3.0

  private var zoomListener: ZoomListener? = null

  override fun getFitScale(): Double {
    val contentSize = Dimension()
    val view: SceneView? = getFocusedSceneView()
    if (view != null) {
      contentSize.size = sceneViewDimensionProvider(view)
    } else {
      contentSize.setSize(0, 0)
    }

    val scale: Double = getFitContentIntoWindowScale(contentSize)
    return min(scale, 1.0)
  }

  override fun canZoomToFit(): Boolean {
    if (isEmpty()) {
      return false
    }

    val fitScale = getFitScale()
    val scale = scale

    return abs(fitScale - scale) > SCALING_THRESHOLD
  }

  private fun isEmpty(): Boolean {
    val sceneManager: NavSceneManager? = sceneManager()
    return sceneManager == null || sceneManager.isEmpty
  }

  private fun getFitContentIntoWindowScale(contentSize: Dimension): Double {
    val availableWidth = viewPort.extentSize.width
    val availableHeight = viewPort.extentSize.height

    val scaleX: Double =
      if (surfaceSize.width == 0) {
        1.0
      } else {
        availableWidth.toDouble() / contentSize.width
      }

    val scaleY: Double =
      if (surfaceSize.size.height == 0) {
        1.0
      } else {
        availableHeight.toDouble() / contentSize.height
      }
    return minOf(scaleX, scaleY, maxZoomToFitScale)
  }

  override fun setScale(scale: Double, x: Int, y: Int): Boolean {
    var newX = x
    var newY = y
    val view: SceneView = getFocusedSceneView() ?: return false

    val oldViewPosition: Point = viewPort.viewPosition
    if (newX < 0 || newY < 0) {
      newX = oldViewPosition.x + viewPort.viewportComponent.width / 2
      newY = oldViewPosition.y + viewPort.viewportComponent.height / 2
    }

    @AndroidDpCoordinate val androidX = Coordinates.getAndroidXDip(view, newX)
    @AndroidDpCoordinate val androidY = Coordinates.getAndroidYDip(view, newY)

    val ret = super.setScale(scale, newX, newY)

    @SwingCoordinate val shiftedX = Coordinates.getSwingXDip(view, androidX)
    @SwingCoordinate val shiftedY = Coordinates.getSwingYDip(view, androidY)
    viewPort.viewPosition =
      Point(oldViewPosition.x + shiftedX - newX, oldViewPosition.y + shiftedY - newY)

    return ret
  }

  fun setZoomListener(listener: ZoomListener) {
    this.zoomListener = listener
  }

  @UiThread
  override fun zoom(type: ZoomType, @SwingCoordinate x: Int, @SwingCoordinate y: Int): Boolean {
    val scaled = super.zoom(type, x, y)
    zoomListener?.onZoomChange(update = ZoomChange(zoomType = type, hasScaleChanged = scaled))
    return scaled
  }
}
