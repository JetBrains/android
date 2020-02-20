/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.layout

import com.android.tools.idea.common.surface.SceneView
import java.awt.Dimension

/**
 * Sorts the [Collection<SceneView>] by its x and y coordinates.
 */
internal fun Collection<SceneView>.sortByPosition() = sortedWith(compareBy({ it.y }, { it.x }))

/**
 * Interface used to layout and measure the size of [SceneView]s in [com.android.tools.idea.common.surface.DesignSurface].
 */
@Deprecated("The functionality here will be migrated to the SceneViewLayoutManager")
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
  fun getPreferredSize(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?): Dimension

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
  fun getRequiredSize(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, dimension: Dimension?): Dimension

  /**
   * Place the given [SceneView]s in the proper positions by using [SceneView.setLocation]
   * Note that it only changes the locations of [SceneView]s but doesn't change their sizes.
   *
   * @param sceneViews          all [SceneView]s in the surface.
   * @param availableWidth      the width of current visible area, which doesn't include the hidden part in the scroll view.
   * @param availableHeight     the height of current visible area, which doesn't include the hidden part in the scroll view.
   * @param keepPreviousPadding true if all padding values should be the same as current one. This happens when resizing the [SceneView].
   */
  fun layout(sceneViews: Collection<SceneView>, availableWidth: Int, availableHeight: Int, keepPreviousPadding: Boolean = false)
}
