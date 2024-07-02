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

import com.android.tools.idea.common.layout.positionable.PositionablePanel
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
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

/** Maximum width allowed for the header */
private const val maxHeaderWidth = 5000

/** Size required for this component in layout. */
private val requiredSize = JBDimension(100, heightPx)

/** Header for the group of previews. */
class SceneViewHeader(
  parentContainer: JComponent,
  organizationGroup: OrganizationGroup,
  createComposeHeader: (OrganizationGroup) -> JComponent,
) : JPanel(BorderLayout()), PositionablePanel {

  init {
    isOpaque = false
    add(createComposeHeader(organizationGroup), BorderLayout.CENTER)

    fun updateSize() {
      // b/348183767: When resizing the header, we dynamically adjust the compose component's size.
      // However, due to a known Skiko issue (https://github.com/JetBrains/skiko/issues/950),
      // excessively large values can cause silent failures. To mitigate this, we limit the header's
      // maximum width.
      val maxParent = minOf(maxHeaderWidth, parentContainer.width)
      size = Dimension(maxParent - scale(widthOffsetPx), scale(heightPx))
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
      override val organizationGroup = organizationGroup
      override val scale: Double = 1.0
      override val x
        get() = this@SceneViewHeader.x

      override val y
        get() = this@SceneViewHeader.y

      override val isFocusedContent: Boolean
        get() = isFocusOwner

      override fun getContentSize(dimension: Dimension?) = requiredSize

      override fun getMargin(scale: Double) = JBUI.emptyInsets()

      override fun setLocation(x: Int, y: Int) {
        this@SceneViewHeader.setLocation(x, y)
      }
    }
}
