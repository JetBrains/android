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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Point

/**
 * [LayoutManager] responsible for positioning and measuring all the [PositionableContent] in a [DesignSurface]
 *
 * For now, the PositionableContentLayoutManager does not contain actual Swing components so we do not need to layout them, just calculate the
 * size of the layout.
 * Eventually, PositionableContent will end up being actual Swing components and we will not need this specialized LayoutManager.
 */
abstract class PositionableContentLayoutManager : LayoutManager {
  /**
   * Method called by the [PositionableContentLayoutManager] to make sure that the layout of the [PositionableContent]s
   * to ask them to be laid out within the [SceneViewPanel].
   */
  abstract fun layoutContainer(content: Collection<PositionableContent>, availableSize: Dimension)

  private fun Container.findSceneViewPeerPanels(): Collection<SceneViewPeerPanel> = components.filterIsInstance<SceneViewPeerPanel>()
  private val Container.availableSize: Dimension
    get() = Dimension(size.width - insets.horizontal, size.height - insets.vertical)

  final override fun layoutContainer(parent: Container) {
    val sceneViewPeerPanels = parent.findSceneViewPeerPanels()

    // We lay out the [SceneView]s first, so we have the actual sizes available for setting the
    // bounds of the Swing components.
    layoutContainer(sceneViewPeerPanels.map { it.positionableAdapter }, parent.availableSize)
  }

  open fun minimumLayoutSize(content: Collection<PositionableContent>, availableSize: Dimension): Dimension = Dimension(0, 0)
  final override fun minimumLayoutSize(parent: Container): Dimension =
    minimumLayoutSize(parent.findSceneViewPeerPanels().map { it.positionableAdapter }, parent.availableSize)

  abstract fun preferredLayoutSize(content: Collection<PositionableContent>, availableSize: Dimension): Dimension
  final override fun preferredLayoutSize(parent: Container): Dimension =
    preferredLayoutSize(parent.findSceneViewPeerPanels().map { it.positionableAdapter }, parent.availableSize)

  override fun addLayoutComponent(name: String?, comp: Component?) {}
  override fun removeLayoutComponent(comp: Component?) {}

  abstract fun getMeasuredPositionableContentPosition(content: Collection<PositionableContent>, availableWidth: Int, availableHeight: Int)
    : Map<PositionableContent, Point>
}