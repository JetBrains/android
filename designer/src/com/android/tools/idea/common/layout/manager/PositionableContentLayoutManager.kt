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
package com.android.tools.idea.common.layout.manager

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.PositionablePanel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.uibuilder.surface.layout.horizontal
import com.android.tools.idea.uibuilder.surface.layout.vertical
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Point

val INVISIBLE_POINT = Point(Integer.MIN_VALUE, Integer.MIN_VALUE)

/**
 * [LayoutManager] responsible for positioning and measuring all the [PositionablePanel] in a
 * [DesignSurface]. [PositionablePanel] is accessed via [PositionableContent].
 *
 * For now, the PositionableContentLayoutManager does not contain actual Swing components so we do
 * not need to layout them, just calculate the size of the layout. Eventually, PositionableContent
 * will end up being actual Swing components and we will not need this specialized LayoutManager.
 */
abstract class PositionableContentLayoutManager : LayoutManager {
  /**
   * Method called by the [PositionableContentLayoutManager] to make sure that the layout of the
   * [PositionableContent]s to ask them to be laid out within the [SceneViewPanel].
   */
  abstract fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension)

  private fun Container.findVisiblePositionablePanels(): Collection<PositionablePanel> =
    components.filterIsInstance<PositionablePanel>().filter { it.isVisible() }

  private val Container.availableSize: Dimension
    get() = Dimension(size.width - insets.horizontal, size.height - insets.vertical)

  final override fun layoutContainer(parent: Container) {
    val panels = parent.findVisiblePositionablePanels()

    // We lay out the [SceneView]s first, so we have the actual sizes available for setting the
    // bounds of the Swing components.
    layoutContainer(panels.map { it.positionableAdapter }, parent.availableSize)

    // We set invisible panels to have the most negative coordinates possible, so that their
    // entire content is guaranteed to be outside of the user visible area.
    parent.components
      .filterIsInstance<PositionablePanel>()
      .filterNot { it.isVisible() }
      .forEach { it.positionableAdapter.setLocation(INVISIBLE_POINT.x, INVISIBLE_POINT.y) }
  }

  open fun minimumLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension = Dimension(0, 0)

  final override fun minimumLayoutSize(parent: Container): Dimension =
    minimumLayoutSize(
      parent.findVisiblePositionablePanels().map { it.positionableAdapter },
      parent.availableSize,
    )

  abstract fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension

  final override fun preferredLayoutSize(parent: Container): Dimension =
    preferredLayoutSize(
      parent.findVisiblePositionablePanels().map { it.positionableAdapter },
      parent.availableSize,
    )

  override fun addLayoutComponent(name: String?, comp: Component?) {}

  override fun removeLayoutComponent(comp: Component?) {}

  abstract fun getMeasuredPositionableContentPosition(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
  ): Map<PositionableContent, Point>
}
