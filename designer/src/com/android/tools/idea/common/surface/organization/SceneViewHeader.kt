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
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.JPanel

/** Header for the group of previews. */
class SceneViewHeader(organizationGroup: String?, displayName: String?) :
  JPanel(BorderLayout()), PositionablePanel {

  val headerSize = JBDimension(200, 26)

  init {
    preferredSize = headerSize
    isOpaque = false
    size = headerSize
    displayName?.let {
      val label = JBLabel(it)
      label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL).deriveFont(Font.BOLD)
      add(label, BorderLayout.CENTER)
    }
  }

  override val positionableAdapter =
    object : HeaderPositionableContent {
      override val organizationGroup: String? = organizationGroup
      override val scale: Double = 1.0
      override var x: Int = 0
        private set

      override var y: Int = 0
        private set

      override fun getContentSize(dimension: Dimension?): Dimension {
        return this@SceneViewHeader.headerSize
      }

      override fun setLocation(x: Int, y: Int) {
        this.x = x
        this.y = y
        this@SceneViewHeader.setLocation(x, y)
      }

      override fun getMargin(scale: Double): Insets {
        return JBUI.emptyInsets()
      }
    }
}
