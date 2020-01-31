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
import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.ResolutionStackItem
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
import java.awt.Component
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
class ResolutionElementEditor(
  private val model: ResolutionStackModel,
  private val editorModel: PropertyEditorModel,
  editor: JComponent
) : JPanel(BorderLayout()), PTableVariableHeightCellEditor {

  private val linkPanel = JPanel()

  override var isCustomHeight = false

  init {
    background = UIUtil.TRANSPARENT_COLOR
    add(editor, BorderLayout.CENTER)
    add(linkPanel, BorderLayout.SOUTH)
    linkPanel.layout = BoxLayout(linkPanel, BoxLayout.Y_AXIS)
    linkPanel.isVisible = false
    linkPanel.background = UIUtil.TRANSPARENT_COLOR
    editorModel.addListener(ValueChangedListener { updateFromModel() })
    updateFromModel()
  }

  private fun updateFromModel() {
    val property = editorModel.property as InspectorPropertyItem
    val resourceLookup = property.model.layoutInspector?.layoutInspectorModel?.resourceLookup
    val locations = resourceLookup?.findFileLocations(property) ?: emptyList()
    val classLocation = (property as? InspectorGroupPropertyItem)?.classLocation
    val hideLinkPanel = (locations.isEmpty() && classLocation == null) || (property is PTableGroupItem && !editorModel.isExpandedTableItem)
    linkPanel.isVisible = !hideLinkPanel
    isCustomHeight = !hideLinkPanel
    background = if (editorModel.isUsedInRendererWithSelection) UIUtil.getTableBackground(true, true) else UIUtil.TRANSPARENT_COLOR
    if (!hideLinkPanel) {
      linkPanel.removeAll()
      val isSelected = editorModel.isUsedInRendererWithSelection
      val isOverridden = property is ResolutionStackItem
      classLocation?.let { linkPanel.add(SourceLocationLink(it, isSelected, false)) }
      when (locations.size) {
        0 -> {}
        1 -> linkPanel.add(SourceLocationLink(locations.first(), isSelected, isOverridden))
        else -> linkPanel.add(ExpansionPanel(model, editorModel, property, locations, isSelected, isOverridden))
      }
    }
  }

  /**
   * A panel with a expandable list of detail locations.
   */
  private class ExpansionPanel(
    val model: ResolutionStackModel,
    val editorModel: PropertyEditorModel,
    val property: InspectorPropertyItem,
    locations: List<SourceLocation>,
    isSelected: Boolean,
    isOverridden: Boolean
  ) : JPanel(BorderLayout()) {

    init {
      val mainPanel = JPanel()
      mainPanel.layout = BoxLayout(mainPanel, BoxLayout.X_AXIS)
      mainPanel.background = UIUtil.TRANSPARENT_COLOR
      mainPanel.border = JBUI.Borders.emptyLeft(8)
      val isExtraPanelVisible = model.isExpanded(property)
      val extraPanel = JPanel()
      extraPanel.layout = BoxLayout(extraPanel, BoxLayout.Y_AXIS)
      extraPanel.background = UIUtil.TRANSPARENT_COLOR
      extraPanel.isVisible = isExtraPanelVisible
      extraPanel.border = JBUI.Borders.emptyLeft(24)
      val expandIcon = UIUtil.getTreeNodeIcon(isExtraPanelVisible, isSelected, isSelected)
      val expandLabel = JBLabel(expandIcon)
      expandLabel.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(event: MouseEvent?) {
          model.toggle(property)
          editorModel.refresh()
        }
      })
      val mainLocation = locations.first()
      mainPanel.add(expandLabel)
      mainPanel.add(SourceLocationLink(mainLocation, isSelected, isOverridden))

      for (index in 1 until locations.size) {
        extraPanel.add(SourceLocationLink(locations[index], isSelected, isOverridden))
      }
      add(mainPanel, BorderLayout.CENTER)
      add(extraPanel, BorderLayout.SOUTH)
      background = UIUtil.TRANSPARENT_COLOR
      alignmentX = Component.LEFT_ALIGNMENT
    }
  }

  /**
   * Allow the link to be clickable in the table even though the editor is only used for rendering.
   *
   * @param [location] the source location. If [SourceLocation.navigatable] is missing then show the link as normal text.
   * @param [isSelected] then the font color will use the table foreground for selected and focused.
   * @param [isOverridden] then the font will use strikeout to indicate the value is overridden.
   */
  private class SourceLocationLink(location: SourceLocation, isSelected: Boolean, isOverridden: Boolean): JBLabel() {

    init {
      val showAsLink = location.navigatable != null
      text = location.source
      font = getMiniFont(showAsLink, isOverridden)
      foreground = when {
        isSelected -> UIUtil.getTableForeground(true, true)
        showAsLink -> JBColor.BLUE
        else -> UIUtil.getTableForeground(false, false)
      }
      isFocusable = true
      border = JBUI.Borders.empty(0, LINK_BORDER, LINK_BORDER, LINK_BORDER)
      alignmentX = Component.LEFT_ALIGNMENT

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(event: MouseEvent?) {
          location.navigatable?.navigate(true)
        }
      })
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (hasFocus() && g is Graphics2D) {
        DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
      }
    }

    private fun getMiniFont(showAsLink: Boolean, strikeout: Boolean): Font {
      val font = UIUtil.getLabelFont(UIUtil.FontSize.MINI)
      @Suppress("UNCHECKED_CAST")
      val attributes = font.attributes as MutableMap<TextAttribute, Any?>
      if (showAsLink) {
        attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
      }
      if (strikeout) {
        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
      }
      return Font(attributes)
    }
  }
}
