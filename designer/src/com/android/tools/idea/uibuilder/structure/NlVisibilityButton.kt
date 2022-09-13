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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.uibuilder.structure.NlVisibilityModel.Visibility
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.IconReplacer
import com.intellij.ui.RetrievableIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton

/**
 * Button that paints views visibility using StudioIcons.
 */
class NlVisibilityButton: JButton() {

  companion object {
    private const val PADDING_X = 1
    private const val PADDING_Y = 4
    const val WIDTH = NlVisibilityGutterPanel.WIDTH - PADDING_X
    const val HEIGHT = RESOURCE_ICON_SIZE + PADDING_Y
  }

  // Used for painting inner background when clicked.
  private val insets = JBInsets.create(1, 2)

  var visibility: Visibility? = null
  var isClicked = false
  var isHovered = false
  var normalIcon: Icon = EmptyIcon.ICON_16
  var hoveredIcon: Icon = EmptyIcon.ICON_16
  var clickedIcon: Icon = EmptyIcon.ICON_16
  var updateBgWhenHovered = false

  init {
    background = secondaryPanelBackground
    preferredSize = Dimension(WIDTH, HEIGHT)

    // These are required to remove blue border when clicked.
    border = BorderFactory.createEmptyBorder()
    isBorderPainted = false;
    isFocusPainted = false
  }

  fun update(presentation: ButtonPresentation) {
    this.visibility = presentation.visibility
    this.isClicked = presentation.isClicked
    this.isHovered = presentation.isHovered
    this.normalIcon = presentation.icon
    this.hoveredIcon = presentation.hoverIcon
    this.clickedIcon = presentation.clickIcon
    this.updateBgWhenHovered = presentation.updateBgWhenHovered
    toolTipText = presentation.hint
  }

  override fun paintComponent(g: Graphics?) {
    val g = g as Graphics2D
    if (isClicked) {
      paintBackground(g, JBUI.CurrentTheme.ActionButton.pressedBackground())
      paintIconOnCenter(g, clickedIcon)
    } else if (isHovered) {
      if (updateBgWhenHovered) {
        paintBackground(g, JBUI.CurrentTheme.ActionButton.hoverBackground())
      }
      paintIconOnCenter(g, hoveredIcon)
    } else {
      paintIconOnCenter(g, normalIcon)
    }
  }

  private fun paintIconOnCenter(g: Graphics2D, icon: Icon) {
    val x = width / 2 - icon.iconWidth / 2
    val y = height / 2 - icon.iconHeight / 2
    icon.paintIcon(this, g, x, y)
  }

  private fun paintBackground(g: Graphics2D, bgColor: Color) {
    val rect = Rectangle(size)
    JBInsets.removeFrom(rect, insets)

    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.color = bgColor
      val arc = DarculaUIUtil.BUTTON_ARC.float
      g2.fill(RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat(), arc, arc))
    }
    finally {
      g2.dispose()
    }
  }
}

/**
 * Presentation for the [NlVisibilityButton]
 */
class ButtonPresentation {

  // The visibility this item represents. null if it represents
  // non-view or referent item.
  var visibility: Visibility? = null
  // True if this item represents Tools attribute.
  // False if it represents android attribute.
  var isToolsAttr: Boolean = false
  // True if visibility.none should hide icon. False otherwise.
  var hideNone: Boolean = false

  var model: NlVisibilityModel? = null
  // Normal Icon
  var icon: Icon = EmptyIcon.ICON_16
  var hoverIcon: Icon = EmptyIcon.ICON_16
  var clickIcon: Icon = EmptyIcon.ICON_16

  var isClicked = false
  var isHovered = false
  var hint = ""
  var updateBgWhenHovered = false

  constructor()

  constructor(model: NlVisibilityModel) {
    this.model = model
    this.visibility = model.getCurrentVisibility()
    this.isToolsAttr = model.isToolsAttrAvailable()
    this.hideNone = true
    update()
  }

  constructor(visibility: Visibility, isToolsAttr: Boolean) {
    this.visibility = visibility
    this.isToolsAttr = isToolsAttr
    this.hideNone = false
    update()
  }

  private fun update() {
    when (visibility) {
      Visibility.NONE -> {
        if (hideNone) {
          hoverIcon = AlphaIcon(StudioIcons.LayoutEditor.Properties.VISIBLE, 0.5f)
          clickIcon = StudioIcons.LayoutEditor.Properties.VISIBLE
        } else {
          icon = StudioIcons.Common.REMOVE
          hoverIcon = icon
          clickIcon = icon
        }
        hint = "Visibility not set"
      }
      Visibility.VISIBLE -> {
        icon = if (isToolsAttr)
          StudioIcons.LayoutEditor.Properties.VISIBLE_TOOLS_ATTRIBUTE else
          StudioIcons.LayoutEditor.Properties.VISIBLE
        hoverIcon = icon
        clickIcon = icon
        hint = "visible"
      }
      Visibility.INVISIBLE -> {
        icon = if (isToolsAttr)
          StudioIcons.LayoutEditor.Properties.INVISIBLE_TOOLS_ATTRIBUTE else
          StudioIcons.LayoutEditor.Properties.INVISIBLE
        hoverIcon = icon
        clickIcon = icon
        hint = "invisible"
      }
      Visibility.GONE -> {
        icon = if (isToolsAttr)
          StudioIcons.LayoutEditor.Properties.GONE_TOOLS_ATTRIBUTE else
          StudioIcons.LayoutEditor.Properties.GONE
        hoverIcon = icon
        clickIcon = icon
        hint = "gone"
      }
    }
  }
}

/**
 * Apply alpha to the existing icon.
 * @param alpha [0.0:1.0] where 0 is fully transparent.
 */
class AlphaIcon(
  private val studioIcon: Icon,
  var alpha: Float = 1.0f) : Icon, RetrievableIcon {

  override fun getIconHeight(): Int {
    return studioIcon.iconHeight
  }

  override fun getIconWidth(): Int {
    return studioIcon.iconWidth
  }

  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    if (g == null) { return }
    val g = g.create() as Graphics2D
    try {
      g.composite = AlphaComposite.SrcOver.derive(alpha)
      studioIcon.paintIcon(c, g, x, y)
    } finally {
      g.dispose()
    }
  }

  override fun retrieveIcon(): Icon = studioIcon

  override fun replaceBy(replacer: IconReplacer): Icon = AlphaIcon(replacer.replaceIcon(studioIcon), alpha)
}
