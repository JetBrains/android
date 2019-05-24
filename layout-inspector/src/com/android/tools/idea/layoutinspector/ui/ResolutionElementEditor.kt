/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.ResolutionStackItem
import com.android.tools.idea.layoutinspector.resource.DesignLookup
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.ptable2.PTableGroupItem
import com.android.tools.property.ptable2.PTableVariableHeightCellEditor
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val LINK_BORDER = 2

/**
 * An editor that is able to show a link below the actual editor.
 *
 * The link provides navigation back to source code where the value
 * of the property was specified.
 */
class ResolutionElementEditor(val model: PropertyEditorModel, editor: JComponent) : JPanel(BorderLayout()), PTableVariableHeightCellEditor {
  private val linkPanel = JPanel()

  override var isCustomHeight = false

  init {
    background = UIUtil.TRANSPARENT_COLOR
    add(editor, BorderLayout.CENTER)
    add(linkPanel, BorderLayout.SOUTH)
    linkPanel.layout = BoxLayout(linkPanel, BoxLayout.Y_AXIS)
    linkPanel.isVisible = false
    linkPanel.background = UIUtil.TRANSPARENT_COLOR
    model.addListener(ValueChangedListener { updateFromModel() })
    updateFromModel()
  }

  private fun updateFromModel() {
    val property = model.property as? InspectorPropertyItem
    val locations = property?.let { DesignLookup.findFileLocations(property) } ?: emptyList()
    val hideLink = locations.isEmpty() || (property is PTableGroupItem && !model.isExpandedTableItem)
    linkPanel.isVisible = !hideLink
    isCustomHeight = !hideLink
    background = if (model.isUsedInRendererWithSelection) UIUtil.getTableBackground(true, true) else UIUtil.TRANSPARENT_COLOR
    if (!hideLink) {
      linkPanel.removeAll()
      for (location in locations) {
        linkPanel.add(LinkLabel(location, model.isUsedInRendererWithSelection, property is ResolutionStackItem))
      }
    }
  }

  /**
   * Allow the link to be clickable in the table even though the editor is only used for rendering.
   */
  private class LinkLabel(location: SourceLocation, isSelected: Boolean, isOverridden: Boolean): JBLabel() {

    init {
      text = location.source
      font = getMiniFont(isOverridden)
      foreground = if (isSelected) UIUtil.getTableForeground(true, true) else JBColor.BLUE
      isFocusable = true
      border = JBUI.Borders.empty(0, LINK_BORDER, LINK_BORDER, LINK_BORDER)

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
          location.navigatable.navigate(true)
        }
      })
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (hasFocus() && g is Graphics2D) {
        DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
      }
    }

    private fun getMiniFont(strikeout: Boolean): Font {
      val font = UIUtil.getLabelFont(UIUtil.FontSize.MINI)
      @Suppress("UNCHECKED_CAST")
      val attributes = font.attributes as MutableMap<TextAttribute, Any?>
      attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
      if (strikeout) {
        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
      }
      return Font(attributes)
    }
  }
}
