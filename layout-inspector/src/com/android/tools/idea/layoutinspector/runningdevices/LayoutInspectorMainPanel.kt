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
package com.android.tools.idea.layoutinspector.runningdevices

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane

/**
 * The main panel of Layout Inspector. Renders the device screen and the view bounds.
 * @param devicePanel The panel on which the device screen is rendered.
 */
class LayoutInspectorMainPanel(devicePanel: JComponent) : JLayeredPane() {
  init {
    layout = FillContainerLayoutManager()
    isFocusable = true

    // TODO(b/265150325): temporary overlay panel, replace with a panel that can render Layout Inspector rectangles.
    val overlay = JLabel("temporary overlay")
    setLayer(overlay, PALETTE_LAYER)
    setLayer(devicePanel, DEFAULT_LAYER)

    add(overlay, BorderLayout.CENTER)
    add(devicePanel, BorderLayout.CENTER)
  }
}

/**
 * A [LayoutManager] that resizes its children to fill the parent container.
 */
private class FillContainerLayoutManager : LayoutManager {

  override fun layoutContainer(target: Container) {
    val insets: Insets = target.insets
    val top = insets.top
    val bottom = target.height - insets.bottom
    val left = insets.left
    val right = target.width - insets.right

    for (child in target.components) {
      child.setBounds(left, top, right - left, bottom - top)
    }
  }

  override fun preferredLayoutSize(parent: Container): Dimension {
    // Request all available space.
    return if (parent.isPreferredSizeSet) parent.preferredSize else Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
  }

  override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)
  override fun addLayoutComponent(name: String?, comp: Component?) { }
  override fun removeLayoutComponent(comp: Component?) { }
}