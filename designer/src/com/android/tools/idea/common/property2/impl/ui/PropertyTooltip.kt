/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_BORDER_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_FILL_COLOR
import com.android.tools.adtui.stdui.StandardColors.ERROR_BUBBLE_TEXT_COLOR
import com.android.tools.idea.common.property2.api.PropertyItem
import com.google.common.html.HtmlEscapers
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.CommonXmlStrings.HTML_END
import com.intellij.xml.CommonXmlStrings.HTML_START
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val ICON_SPACING = 6
private const val TIP_WIDTH_START = "<div WIDTH=600>"
private const val TIP_WIDTH_END = "</div>"

/**
 * Implementation of custom tool tips for displaying errors and warnings.
 *
 * This implementation only works when running in the IDE, since we are using
 * the IJ IdeTooltipManager.
 */
class PropertyTooltip(val component: JComponent, point: Point) : IdeTooltip(component, point, TooltipComponent()) {
  private val tip = tipComponent as TooltipComponent

  override fun onHidden() {
    // Remove references to this custom tool tip
    val manager = IdeTooltipManager.getInstance()
    manager.setCustomTooltip(component, null)
  }

  companion object {

    fun setToolTip(component: JComponent, event: MouseEvent, property: PropertyItem?, forValue: Boolean, text: String): String? {
      val manager = IdeTooltipManager.getInstance()
      if (property == null) {
        manager.hideCurrent(event)
      }
      else {
        val tooltip = createToolTip(component, event.point, property, forValue, text)
        manager.setCustomTooltip(component, tooltip)
      }
      return null
    }

    private fun createToolTip(component: JComponent,
                              point: Point,
                              property: PropertyItem,
                              forValue: Boolean,
                              currentText: String): PropertyTooltip? {
      if (!forValue) {
        val text = property.tooltipForName.nullize() ?: return null
        return createTooltipWithContent(component, point, text)
      }

      val validation = property.editingSupport.validation(currentText)
      when (validation.first) {
        EditingErrorCategory.ERROR ->
          return createTooltipWithContent(component, point, validation.second, StudioIcons.Common.ERROR_INLINE,
                                          ERROR_BUBBLE_BORDER_COLOR, ERROR_BUBBLE_TEXT_COLOR, ERROR_BUBBLE_FILL_COLOR)
        EditingErrorCategory.WARNING ->
          return createTooltipWithContent(component, point, validation.second, StudioIcons.Common.WARNING_INLINE)
        else -> {
          val text = property.tooltipForValue.nullize() ?: return null
          return createTooltipWithContent(component, point, text, null, null, null, null)
        }
      }
    }

    private fun createTooltipWithContent(component: JComponent,
                                         point: Point,
                                         text: String,
                                         icon: Icon? = null,
                                         border: Color? = null,
                                         foreground: Color? = null,
                                         background: Color? = null): PropertyTooltip {
      val tooltip = PropertyTooltip(component, point)
      tooltip.tip.icon = icon
      tooltip.tip.text = text
      tooltip.borderColor = border
      tooltip.textBackground = background
      tooltip.textForeground = foreground

      // Make the popup display faster if there is an error or warning,
      // use the normal delay if this is just information.
      tooltip.setHighlighterType(icon != null)
      return tooltip
    }
  }
}

private class TooltipComponent: JPanel(BorderLayout()) {
  private val iconLabel = JBLabel()
  private val textLabel = JBLabel()

  var icon: Icon?
    get() = iconLabel.icon
    set(value) {
      iconLabel.icon = value
      iconLabel.isVisible = (value != null)
    }

  var text: String
    get() = textLabel.text
    set(value) {
      textLabel.text = formatText(value)
    }

  init {
    background = UIUtil.TRANSPARENT_COLOR
    textLabel.border = JBUI.Borders.empty()
    textLabel.background = UIUtil.TRANSPARENT_COLOR
    textLabel.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    iconLabel.border = JBUI.Borders.emptyRight(ICON_SPACING)
    iconLabel.verticalAlignment = SwingConstants.TOP
    add(iconLabel, BorderLayout.WEST)
    add(textLabel, BorderLayout.CENTER)
  }

  private fun formatText(value: String): String {
    val content: String
    if (value.startsWith(HTML_START) && value.endsWith(HTML_END)) {
      content = value.removePrefix(HTML_START).removeSuffix(HTML_END)
    }
    else {
      content = HtmlEscapers.htmlEscaper().escape(value)
    }
    return "$HTML_START$TIP_WIDTH_START$content$TIP_WIDTH_END$HTML_END"
  }
}
