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

import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.ResolutionStackItem
import com.android.tools.idea.layoutinspector.resource.SourceLocation
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.ptable.PTableGroupItem
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
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
 * The link provides navigation back to source code where the value of the property was specified.
 */
class ResolutionElementEditor(
  private val model: ResolutionStackModel,
  @get:VisibleForTesting val editorModel: PropertyEditorModel,
  editor: JComponent,
) : JPanel(BorderLayout()) {

  private val linkPanel = JPanel()

  init {
    isOpaque = false
    background = UIUtil.TRANSPARENT_COLOR
    add(editor, BorderLayout.CENTER)
    add(linkPanel, BorderLayout.SOUTH)
    linkPanel.layout = BoxLayout(linkPanel, BoxLayout.Y_AXIS)
    linkPanel.isVisible = false
    linkPanel.isOpaque = false
    linkPanel.background = UIUtil.TRANSPARENT_COLOR
    editorModel.addListener { updateFromModel() }
    editor.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(event: MouseEvent) {
          if (!event.isConsumed && event.clickCount > 1) {
            editorModel.tableSupport?.toggleGroup()
            event.consume()
          }
        }
      }
    )
    updateFromModel()
  }

  private fun updateFromModel() {
    val property = editorModel.property as InspectorPropertyItem
    val locations = property.sourceLocations
    val classLocation = (property as? InspectorGroupPropertyItem)?.classLocation
    val hideLinkPanel =
      (locations.isEmpty() && classLocation == null) ||
        (property is PTableGroupItem && !editorModel.isExpandedTableItem)
    val isSelected = editorModel.isUsedInRendererWithSelection
    linkPanel.isVisible = !hideLinkPanel
    editorModel.isCustomHeight = !hideLinkPanel
    isOpaque = isSelected
    background = if (isSelected) UIUtil.getTableBackground(true, true) else UIUtil.TRANSPARENT_COLOR
    if (!hideLinkPanel) {
      linkPanel.removeAll()
      val isOverridden = property is ResolutionStackItem
      classLocation?.let { linkPanel.add(SourceLocationLink(it, isSelected, false)) }
      when (locations.size) {
        0 -> {}
        1 -> linkPanel.add(SourceLocationLink(locations.first(), isSelected, isOverridden))
        else ->
          linkPanel.add(
            ExpansionPanel(model, editorModel, property, locations, isSelected, isOverridden)
          )
      }
    }
  }

  /** A panel with a expandable list of detail locations. */
  private class ExpansionPanel(
    private val model: ResolutionStackModel,
    private val editorModel: PropertyEditorModel,
    private val property: InspectorPropertyItem,
    locations: List<SourceLocation>,
    private val isSelected: Boolean,
    isOverridden: Boolean,
  ) : JPanel(BorderLayout()) {

    private val extraPanel = JPanel()
    private val expandLabel =
      object : JBLabel() {
        override fun paintComponent(g: Graphics) {
          super.paintComponent(g)
          if (hasFocus() && g is Graphics2D) {
            DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
          }
        }
      }

    init {
      val mainPanel = JPanel()
      mainPanel.layout = BoxLayout(mainPanel, BoxLayout.X_AXIS)
      mainPanel.isOpaque = false
      mainPanel.background = UIUtil.TRANSPARENT_COLOR
      mainPanel.border = JBUI.Borders.emptyLeft(8)
      val isExtraPanelVisible = model.isExpanded(property)
      extraPanel.layout = BoxLayout(extraPanel, BoxLayout.Y_AXIS)
      extraPanel.isOpaque = false
      extraPanel.background = UIUtil.TRANSPARENT_COLOR
      extraPanel.isVisible = isExtraPanelVisible
      extraPanel.border = JBUI.Borders.emptyLeft(24)
      expandLabel.icon = UIUtil.getTreeNodeIcon(isExtraPanelVisible, isSelected, isSelected)
      expandLabel.registerActionKey({ toggle() }, KeyStrokes.SPACE, "space")
      expandLabel.registerActionKey({ toggle() }, KeyStrokes.ENTER, "enter")
      expandLabel.registerActionKey({ open() }, KeyStrokes.RIGHT, "open")
      expandLabel.registerActionKey({ open() }, KeyStrokes.NUM_RIGHT, "open")
      expandLabel.registerActionKey({ close() }, KeyStrokes.LEFT, "close")
      expandLabel.registerActionKey({ close() }, KeyStrokes.NUM_LEFT, "close")
      expandLabel.border = JBUI.Borders.empty(LINK_BORDER)
      expandLabel.isFocusable = true
      expandLabel.addMouseListener(
        object : MouseAdapter() {
          override fun mousePressed(event: MouseEvent) {
            toggle()
          }
        }
      )
      val mainLocation = locations.first()
      mainPanel.add(expandLabel)
      mainPanel.add(SourceLocationLink(mainLocation, isSelected, isOverridden))

      for (index in 1 until locations.size) {
        extraPanel.add(SourceLocationLink(locations[index], isSelected, isOverridden))
      }
      add(mainPanel, BorderLayout.CENTER)
      add(extraPanel, BorderLayout.SOUTH)
      isOpaque = false
      background = UIUtil.TRANSPARENT_COLOR
      alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun toggle() {
      model.toggle(property)
      val isExpanded = model.isExpanded(property)
      expandLabel.icon = UIUtil.getTreeNodeIcon(isExpanded, isSelected, isSelected)
      extraPanel.isVisible = isExpanded
      editorModel.tableSupport?.updateRowHeight(true)
    }

    private fun open() {
      if (!model.isExpanded(property)) {
        toggle()
      }
    }

    private fun close() {
      if (model.isExpanded(property)) {
        toggle()
      }
    }
  }

  companion object {

    /**
     * Return true if this property be displayed with a link panel.
     *
     * This information is useful for determining if a property value is editable.
     */
    fun hasLinkPanel(property: InspectorPropertyItem): Boolean {
      if (property is PTableGroupItem) {
        return true
      }
      return property.sourceLocations.isNotEmpty()
    }
  }

  /**
   * Allow the link to be clickable in the table even though the editor is only used for rendering.
   *
   * @param [location] the source location. If [SourceLocation.navigatable] is missing then show the
   *   link as normal text.
   * @param [isSelected] then the font color will use the table foreground for selected and focused.
   * @param [isOverridden] then the font will use strikeout to indicate the value is overridden.
   */
  private class SourceLocationLink(
    private val location: SourceLocation,
    isSelected: Boolean,
    isOverridden: Boolean,
  ) : JBLabel() {

    init {
      val showAsLink = location.navigatable != null
      val normalForegroundColor =
        when {
          isSelected -> UIUtil.getTableForeground(true, true)
          showAsLink -> JBUI.CurrentTheme.Link.Foreground.ENABLED
          else -> UIUtil.getTableForeground(false, false)
        }
      text = location.source
      font = getSmallFont(showAsLink, isOverridden)
      foreground = normalForegroundColor
      isFocusable = true
      border = JBUI.Borders.empty(0, LINK_BORDER, LINK_BORDER, LINK_BORDER)
      alignmentX = Component.LEFT_ALIGNMENT
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      registerActionKey({ activateLink() }, KeyStrokes.SPACE, "space")
      registerActionKey({ activateLink() }, KeyStrokes.ENTER, "enter")

      addMouseListener(
        object : MouseAdapter() {
          override fun mousePressed(event: MouseEvent) {
            activateLink()
          }
        }
      )
    }

    private fun activateLink() {
      location.navigatable?.navigate(true)
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      if (hasFocus() && g is Graphics2D) {
        DarculaUIUtil.paintFocusBorder(g, width, height, 0f, true)
      }
    }

    private fun getSmallFont(showAsLink: Boolean, strikeout: Boolean): Font {
      val font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
      @Suppress("UNCHECKED_CAST")
      val attributes = font.attributes as MutableMap<TextAttribute, Any?>
      if (showAsLink) {
        attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
      }
      if (strikeout) {
        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
      }
      return font.deriveFont(attributes)
    }
  }
}
