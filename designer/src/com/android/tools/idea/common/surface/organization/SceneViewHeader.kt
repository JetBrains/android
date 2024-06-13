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
package com.android.tools.idea.common.surface.organization

import com.android.tools.idea.uibuilder.surface.layout.HeaderPositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionablePanel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel

/** The unscaled height of the [SceneViewHeader]. */
private const val heightPx = 26

/** Offset to parent's width. */
private const val widthOffsetPx = 30

/** Size required for this component in layout. */
private val requiredSize = JBDimension(100, heightPx)

/** Header for the group of previews. */
class SceneViewHeader(
  parentContainer: JComponent,
  organizationGroup: String?,
  displayName: String,
  createComposeHeader: (OrganizationGroup) -> JComponent,
) : JPanel(BorderLayout()), PositionablePanel {

  init {
    isOpaque = false
    add(createComposeHeader(OrganizationGroup(displayName)), BorderLayout.CENTER)

    fun updateSize() {
      size = Dimension(parentContainer.width - scale(widthOffsetPx), scale(heightPx))
    }
    updateSize()

    // Let component follow parent's width with offset.
    parentContainer.addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          updateSize()
        }
      }
    )
  }

  override val positionableAdapter =
    object : HeaderPositionableContent {
      override val organizationGroup: String? = organizationGroup
      override val scale: Double = 1.0
      override val x
        get() = this@SceneViewHeader.x

      override val y
        get() = this@SceneViewHeader.y

      override fun getContentSize(dimension: Dimension?) = requiredSize

      override fun getMargin(scale: Double) = JBUI.emptyInsets()

      override fun setLocation(x: Int, y: Int) {
        this@SceneViewHeader.setLocation(x, y)
      }
    }
}
