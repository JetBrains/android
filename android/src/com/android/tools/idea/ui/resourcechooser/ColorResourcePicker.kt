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
package com.android.tools.idea.ui.resourcechooser

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.ui.resourcechooser.colorpicker2.COLOR_PICKER_WIDTH
import com.android.tools.idea.ui.resourcechooser.colorpicker2.HORIZONTAL_MARGIN_TO_PICKER_BORDER
import com.android.tools.idea.ui.resourcechooser.colorpicker2.PICKER_BACKGROUND_COLOR
import com.android.tools.idea.ui.resourcechooser.util.createResourcePickerDialog
import com.android.tools.idea.util.androidFacet
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent

private const val SEARCH_FIELD_WIDTH = (COLOR_PICKER_WIDTH * 0.55).toInt()
private const val SEARCH_BAR_HEIGHT = 30
private const val CATEGORY_DROPDOWN_BUTTON_WIDTH = (COLOR_PICKER_WIDTH * 0.35).toInt()
private const val CATEGORY_DROPDOWN_BUTTON_HEIGHT = SEARCH_BAR_HEIGHT
private const val ITEM_HEIGHT = 40

/**
 * A dialog for picking color resources.
 *
 * @param configuration            The configuration information which is used to resolve the color
 * @param initialResourceReference The default selected reference. If this is null then there is no default selected reference.
 */
class ColorResourcePicker(configuration: Configuration, initialResourceReference: ResourceReference?): JPanel(BorderLayout()) {
  private val colorResourceModel = ColorResourceModel(configuration)

  private val searchField = SearchTextField()
  private val scrollView: JScrollPane
  private val boxModel: MyComboBoxModel
  private val box: CommonComboBox<String, MyComboBoxModel>
  private val list = JBList<ResourceCellData>()
  private val listData = Vector<ResourceCellData>()

  private val listeners = mutableListOf<ColorResourcePickerListener>()

  init {
    val boxPanel = JPanel(BorderLayout())
    boxPanel.border = JBUI.Borders.empty(HORIZONTAL_MARGIN_TO_PICKER_BORDER)
    boxPanel.background = PICKER_BACKGROUND_COLOR

    searchField.run {
      preferredSize = JBDimension(SEARCH_FIELD_WIDTH, SEARCH_BAR_HEIGHT)
      background = PICKER_BACKGROUND_COLOR
      border = JBUI.Borders.empty(0, 0, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateListData()
        }
      })
    }

    boxModel = MyComboBoxModel(colorResourceModel.categories)
    box = CommonComboBox(boxModel)
    box.addActionListener {
      updateListData()
    }
    box.preferredSize = JBDimension(CATEGORY_DROPDOWN_BUTTON_WIDTH, CATEGORY_DROPDOWN_BUTTON_HEIGHT)
    box.background = PICKER_BACKGROUND_COLOR

    boxPanel.add(searchField, BorderLayout.WEST)
    boxPanel.add(box, BorderLayout.CENTER)

    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = ItemRenderer()
    list.setListData(listData)
    list.background = PICKER_BACKGROUND_COLOR
    // Leave some spaces to left side for better view, ans leave spaces to right and bottom sides to avoid scrollbar overlapping.
    list.border = JBUI.Borders.empty(0,
                                     HORIZONTAL_MARGIN_TO_PICKER_BORDER,
                                     HORIZONTAL_MARGIN_TO_PICKER_BORDER,
                                     HORIZONTAL_MARGIN_TO_PICKER_BORDER)

    list.addListSelectionListener {
      val index = list.selectedIndex
      if (index < 0 || index >= listData.size) {
        return@addListSelectionListener
      }

      val selectedResource = listData[index]
      listeners.forEach { it.colorResourcePicked(selectedResource.resourceReference) }
    }

    scrollView = object : JBScrollPane(list, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED) {
      override fun getPreferredSize(): Dimension {
        return JBDimension(COLOR_PICKER_WIDTH, 10 * ITEM_HEIGHT)
      }
    }
    // TODO? remember the previous selection?

    add(boxPanel, BorderLayout.NORTH)
    add(scrollView, BorderLayout.CENTER)

    updateListData()

    if (initialResourceReference != null) {
      selectAndNavigateToResourceReference(initialResourceReference)
    }

    configuration.module.androidFacet?.let { facet ->
      // It is only possible to open resource picker when there is an AndroidFacet
      val footerPanel = JPanel(BorderLayout()).apply {
        background = PICKER_BACKGROUND_COLOR
        val browseLabel = ClickableLabel("Browse").apply {
          background = PICKER_BACKGROUND_COLOR
          isOpaque = false
          foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
          border = JBUI.Borders.empty(HORIZONTAL_MARGIN_TO_PICKER_BORDER / 2, HORIZONTAL_MARGIN_TO_PICKER_BORDER)

          addActionListener {
            val dialog = createResourcePickerDialog(
              "Pick a Color",
              initialResourceReference?.resourceUrl?.toString(),
              facet,
              setOf(ResourceType.COLOR),
              ResourceType.COLOR,
              showColorStateLists = false,
              showSampleData = false,
              showThemeAttributes = true,
              file = configuration.file
            )
            // TODO: Use resource reference instead of resource string when using resource management to pickup resource.
            if (dialog.showAndGet()) dialog.resourceName?.let { pickedResourceName ->
              ResourceUrl.parse(pickedResourceName)
                ?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
                ?.let { ref ->
                  selectAndNavigateToResourceReference(ref)
                  listeners.forEach { listener -> listener.colorResourcePicked(ref) }
                }
            }
          }
        }
        add(browseLabel, BorderLayout.EAST)
      }
      add(footerPanel, BorderLayout.SOUTH)
    }
  }

  private fun selectAndNavigateToResourceReference(ref: ResourceReference) {
    val category = colorResourceModel.findResourceCategory(ref) ?: colorResourceModel.categories.first()
    val boxSelectedIndex = boxModel.getIndexOf(category)
    if (boxSelectedIndex == -1) {
      return
    }
    box.selectedIndex = boxSelectedIndex

    val itemIndex = listData.map { it.resourceReference }.indexOf(ref)
    if (itemIndex != -1) {
      list.selectedIndex = itemIndex
      list.ensureIndexIsVisible(itemIndex)
    }
  }

  private fun updateListData() {
    listData.clear()
    val filter = searchField.text?.trim { it <= ' ' } ?: ""
    colorResourceModel.getResourceReference(box.selectedItem as String, filter).mapNotNullTo(listData) {
      val color = colorResourceModel.resolveColor(it)
      if (color != null) ResourceCellData(it, color) else null
    }
    list.setListData(listData)
  }

  override fun getPreferredSize(): Dimension {
    return scrollView.preferredSize
  }

  fun addColorResourcePickerListener(listener: ColorResourcePickerListener) {
    listeners.add(listener)
  }
}

