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

import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager
import com.android.tools.idea.common.layout.option.layout
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.intellij.openapi.Disposable
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * [PositionableContentLayoutManager] for the [NlDesignSurface]. It uses a delegated
 * [SurfaceLayoutManager] to layout the different [PositionableContent]s. The [SurfaceLayoutManager]
 * can be switched at runtime.
 */
class NlDesignSurfacePositionableContentLayoutManager(layoutOption: SurfaceLayoutOption) :
  PositionableContentLayoutManager(), LayoutManagerSwitcher, Disposable.Default {

  lateinit var surface: NlDesignSurface

  override val currentLayoutOption = MutableStateFlow(layoutOption)

  private val scope = AndroidCoroutineScope(this)

  private var currentLayout = layoutOption.createLayoutManager()

  init {
    scope.launch { currentLayoutOption.collect { currentLayout = it.createLayoutManager() } }
  }

  /**
   * The current [GridLayoutGroup] applied in the layout manager. This state is only used to store
   * group layouts, and it doesn't apply on list layout.
   */
  private val cachedLayoutGroups = MutableStateFlow(listOf<GridLayoutGroup>())

  override fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension) {
    availableSize.size = surface.extentSize
    currentLayout.useCachedLayoutGroups(cachedLayoutGroups)
    currentLayout.layout(
      content,
      availableSize.width,
      availableSize.height,
      surface.isCanvasResizing,
    )
  }

  /**
   * Get the fit into scale value which can display all the [PositionableContent] in the given
   * [availableSize].
   */
  fun getFitIntoScale(content: Collection<PositionableContent>, availableSize: Dimension): Double {
    return currentLayout.getFitIntoScale(content, availableSize.width, availableSize.height)
  }

  override fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension {
    availableSize.size = surface.extentSize
    currentLayout.useCachedLayoutGroups(cachedLayoutGroups)
    val dimension =
      currentLayout.getRequiredSize(content, availableSize.width, availableSize.height, null)
    dimension.setSize(
      max(surface.scrollableViewMinSize.width.toDouble(), dimension.width.toDouble()),
      max(surface.scrollableViewMinSize.height.toDouble(), dimension.height.toDouble()),
    )

    return dimension
  }

  override fun getMeasuredPositionableContentPosition(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
  ): Map<PositionableContent, Point> {
    return currentLayout.measure(content, availableWidth, availableHeight, surface.isCanvasResizing)
  }
}
