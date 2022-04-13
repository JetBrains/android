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
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.view.rules.createDecoratedTable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val MINIMUM_DETAILS_VIEW_WIDTH = 400
private const val TEXT_LABEL_WIDTH = 220

/**
 * A dialog box that allows adding and editing header rules.
 */
class HeaderRuleDialog(private val saveAction: (RuleData.TransformationRuleData) -> Unit) : DialogWrapper(false) {

  companion object {
    private const val DEFAULT_TEXT = "Text"
  }

  private val nameLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)
  private val valueLabel: JBTextField = createTextField(DEFAULT_TEXT, TEXT_LABEL_WIDTH)

  init {
    title = "New Header Rule"
    init()
  }

  override fun createNorthPanel() = JPanel(VerticalLayout(18)).apply {
    add(createKeyValuePair("Name") { nameLabel })
    add(createKeyValuePair("Value") { valueLabel })
  }

  override fun createCenterPanel(): JComponent? = null

  override fun doOKAction() {
    super.doOKAction()
    saveAction(RuleData.HeaderAddedRuleData(nameLabel.text, valueLabel.text))
  }
}

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
    minimumSize = Dimension(MINIMUM_DETAILS_VIEW_WIDTH, minimumSize.height)
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Rule Details")
    instanceViewLabel.border = BorderFactory.createEmptyBorder(0, 6, 0, 0)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun updateRuleInfo(detailsPanel: ScrollablePanel, rule: RuleData) {
    detailsPanel.add(createKeyValuePair("Name") {
      createTextField(rule.name, TEXT_LABEL_WIDTH) { text ->
        rule.name = text
      }
    })
    detailsPanel.add(createCategoryPanel("Origin", listOf(
      createKeyValuePair("Host url") {
        createTextField(rule.criteria.host, TEXT_LABEL_WIDTH) { text ->
          rule.criteria.apply {
            host = text
          }
        }
      }
    )))

    detailsPanel.add(createCategoryPanel("Header rules", listOf(
      createHeadersTable(rule)
    )))

    TreeWalker(detailsPanel).descendantStream().forEach { (it as? JComponent)?.isOpaque = false }
    detailsPanel.background = primaryContentBackground
    detailsPanel.isOpaque
  }

  private fun createHeadersTable(rule: RuleData): JComponent {
    val model = rule.headerRuleTableModel
    val table = TableView(model)
    val decorator = ToolbarDecorator.createDecorator(table)

    decorator.setAddAction {
      val dialog: DialogWrapper = HeaderRuleDialog() { headerRule ->
        model.addRow(headerRule)
      }
      dialog.show()
    }

    val container = ScrollablePanel(TabularLayout("*", "200px"))
    container.add(createDecoratedTable(table, decorator).apply {
      border = BorderFactory.createLineBorder(borderLight)
    }, TabularLayout.Constraint(0, 0))
    return JBScrollPane().apply { setViewportView(container) }
  }
}

private fun createCategoryPanel(name: String, entryComponents: List<JComponent>): JPanel {
  val panel = JPanel(VerticalLayout(6))

  val headingPanel = TitledSeparator(name)
  headingPanel.minimumSize = Dimension(0, 34)
  panel.add(headingPanel)

  for (component in entryComponents) {
    component.border = BorderFactory.createEmptyBorder()
    panel.add(component)
  }
  return panel
}

private fun createKeyValuePair(key: String, componentProvider: () -> JComponent): JPanel {
  val panel = JPanel(TabularLayout("155px,Fit")).apply {
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
  }
  val keyPanel = JPanel(BorderLayout())
  keyPanel.add(JBLabel(key), BorderLayout.NORTH) // If value is multi-line, key should stick to the top of its cell
  panel.add(keyPanel, TabularLayout.Constraint(0, 0))
  panel.add(componentProvider(), TabularLayout.Constraint(0, 1))
  return panel
}

private fun createTextField(defaultText: String, width: Int, focusLost: (String) -> Unit = {}): JBTextField {
  return JBTextField(defaultText).apply {
    preferredSize = Dimension(width, preferredSize.height)
    border = BorderFactory.createLineBorder(borderLight)
    addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        focusLost(text)
      }
    })
  }
}
