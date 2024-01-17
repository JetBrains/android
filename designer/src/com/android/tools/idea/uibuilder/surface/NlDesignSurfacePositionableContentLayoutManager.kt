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
package com.android.tools.idea.uibuilder.surface

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.surface.DesignSurface.SceneViewAlignment
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionableContentLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.layout
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max

/**
 * [PositionableContentLayoutManager] for the [NlDesignSurface]. It uses a delegated
 * [SurfaceLayoutManager] to layout the different [PositionableContent]s. The [SurfaceLayoutManager]
 * can be switched at runtime.
 */
class NlDesignSurfacePositionableContentLayoutManager(
  private val surface: NlDesignSurface,
  var layoutManager: SurfaceLayoutManager
) : PositionableContentLayoutManager(), LayoutManagerSwitcher {
  override fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension) {
    availableSize.size = surface.extentSize
    layoutManager.layout(
      content,
      availableSize.width,
      availableSize.height,
      surface.isCanvasResizing
    )
  }

  override fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension
  ): Dimension {
    availableSize.size = surface.extentSize
    val dimension =
      layoutManager.getRequiredSize(content, availableSize.width, availableSize.height, null)
    dimension.setSize(
      max(surface.scrollableViewMinSize.width.toDouble(), dimension.width.toDouble()),
      max(surface.scrollableViewMinSize.height.toDouble(), dimension.height.toDouble())
    )

    return dimension
  }

  override fun isLayoutManagerSelected(layoutManager: SurfaceLayoutManager) =
    this.layoutManager == layoutManager

  @UiThread
  override fun setLayoutManager(
    layoutManager: SurfaceLayoutManager,
    sceneViewAlignment: SceneViewAlignment
  ) {
    this.layoutManager = layoutManager
    surface.setSceneViewAlignment(sceneViewAlignment)
    surface.setScrollPosition(0, 0)
    surface.revalidateScrollArea()
  }

  override fun getMeasuredPositionableContentPosition(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int
  ): Map<PositionableContent, Point> {
    return layoutManager.measure(content, availableWidth, availableHeight, surface.isCanvasResizing)
  }
}
