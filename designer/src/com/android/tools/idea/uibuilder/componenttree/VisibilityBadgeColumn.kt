/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.componenttree

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.componenttree.api.IconColumn
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.colorpicker.LightCalloutPopup
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A BadgeItem for displaying visibility icons in the 3rd column of the component TreeTable.
 */
class VisibilityBadgeColumn(private val badgeUpdated: () -> Unit) : IconColumn("Visibility") {
  private val unsetIcon = ColoredIconGenerator.generateDeEmphasizedIcon(StudioIcons.LayoutEditor.Properties.VISIBLE)

  override fun getIcon(item: Any): Icon? =
    (item as? NlComponent)?.combinedVisibility?.icon

  override fun getHoverIcon(item: Any): Icon? =
    if ((item as? NlComponent)?.combinedVisibility == Visibility.NONE) unsetIcon else null

  override fun getTooltipText(item: Any): String {
    val component = item as? NlComponent ?: return ""
    return component.combinedVisibility.value ?: "Visibility not set"
  }

  override val leftDivider = true

  override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {
    showPopup(item, component, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
  }

  override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
    if (item !is NlComponent) return
    val popupMenu = LightCalloutPopup(VisibilityPanel(item, badgeUpdated))
    popupMenu.show(component, Point(x + JBUIScale.scale(16), y), Balloon.Position.atRight)
  }
}

private enum class Visibility { NONE, VISIBLE, INVISIBLE, GONE, TOOLS_VISIBLE, TOOLS_INVISIBLE, TOOLS_GONE }

private val Visibility.icon: Icon?
  get() = when (this) {
    Visibility.VISIBLE -> StudioIcons.LayoutEditor.Properties.VISIBLE
    Visibility.INVISIBLE -> StudioIcons.LayoutEditor.Properties.INVISIBLE
    Visibility.GONE -> StudioIcons.LayoutEditor.Properties.GONE
    Visibility.TOOLS_VISIBLE -> StudioIcons.LayoutEditor.Properties.VISIBLE_TOOLS_ATTRIBUTE
    Visibility.TOOLS_INVISIBLE -> StudioIcons.LayoutEditor.Properties.INVISIBLE_TOOLS_ATTRIBUTE
    Visibility.TOOLS_GONE -> StudioIcons.LayoutEditor.Properties.GONE_TOOLS_ATTRIBUTE
    else -> null
  }

private val Visibility.value: String?
  get() = when (this) {
    Visibility.TOOLS_VISIBLE,
    Visibility.VISIBLE -> "visible"
    Visibility.TOOLS_INVISIBLE,
    Visibility.INVISIBLE -> "invisible"
    Visibility.TOOLS_GONE,
    Visibility.GONE -> "gone"
    else -> null
  }

private var NlComponent.toolsVisibility: Visibility
  get() = runReadAction {
    when (getAttribute(TOOLS_URI, ATTR_VISIBILITY)) {
      "visible" -> Visibility.TOOLS_VISIBLE
      "invisible" -> Visibility.TOOLS_INVISIBLE
      "gone" -> Visibility.TOOLS_GONE
      else -> Visibility.NONE
    }
  }
  set(value) = runWriteAction {
    NlWriteCommandActionUtil.run(this, "Update tools visibility") { setAttribute(TOOLS_URI, ATTR_VISIBILITY, value.value) }
  }

private var NlComponent.androidVisibility: Visibility
  get() = runReadAction {
    when (getAttribute(ANDROID_URI, ATTR_VISIBILITY)) {
      "visible" -> Visibility.VISIBLE
      "invisible" -> Visibility.INVISIBLE
      "gone" -> Visibility.GONE
      else -> Visibility.NONE
    }
  }
  set(value) {
    NlWriteCommandActionUtil.run(this, "Update visibility") { setAttribute(ANDROID_URI, ATTR_VISIBILITY, value.value) }
  }

