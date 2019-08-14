/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.common.surface.SceneView
import java.awt.Dimension
import java.awt.Rectangle

/**
 * Interface used to layout and measure the size of [SceneView]s in [com.android.tools.idea.common.surface.DesignSurface].
 */
interface SurfaceLayoutManager {

  /**
   * Get the total content size of [SceneView]s when available display size is [availableWidth] x [availableHeight].
   * The size is for containing the raw size of [SceneView]s. It doesn't consider the zoom level of the given [SceneView]s.
   *
   * @param sceneViews      all [SceneView]s in the surface.
   * @param availableWidth  the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param dimension       used to store the result size. The new [Dimension] instance is created if the given instance is null.
   *
   * @see [getRequiredSize]
   */
  fun getPreferredSize(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?): Dimension

  /**
   * Get the total content size of the given [SceneView]s when available display size is [availableWidth] x [availableHeight].
   * Not like [getPreferredSize], this considers the current zoom level of the given [SceneView]s.
   *
   * @param sceneViews      all [SceneView]s in the surface.
   * @param availableWidth  the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param dimension       used to store the result size. The new [Dimension] instance is created if the given instance is null.
   *
   * @see [getPreferredSize]
   */
  fun getRequiredSize(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?): Dimension

  /**
   * Place the given [SceneView]s in the proper positions by using [SceneView.setLocation]
   * Note that it only changes the locations of [SceneView]s but doesn't change their sizes.
   *
   * @param sceneViews          all [SceneView]s in the surface.
   * @param availableWidth      the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight     the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param keepPreviousPadding true if all padding values should be the same as current one. This happens when resizing the [SceneView].
   */
  fun layout(sceneViews: List<SceneView>, availableWidth: Int, availableHeight: Int, keepPreviousPadding: Boolean = false)

  /**
   * Find the renderable area for the given [targetSceneView].
   *
   * @param sceneViews      all [SceneView]s in the current Surface.
   * @param availableWidth  the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param surfaceRect     the size of total scrollable area in surface.
   * @param retRect         used to store the result bounds. The new [Rectangle] instance is created if the given instance is null.
   */
  fun getRenderableBoundsForInvisibleComponents(targetSceneView: SceneView,
                                                sceneViews: List<SceneView>,
                                                availableWidth: Int,
                                                availableHeight: Int,
                                                surfaceRect: Rectangle,
                                                retRect: Rectangle?)
    : Rectangle
}
