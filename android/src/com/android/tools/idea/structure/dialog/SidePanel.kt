/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.structure.dialog

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.roots.ui.configuration.SidePanelSeparator
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AbstractExpandableItemsHandler
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.Navigator
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.SIDE_PANEL_BACKGROUND
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import javax.swing.CellRendererPane
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.border.EmptyBorder

class SidePanel(private val myNavigator: Navigator, private val myHistory: History) : JPanel(BorderLayout()) {
  data class ProblemStats(val count: Int, val containsErrors: Boolean)

  data class PlaceData(
    val place: Place,
    val separator: String?,
    val presentation: Presentation,
    val countProvider: (() -> ProblemStats)?
  )

  private val listModel: DefaultListModel<PlaceData> = DefaultListModel()

  @VisibleForTesting
  val descriptor: ListItemDescriptor<PlaceData> = object : ListItemDescriptor<PlaceData> {
    override fun getTextFor(place: PlaceData): String? = place.presentation.text
    override fun getTooltipFor(place: PlaceData): String? = null
    override fun getIconFor(place: PlaceData): Icon = JBUIScale.scaleIcon(EmptyIcon.create(16, 20))
    override fun hasSeparatorAboveOf(value: PlaceData): Boolean = value.separator != null
    override fun getCaptionAboveOf(value: PlaceData): String? = value.separator
  }

  private val cellRenderer: ListCellRenderer<PlaceData> = object : GroupedItemsListRenderer<PlaceData>(descriptor) {
    var extraPanel: JPanel? = null
    var countLabel: JLabel? = null
    private var containsErrors: Boolean = false
    private var isSelected: Boolean = false
    private val textBorders = JBUI.Borders.empty(2, 5, 2, 6 + 6)
    private val emptyBorders = JBUI.Borders.empty()

    init {
      mySeparatorComponent.setCaptionCentered(false)
    }

    override fun getForeground(): Color = JBColor(Gray._60, Gray._140)

    override fun createSeparator(): SeparatorWithText = SidePanelSeparator()

    override fun layout() {
      countLabel?.border = if (countLabel?.text.isNullOrEmpty()) emptyBorders else textBorders
      val extraPanel = extraPanel ?: return
      myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH)
      extraPanel.add(myComponent, BorderLayout.CENTER)
      extraPanel.add(countLabel, BorderLayout.EAST)
      myRendererComponent.add(this.extraPanel, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
      list: JList<out PlaceData>,
      value: PlaceData,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      updateCountLabel(isSelected, value)
      layout()
      if (UIUtil.isClientPropertyTrue(list, ExpandableItemsHandler.EXPANDED_RENDERER)) {
        val bounds = list.getCellBounds(index, index)
        bounds.setSize(component.preferredSize.getWidth().toInt(), bounds.getHeight().toInt())
        extraPanel?.let {
          AbstractExpandableItemsHandler.setRelativeBounds(component, bounds, it, validationParent)
          it.setSize(it.preferredSize.getWidth().toInt(), it.height)
          UIUtil.putClientProperty(it, ExpandableItemsHandler.USE_RENDERER_BOUNDS, true)
          return it
        }
      }

      return component
    }

    override fun createItemComponent(): JComponent {
      extraPanel = NonOpaquePanel(BorderLayout())
      countLabel = object : JLabel() {
        init {
          font = UIUtil.getListFont().deriveFont(Font.BOLD)
        }

        override fun paintComponent(g: Graphics) {
          g.color = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.SIDE_PANEL_BACKGROUND
          g.fillRect(0, 0, width, height)
          if (StringUtil.isEmpty(text)) return
          val deepBlue = JBColor(Color(0x97A4B2), Color(92, 98, 113))
          g.color = when {
            isSelected -> Gray._255.withAlpha(if (UIUtil.isUnderDarcula()) 100 else 220)
            containsErrors -> JBColor.RED.darker()
            else -> deepBlue
          }
          val config = GraphicsUtil.setupAAPainting(g)
          g.fillRoundRect(0, 3, width - 6 - 1, height - 6, height - 6, height - 6)
          config.restore()
          foreground = when {
            isSelected && containsErrors -> JBColor.RED.darker()
            isSelected -> deepBlue.darker()
            else -> UIUtil.getListForeground(true, true)
          }
          super.paintComponent(g)
        }

      }
      val component = super.createItemComponent()

      myTextLabel.foreground = Gray._240
      myTextLabel.isOpaque = true

      return component
    }

    override fun getBackground(): Color = SIDE_PANEL_BACKGROUND

    private fun updateCountLabel(isSelected: Boolean, value: PlaceData) {
      val countLabel = countLabel ?: return
      val problemStats = value.countProvider?.invoke()
      val count = problemStats?.count ?: 0

      this.isSelected = isSelected
      containsErrors = problemStats?.containsErrors ?: false
      countLabel.text = when {
        count == 0 -> ""
        count > 100 -> "100+"
        else -> count.toString()
      }
    }
  }

  val validationParent = CellRendererPane()
  val list: JBList<PlaceData> = JBList(listModel)
    .also {
      it.background = SIDE_PANEL_BACKGROUND
      it.border = EmptyBorder(5, 0, 0, 0)
      it.selectionMode = SINGLE_SELECTION
      it.cellRenderer = cellRenderer
      it.add(validationParent)

      it.addListSelectionListener { e ->
        if (e.valueIsAdjusting) return@addListSelectionListener
        it.selectedValue?.let { value ->
          myNavigator.navigateTo(value.place, false)
        }
      }
    }

  private var pendingSeparator: String? = null

  init {
    add(createScrollPane(list, true), BorderLayout.CENTER)
  }

  fun addPlace(place: Place, presentation: Presentation, counterProvider: (() -> ProblemStats)?) {
    listModel.addElement(PlaceData(place, pendingSeparator, presentation, counterProvider))
    pendingSeparator = null
  }

  fun clear() {
    listModel.clear()
    pendingSeparator = null
  }

  fun addSeparator(text: String) {
    pendingSeparator = text
  }

  fun select(place: Place) {
    list.setSelectedValue(listModel.elements().asSequence().find { it.place == place }, true)
  }
}
