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

import androidx.annotation.VisibleForTesting
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.layout
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
class NlDesignSurfacePositionableContentLayoutManager(
  private val surface: NlDesignSurface,
  parentDisposable: Disposable,
  layoutOption: SurfaceLayoutOption,
) : PositionableContentLayoutManager(), LayoutManagerSwitcher {

  @VisibleForTesting val scope = AndroidCoroutineScope(parentDisposable)

  override val currentLayout = MutableStateFlow(layoutOption)

  init {
    scope.launch(uiThread) { currentLayout.collect { surface.onLayoutUpdated(it) } }
  }

  override fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension) {
    availableSize.size = surface.extentSize
    currentLayout.value.layoutManager.layout(
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
    return currentLayout.value.layoutManager.getFitIntoScale(
      content,
      availableSize.width,
      availableSize.height,
    )
  }

  override fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension {
    availableSize.size = surface.extentSize
    val dimension =
      currentLayout.value.layoutManager.getRequiredSize(
        content,
        availableSize.width,
        availableSize.height,
        null,
      )
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
    return currentLayout.value.layoutManager.measure(
      content,
      availableWidth,
      availableHeight,
      surface.isCanvasResizing,
    )
  }
}
