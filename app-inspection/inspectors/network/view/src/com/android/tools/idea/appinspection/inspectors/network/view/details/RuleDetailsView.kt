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
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.NetworkInspectorTracker.InterceptionCriteria
import com.android.tools.idea.appinspection.inspectors.network.model.rules.Method
import com.android.tools.idea.appinspection.inspectors.network.model.rules.Protocol
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleVariable
import com.android.tools.idea.appinspection.inspectors.network.model.rules.applyTo
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NetworkInspectorBundle
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerTabKeyAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.TableUtil
import com.intellij.ui.TitledSeparator
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.table.TableView
import com.intellij.util.applyIf
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.net.URI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

private const val MALFORMED_URL = "URL is malformed"

private const val INVALID_PORT = "Port should be an integer between 0 and 65535"

/** View to display a single network interception rule and its detailed information. */
class RuleDetailsView(
  private val getRuleNames: () -> Set<String>,
  private val ruleVariables: MutableList<RuleVariable>,
  private val usageTracker: NetworkInspectorTracker,
) : JPanel() {

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
    border = JBUI.Borders.empty()
    val headingPanel = JPanel(BorderLayout())
    val instanceViewLabel = JLabel("Rule Details")
    instanceViewLabel.border = JBUI.Borders.emptyLeft(6)
    headingPanel.add(instanceViewLabel, BorderLayout.WEST)
    add(headingPanel, TabularLayout.Constraint(0, 0))
    scrollPane.border = AdtUiUtils.DEFAULT_TOP_BORDER
    add(scrollPane, TabularLayout.Constraint(1, 0))
  }

  private fun updateRuleInfo(detailsPanel: ScrollablePanel, rule: RuleData) {
    val namePanel =
      TextFieldWithWarning(rule.name, "Enter rule name", "name", { validateRuleName(it, rule) }) {
        rule.name = it
      }
    val nameCategoryPanel = createCategoryPanel(null, JBLabel("Name:") to namePanel)
    detailsPanel.add(nameCategoryPanel)

    detailsPanel.add(createOriginCategoryPanel(rule))

    detailsPanel.add(createStatusCodeCategoryPanel(rule))

    @Suppress("DialogTitleCapitalization") detailsPanel.add(TitledSeparator("Header rules"))
    detailsPanel.add(createRulesTable(rule.headerRuleTableModel, "headerRules"))

    @Suppress("DialogTitleCapitalization") detailsPanel.add(TitledSeparator("Body rules"))
    detailsPanel.add(createRulesTable(rule.bodyRuleTableModel, "bodyRules"))

    TreeWalker(detailsPanel).descendantStream().forEach { (it as? JComponent)?.isOpaque = false }
    detailsPanel.background = primaryContentBackground
  }

  private fun validateRuleName(name: String, self: RuleData): String? {
    return when {
      name.isBlank() -> "Rule name cannot be blank"
      self.name == name -> null
      getRuleNames().contains(name) -> "Rule named '$name' already exists"
      else -> null
    }
  }

  private fun createStatusCodeCategoryPanel(rule: RuleData): JPanel {
    val data = rule.statusCodeRuleData
    val doReplace = JBCheckBox("Replace with status code:")

    fun validateStatusCode(text: String, isEmptyValid: Boolean): String? {
      return when (validateIntegerInput(text, isEmptyValid, 100, 599)) {
        true -> null
        false -> "Status code should be an integer between 100 and 599"
      }
    }

    val findCodePanel =
      TextFieldWithWarning(
        data.findCode,
        "200",
        "findCode",
        {
          val warning = validateStatusCode(it, !doReplace.isSelected)
          if (warning != null) {
            data.isActive = false
          }
          warning
        },
      ) {
        data.isActive =
          doReplace.isSelected && !parent.findDescendantByName("newCodeWarningLabel").isVisible
        if (data.findCode != it) {
          data.findCode = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.FIND_CODE)
        }
      }
    val newCodePanel =
      TextFieldWithWarning(
        data.newCode,
        "500",
        "newCode",
        {
          val warning = validateStatusCode(it, !doReplace.isSelected)
          if (warning != null) {
            data.isActive = false
          }
          warning
        },
      ) {
        data.isActive =
          doReplace.isSelected && !parent.findDescendantByName("findCodeWarningLabel").isVisible
        if (data.newCode != it) {
          data.newCode = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.FIND_REPLACE_CODE)
        }
      }

    with(doReplace) {
      isSelected = data.isActive
      newCodePanel.isEnabled = isSelected
      addItemListener {
        data.isActive = isSelected
        newCodePanel.isEnabled = isSelected
        findCodePanel.validateText()
        newCodePanel.validateText()
      }
    }
    val root = JPanel(VerticalLayout(6))
    return root.apply {
      add(TitledSeparator("Response").apply { minimumSize = Dimension(0, 34) })
      add(
        JPanel(TabularLayout("Fit,5px,*,40px,Fit,5px,*")).apply {
          add(JLabel("Apply rule for status:"), TabularLayout.Constraint(0, 0))
          add(findCodePanel, TabularLayout.Constraint(0, 2))
          add(doReplace, TabularLayout.Constraint(0, 4))
          add(newCodePanel, TabularLayout.Constraint(0, 6))
        }
      )
    }
  }

  private fun createOriginCategoryPanel(rule: RuleData): JPanel {
    val criteria = rule.criteria
    val protocolComboBox =
      CommonComboBox(DefaultCommonComboBoxModel("", enumValues<Protocol>().toList())).apply {
        isEditable = false
        selectedIndex = 0
        selectedItem = criteria.protocol
        addActionListener {
          (selectedItem as? Protocol)?.let {
            if (criteria.protocol != it) {
              criteria.protocol = it
              usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PROTOCOL)
            }
          }
        }
        name = "protocolComboBox"
      }

    val urlPanel =
      TextFieldWithWarning(
        criteria.host,
        "www.google.com",
        "url",
        { validateHostInput(protocolComboBox.selectedItem as Protocol, it) },
      ) {
        if (criteria.host != it) {
          criteria.host = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.URL_HOST)
        }
      }

    val portPanel =
      TextFieldWithWarning(
        criteria.port,
        "80",
        "port",
        {
          when {
            it == "-0" -> INVALID_PORT
            !validateIntegerInput(it, true, 0, 65535) -> INVALID_PORT
            else -> null
          }
        },
      ) {
        if (criteria.port != it) {
          criteria.port = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PORT)
        }
      }

    val pathTextField =
      createTextField(criteria.path, "search", "pathTextField") { input ->
        val text = input.applyIf(input.isNotBlank() && !input.startsWith('/')) { "/$input" }
        criteria.apply {
          if (path != text) {
            path = text
            usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PATH)
          }
        }
      }
    val queryTextField =
      createTextField(criteria.query, "q=android+studio", "queryTextField") { text ->
        criteria.apply {
          if (query != text) {
            query = text
            usageTracker.trackRuleUpdated(InterceptionCriteria.URL_QUERY)
          }
        }
      }
    val methodComboBox =
      CommonComboBox(DefaultCommonComboBoxModel("", enumValues<Method>().toList())).apply {
        isEditable = false
        selectedIndex = 0
        selectedItem = criteria.method
        addActionListener {
          (selectedItem as? Method)?.let {
            if (criteria.method != it) {
              criteria.method = it
            }
          }
        }
        name = "methodComboBox"
      }
    return createCategoryPanel(
      "Origin",
      JLabel("Protocol:") to protocolComboBox,
      JLabel("Host URL:") to urlPanel,
      JLabel("Port:") to portPanel,
      JLabel("Path:") to pathTextField,
      JLabel("Query:") to queryTextField,
      JLabel("Method:") to methodComboBox,
    )
  }

  private fun createRulesTable(
    model: ListTableModel<RuleData.TransformationRuleData>,
    name: String,
  ): JComponent {
    val table = TableView(model)
    table.registerTabKeyAction { table.transferFocus() }
    table.name = name
    val decorator = ToolbarDecorator.createDecorator(table)

    val trackAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      val component =
        when (newItem) {
          is RuleData.HeaderAddedRuleData -> InterceptionCriteria.ADD_HEADER
          is RuleData.HeaderReplacedRuleData -> InterceptionCriteria.FIND_REPLACE_HEADER
          is RuleData.BodyReplacedRuleData -> InterceptionCriteria.REPLACE_BODY
          is RuleData.BodyModifiedRuleData -> InterceptionCriteria.FIND_REPLACE_BODY
          else -> null
        }
      component?.let { usageTracker.trackRuleUpdated(it) }
    }

    val addRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      model.addRow(newItem)
      val index = table.convertRowIndexToView(model.rowCount - 1)
      table.selectionModel.setSelectionInterval(index, index)
      trackAction(newItem)
    }
    decorator.setAddAction {
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(null, addRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(null, addRowAction).show()
      }
    }
    decorator.setRemoveAction {
      val message =
        when (model) {
          is RuleData.HeaderRulesTableModel -> NetworkInspectorBundle.message("confirmation.header")
          else -> NetworkInspectorBundle.message("confirmation.body")
        }
      val isConfirmed =
        MessageDialogBuilder.okCancel(NetworkInspectorBundle.message("confirmation.title"), message)
          .ask(table)
      if (isConfirmed) {
        if (TableUtil.doRemoveSelectedItems(table, model, null)) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(table, true)
          }
          TableUtil.updateScroller(table)
        }
      }
    }

    val replaceRowAction: (RuleData.TransformationRuleData) -> Unit = { newItem ->
      val selectedItem = table.selectedObject
      val replaceIndex = model.items.indexOf(selectedItem)
      if (replaceIndex != -1) {
        model.items = model.items.map { if (it == selectedItem) newItem else it }
        model.fireTableRowsUpdated(replaceIndex, replaceIndex)
        val tableIndex = table.convertRowIndexToView(replaceIndex)
        table.selectionModel.setSelectionInterval(tableIndex, tableIndex)
        trackAction(newItem)
      }
    }
    decorator.setEditAction {
      val selectedItem = table.selectedObject
      when (model) {
        is RuleData.HeaderRulesTableModel -> HeaderRuleDialog(selectedItem, replaceRowAction).show()
        is RuleData.BodyRulesTableModel -> BodyRuleDialog(selectedItem, replaceRowAction).show()
      }
    }

    val decoratedTableView = decorator.createPanel()
    val infoLabel =
      JBLabel(AllIcons.General.Information).apply {
        border = JBUI.Borders.emptyRight(8)
        isEnabled = false
        toolTipText = "Order of rules indicate execution order."
      }
    decorator.actionsPanel.setToolbarLabel(infoLabel, ActionToolbarPosition.RIGHT)
    ActionToolbarUtil.makeToolbarNavigable(decorator.actionsPanel.toolbar)
    return decoratedTableView
  }

  /** Validate the input in text field to be an integer. */
  private fun validateIntegerInput(
    text: String,
    isEmptyValid: Boolean,
    lowerBound: Int = Int.MIN_VALUE,
    upperBound: Int = Int.MAX_VALUE,
  ): Boolean {
    val expanded = ruleVariables.applyTo(text)!!
    if (expanded.isEmpty()) return isEmptyValid
    val intInput = expanded.toIntOrNull() ?: return false
    return intInput in lowerBound..upperBound
  }

  /**
   * Validate the input in text field to be a valid host. Compare the host field of the `url]` to
   * the [host].
   */
  private fun validateHostInput(protocol: Protocol, host: String): String? {
    // Empty host is acceptable.
    val expanded = ruleVariables.applyTo(host)!!
    if (expanded.isEmpty()) return null
    return try {
      if (URI("${protocol.name}://$expanded").host == expanded) null else MALFORMED_URL
    } catch (_: Exception) {
      MALFORMED_URL
    }
  }
}

private fun Container.findDescendantByName(name: String): Component {
  return TreeWalker(this).descendants().first { it.name == name }
}