private val NlComponent.combinedVisibility: Visibility
  get() {
    val visibility = toolsVisibility
    return if (visibility == Visibility.NONE) androidVisibility else visibility
  }

/**
 * The panel used in the popup when changing the visibility of a component in the component tree.
 */
private class VisibilityPanel(item: NlComponent, private val badgeUpdated: () -> Unit) : JPanel() {
  init {
    val android = JPanel()
    val androidVisibility = Ref(item.androidVisibility, android) {
      item.androidVisibility = it
      badgeUpdated()
    }
    android.layout = BoxLayout(android, BoxLayout.X_AXIS)
    android.border = JBUI.Borders.emptyLeft(3)
    android.alignmentX = LEFT_ALIGNMENT
    android.add(VButton(androidVisibility, Visibility.NONE))
    android.add(VButton(androidVisibility, Visibility.VISIBLE))
    android.add(VButton(androidVisibility, Visibility.INVISIBLE))
    android.add(VButton(androidVisibility, Visibility.GONE))
    val tools = JPanel()
    val toolsVisibility = Ref(item.toolsVisibility, tools) {
      item.toolsVisibility = it
      badgeUpdated()
    }
    tools.layout = BoxLayout(tools, BoxLayout.X_AXIS)
    tools.border = JBUI.Borders.emptyLeft(3)
    tools.alignmentX = LEFT_ALIGNMENT
    tools.add(VButton(toolsVisibility, Visibility.NONE))
    tools.add(VButton(toolsVisibility, Visibility.TOOLS_VISIBLE))
    tools.add(VButton(toolsVisibility, Visibility.TOOLS_INVISIBLE))
    tools.add(VButton(toolsVisibility, Visibility.TOOLS_GONE))

    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    border = JBUI.Borders.empty(8)
    add(VLabel("android:visibility"))
    add(android)
    add(VLabel("tools:visibility"))
    add(tools)
  }

  /**
   * A reference to a Visibility value with a [setter] for updating the associated component.
   */
  private class Ref(initialValue: Visibility, val panel: JPanel, val setter: (Visibility) -> Unit) {
    var value: Visibility = initialValue
      set(value) {
        field = value
        setter(value)
        invokeLater { panel.repaint() }
      }
  }

  /**
   * Convenience swing component for creating left aligned labels for use in the [VisibilityPanel].
   */
  private class VLabel(text: String) : JBLabel(text) {
    init {
      isOpaque = true
      alignmentX = LEFT_ALIGNMENT
    }
  }

  /**
   * A button swing component that detects hovering and clicks; for setting [Visibility] in the [VisibilityPanel].
   */
  private class VButton(val ref: Ref, val visibility: Visibility) : JBLabel(visibility.icon ?: AllIcons.General.Remove) {
    private var isHovering = false

    init {
      border = JBUI.Borders.empty(6, 6)
      toolTipText = visibility.value ?: "Remove attribute"
      addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
          isHovering = true
          repaint()
        }

        override fun mouseExited(event: MouseEvent) {
          isHovering = false
          repaint()
        }

        override fun mouseClicked(event: MouseEvent) {
          ref.value = visibility
        }
      })
    }

    override fun paintComponent(g: Graphics) {
      if (visibility == ref.value) {
        paintBackground(g, JBUI.CurrentTheme.ActionButton.pressedBackground())
      }
      else if (isHovering) {
        paintBackground(g, JBUI.CurrentTheme.ActionButton.hoverBackground())
      }
      super.paintComponent(g)
    }

    private fun paintBackground(g: Graphics, color: Color) {
      val g2 = g.create() as Graphics2D
      val rect = Rectangle(size)
      JBInsets.removeFrom(rect, JBInsets(4, 4, 4, 4))

      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g2.color = color
        val arc = DarculaUIUtil.BUTTON_ARC.get()
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc)
      }
      finally {
        g2.dispose()
      }
    }
  }
}
