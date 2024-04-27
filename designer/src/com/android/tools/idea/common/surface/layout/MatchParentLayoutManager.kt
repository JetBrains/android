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
package com.android.tools.idea.common.surface.layout

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager

/**
 * Layout manager that uses the size of the parent component to display the components.
 *
 * This is useful with the [javax.swing.JLayeredPane] so every layer will take all the space in the
 * pane.
 */
class MatchParentLayoutManager : LayoutManager {
  override fun layoutContainer(parent: Container) {
    val parentBounds = parent.bounds
    if (parent.isPreferredSizeSet) {
      parentBounds.size = parent.preferredSize
    }

    parent.components.forEach { it.bounds = parentBounds }
  }

  // Request max available space
  override fun preferredLayoutSize(parent: Container): Dimension =
    if (parent.isPreferredSizeSet) parent.preferredSize else Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

  override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)

  override fun addLayoutComponent(name: String?, comp: Component?) {}

  override fun removeLayoutComponent(comp: Component?) {}
}