private class MyComboBoxModel(ids: List<String>) : DefaultComboBoxModel<String>(), CommonComboBoxModel<String> {

  init {
    ids.forEach { addElement(it) }
  }

  override var value = ""

  override var text = ""

  override var editable = false
    private set

  override fun addListener(listener: ValueChangedListener) = Unit

  override fun removeListener(listener: ValueChangedListener) = Unit
}

private data class ResourceCellData(val resourceReference: ResourceReference, val color: Color)

private const val ICON_SIZE = (ITEM_HEIGHT * 0.5).toInt()

private class ItemRenderer : DefaultListCellRenderer() {
  private val panel = JPanel()
  private val colorBrick = ColorBrick()
  private val idLabel = JLabel()

  init {
    horizontalAlignment = SwingConstants.LEADING
    preferredSize = JBDimension(COLOR_PICKER_WIDTH, ITEM_HEIGHT)

    panel.layout = BorderLayout()
    panel.background = PICKER_BACKGROUND_COLOR
    panel.border = JBUI.Borders.empty(3)

    colorBrick.border = JBUI.Borders.empty(2)
    colorBrick.isOpaque = true

    // Add more space between color brick
    idLabel.border = JBUI.Borders.empty(2, 6, 2, 2)
    idLabel.isOpaque = true

    // TODO?: Add edit button at right site.
    panel.add(colorBrick, BorderLayout.WEST)
    panel.add(idLabel, BorderLayout.CENTER)
  }

  override fun getListCellRendererComponent(list: JList<*>,
                                            value: Any?,
                                            index: Int,
                                            selected: Boolean,
                                            expanded: Boolean): Component {
    val item = value as ResourceCellData
    idLabel.text = item.resourceReference.name
    colorBrick.color = item.color

    if (selected) {
      panel.background = UIUtil.getTreeSelectionBackground(list.isFocusOwner)
      colorBrick.background = UIUtil.getTreeSelectionBackground(list.isFocusOwner)
      idLabel.background = UIUtil.getTreeSelectionBackground(list.isFocusOwner)

      colorBrick.foreground = UIUtil.getTreeSelectionForeground(list.isFocusOwner)
      panel.foreground = UIUtil.getTreeSelectionForeground(list.isFocusOwner)
      idLabel.foreground = UIUtil.getTreeSelectionForeground(list.isFocusOwner)
    }
    else {
      panel.background = PICKER_BACKGROUND_COLOR
      colorBrick.background = PICKER_BACKGROUND_COLOR
      idLabel.background = PICKER_BACKGROUND_COLOR

      panel.foreground = UIUtil.getTreeForeground(false, list.isFocusOwner)
      colorBrick.foreground = UIUtil.getTreeForeground(false, list.isFocusOwner)
      idLabel.foreground = UIUtil.getTreeForeground(false, list.isFocusOwner)
    }

    return panel
  }
}

class ColorBrick(var color: Color = Color.WHITE): JComponent() {

  private val ROUND_CORNER_ARC = JBUI.scale(6)

  init {
    preferredSize = JBUI.size(ICON_SIZE)
    border = JBUI.Borders.empty(6)
    background = PICKER_BACKGROUND_COLOR
  }

  override fun paintComponent(g: Graphics) {
    val g2d = g as Graphics2D
    val originalAntialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    val originalColor = g.color

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    // Cleanup background
    g.color = background
    g.fillRect(0, 0, width, height)

    val left = insets.left
    val top = insets.top
    val brickWidth = width - insets.left - insets.right
    val brickHeight = height - insets.top - insets.bottom
    g.color = color
    g2d.fillRoundRect(left, top, brickWidth, brickHeight, ROUND_CORNER_ARC, ROUND_CORNER_ARC)

    g.color = originalColor
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
  }
}

interface ColorResourcePickerListener {
  fun colorResourcePicked(resourceReference: ResourceReference)
}
