/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.borderLight
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.view.rules.createDecoratedTable
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.RectangularShape
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.plaf.ComboBoxUI

/**
 * View to display a single network interception rule and its detailed information.
 */
class RuleDetailsView : JPanel() {
  var selectedRule = RuleData(-1, "", false)
    set(value) {
      if (field == value) {
        return
      }
      field = value

      val detailsPanel = ScrollablePanel(VerticalLayout(18))
      // Reserve 14px extra space for scroll bar on the right.
      detailsPanel.border = JBUI.Borders.empty(6, 16, 20, 30)
      updateRuleInfo(detailsPanel, field)
      scrollPane.setViewportView(detailsPanel)
    }

  private val scrollPane = JBScrollPane()

  init {
    layout = TabularLayout("*", "28px,*")
    border = BorderFactory.createEmptyBorder()
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Rule Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun updateRuleInfo(detailsPanel: ScrollablePanel, rule: RuleData) {
    detailsPanel.add(
      createCategoryPanel(
        null, JBLabel("Name:") to createTextField(rule.name, "Enter rule name", "nameTextField") { text ->
        rule.name = text
      })
    )

    detailsPanel.add(createOriginCategoryPanel(rule))

    detailsPanel.add(createStatusCodeCategoryPanel(rule))

    @Suppress("DialogTitleCapitalization")
    detailsPanel.add(TitledSeparator("Header rules"))
    detailsPanel.add(createRulesTable(rule.headerRuleTableModel, "headerRules"))

    @Suppress("DialogTitleCapitalization")
    detailsPanel.add(TitledSeparator("Body rules"))
    detailsPanel.add(createRulesTable(rule.bodyRuleTableModel, "bodyRules"))

    TreeWalker(detailsPanel).descendantStream().forEach { (it as? JComponent)?.isOpaque = false }
    detailsPanel.background = primaryContentBackground
  }

  private fun createStatusCodeCategoryPanel(rule: RuleData): JPanel {
    val statusCodeData = rule.statusCodeRuleData
    val findCodeTextField = createTextField(statusCodeData.findCode, "200", "findCodeTextField") {
      statusCodeData.findCode = it
    }
    val newCodeTextField = createTextField(statusCodeData.newCode, "500", "newCodeTextField") {
      statusCodeData.newCode = it
    }
    val isActiveCheckBox = JBCheckBox("Replace with status code:").apply {
      isSelected = statusCodeData.isActive
      newCodeTextField.isEnabled = isSelected
      addActionListener {
        statusCodeData.isActive = isSelected
        newCodeTextField.isEnabled = isSelected
      }
    }
    return JPanel(VerticalLayout(6)).apply {
      add(TitledSeparator("Response").apply { minimumSize = Dimension(0, 34) })
      add(JPanel(TabularLayout("Fit,5px,*,40px,Fit,5px,*")).apply {
        add(JLabel("Apply rule for status:"), TabularLayout.Constraint(0, 0))
        add(findCodeTextField, TabularLayout.Constraint(0, 2))
        add(isActiveCheckBox, TabularLayout.Constraint(0, 4))
        add(newCodeTextField, TabularLayout.Constraint(0, 6))
      })
    }
  }

  private fun createOriginCategoryPanel(rule: RuleData): JPanel {
    val protocolComboBox = BorderlessComboBox(DefaultCommonComboBoxModel("", listOf("https", "http"))).apply {
      isEditable = false
      selectedIndex = 0
      addActionListener {
        rule.criteria.protocol = selectedItem?.toString() ?: ""
      }
      name = "protocolComboBox"
    }
    val urlTextField = createTextField(rule.criteria.host, "www.google.com", "urlTextField") { text ->
      rule.criteria.apply {
        host = text
      }
    }
    val portTextField = createTextField(rule.criteria.port, "80", "portTextField") { text ->
      rule.criteria.port = text
    }
    val pathTextField = createTextField(rule.criteria.path, "search", "pathTextField") { text ->
      rule.criteria.path = text
    }
    val queryTextField = createTextField(rule.criteria.query, "q=android+studio", "queryTextField") { text ->
      rule.criteria.query = text
    }
    val methodComboBox = BorderlessComboBox(DefaultCommonComboBoxModel("", listOf("GET", "POST"))).apply {
      isEditable = false
      selectedIndex = 0
      addActionListener {
        rule.criteria.method = selectedItem?.toString() ?: ""
      }
      name = "methodComboBox"
    }
    return createCategoryPanel(
      "Origin",
      JLabel("Protocol:") to protocolComboBox,
      JLabel("Host url:") to urlTextField,
      JLabel("Port:") to portTextField,
      JLabel("Path:") to pathTextField,
      JLabel("Query:") to queryTextField,
      JLabel("Method:") to methodComboBox
    )
  }

  private fun createRulesTable(model: ListTableModel<RuleData.TransformationRuleData>, name: String): JComponent {
    val table = TableView(model)
    table.name = name
    val decorator = ToolbarDecorator.createDecorator(table)

    val addRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      model.addRow(newItem)
      val index = table.convertRowIndexToView(model.rowCount - 1)
      table.selectionModel.setSelectionInterval(index, index)
    }
    decorator.setAddAction {
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(null, addRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(null, addRowAction).show()
      }
    }

    val replaceRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      val selectedItem = table.selectedObject
      val replaceIndex = model.items.indexOf(selectedItem)
      if (replaceIndex != -1) {
        model.items = model.items.map {
          if (it == selectedItem) newItem else it
        }
        model.fireTableRowsUpdated(replaceIndex, replaceIndex)
        val tableIndex = table.convertRowIndexToView(replaceIndex)
        table.selectionModel.setSelectionInterval(tableIndex, tableIndex)
      }
    }
    decorator.setEditAction {
      val selectedItem = table.selectedObject
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(selectedItem, replaceRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(selectedItem, replaceRowAction).show()
      }
    }

    val container = ScrollablePanel(TabularLayout("*", "200px"))
    container.add(createDecoratedTable(table, decorator).apply {
      border = BorderFactory.createLineBorder(borderLight)
    }, TabularLayout.Constraint(0, 0))
    return JBScrollPane().apply { setViewportView(container) }
  }
}

private class BorderlessComboBox(
  model: DefaultCommonComboBoxModel<String>
) : CommonComboBox<String, DefaultCommonComboBoxModel<String>>(model) {
  override fun setUI(ui: ComboBoxUI?) {
    super.setUI(BorderlessComboBoxUI())
  }
}

private class BorderlessComboBoxUI : DarculaComboBoxUI(DarculaUIUtil.COMPONENT_ARC.float, JBUI.insets(0), true) {
  override fun installDefaults() {
    super.installDefaults()
    padding = JBUI.emptyInsets()
  }

  override fun getBorderInsets(c: Component?): Insets {
    return JBUI.insets(0, 7)
  }

  override fun getOuterShape(r: Rectangle?, bw: Float, arc: Float): RectangularShape {
    return super.getOuterShape(r, 0f, arc)
  }

  override fun getInnerShape(r: Rectangle, bw: Float, lw: Float, arc: Float): RectangularShape? {
    return super.getInnerShape(r, 0f, lw, arc)
  }
}