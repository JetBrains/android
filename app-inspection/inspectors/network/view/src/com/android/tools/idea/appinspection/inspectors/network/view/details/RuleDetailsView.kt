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
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NetworkInspectorBundle
import com.android.tools.idea.appinspection.inspectors.network.view.rules.registerTabKeyAction
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
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
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.RectangularShape
import java.net.URI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.plaf.ComboBoxUI
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/** View to display a single network interception rule and its detailed information. */
class RuleDetailsView(private val usageTracker: NetworkInspectorTracker) : JPanel() {
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
    detailsPanel.add(
      createCategoryPanel(
        null,
        JBLabel("Name:") to
          createTextField(rule.name, "Enter rule name", "nameTextField") { text ->
            rule.name = text
          }
      )
    )

    detailsPanel.add(createOriginCategoryPanel(rule))

    detailsPanel.add(createStatusCodeCategoryPanel(rule))

    @Suppress("DialogTitleCapitalization") detailsPanel.add(TitledSeparator("Header rules"))
    detailsPanel.add(createRulesTable(rule.headerRuleTableModel, "headerRules"))

    @Suppress("DialogTitleCapitalization") detailsPanel.add(TitledSeparator("Body rules"))
    detailsPanel.add(createRulesTable(rule.bodyRuleTableModel, "bodyRules"))

    TreeWalker(detailsPanel).descendantStream().forEach { (it as? JComponent)?.isOpaque = false }
    detailsPanel.background = primaryContentBackground
  }

  private fun createStatusCodeCategoryPanel(rule: RuleData): JPanel {
    val statusCodeData = rule.statusCodeRuleData
    fun validateStatusCode(text: String, isEmptyValid: Boolean) =
      validateIntegerInput(text, isEmptyValid, 100, 599)
    val findCodeWarningLabel =
      createWarningLabel(
        "Status code should be an integer between 100 and 599",
        "findCodeWarningLabel"
      )
    val newCodeWarningLabel =
      createWarningLabel(
        "Status code should be an integer between 100 and 599",
        "newCodeWarningLabel"
      )
    val newCodeTextField =
      createTextField(statusCodeData.newCode, "500", "newCodeTextField") {
        if (isEnabled && !validateStatusCode(it, false)) {
          newCodeWarningLabel.isVisible = true
          statusCodeData.isActive = false
          return@createTextField
        }
        statusCodeData.isActive = !findCodeWarningLabel.isVisible
        if (statusCodeData.newCode != it) {
          statusCodeData.newCode = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.FIND_REPLACE_CODE)
        }
      }
    (newCodeTextField.document as AbstractDocument).documentFilter =
      ClearWarningLabelDocumentFilter(newCodeWarningLabel)
    val newCodePanel =
      createPanelWithTextFieldAndWarningLabel(newCodeTextField, newCodeWarningLabel)

    val findCodeTextField =
      createTextField(statusCodeData.findCode, "200", "findCodeTextField") {
        // newCodeTextField is enabled only when the isActiveCheckBox is checked. Use that as a
        // marker to check if empty input is acceptable
        if (!validateStatusCode(it, !newCodeTextField.isEnabled)) {
          findCodeWarningLabel.isVisible = true
          statusCodeData.isActive = false
          return@createTextField
        }
        statusCodeData.isActive = newCodeTextField.isEnabled && !newCodeWarningLabel.isVisible
        if (statusCodeData.findCode != it) {
          statusCodeData.findCode = it
          usageTracker.trackRuleUpdated(InterceptionCriteria.FIND_CODE)
        }
      }
    (findCodeTextField.document as AbstractDocument).documentFilter =
      ClearWarningLabelDocumentFilter(findCodeWarningLabel)
    val findCodePanel =
      createPanelWithTextFieldAndWarningLabel(findCodeTextField, findCodeWarningLabel)

    val isActiveCheckBox =
      JBCheckBox("Replace with status code:").apply {
        isSelected = statusCodeData.isActive
        newCodeTextField.isEnabled = isSelected
        addItemListener {
          newCodeTextField.isEnabled = isSelected
          newCodeWarningLabel.isVisible =
            isSelected && !validateStatusCode(newCodeTextField.text, false)
          findCodeWarningLabel.isVisible = !validateStatusCode(findCodeTextField.text, !isSelected)
          statusCodeData.isActive =
            isSelected && !newCodeWarningLabel.isVisible && !findCodeWarningLabel.isVisible
        }
      }
    return JPanel(VerticalLayout(6)).apply {
      add(TitledSeparator("Response").apply { minimumSize = Dimension(0, 34) })
      add(
        JPanel(TabularLayout("Fit,5px,*,40px,Fit,5px,*")).apply {
          add(JLabel("Apply rule for status:"), TabularLayout.Constraint(0, 0))
          add(findCodePanel, TabularLayout.Constraint(0, 2))
          add(isActiveCheckBox, TabularLayout.Constraint(0, 4))
          add(newCodePanel, TabularLayout.Constraint(0, 6))
        }
      )
    }
  }

  private fun createOriginCategoryPanel(rule: RuleData): JPanel {
    val protocolComboBox =
      CommonComboBox(DefaultCommonComboBoxModel("", enumValues<Protocol>().toList())).apply {
        isEditable = false
        selectedIndex = 0
        selectedItem = rule.criteria.protocol
        addActionListener {
          (selectedItem as? Protocol)?.let {
            if (rule.criteria.protocol != it) {
              rule.criteria.protocol = it
              usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PROTOCOL)
            }
          }
        }
        name = "protocolComboBox"
      }
    val urlWarningLabel = createWarningLabel("URL is malformed", "urlWarningLabel")
    val urlTextField =
      createTextField(rule.criteria.host, "www.google.com", "urlTextField") { text ->
        if (
          !validateHostInput("${(protocolComboBox.selectedItem as Protocol).name}://$text", text)
        ) {
          urlWarningLabel.isVisible = true
          return@createTextField
        }
        rule.criteria.apply {
          if (host != text) {
            host = text
            usageTracker.trackRuleUpdated(InterceptionCriteria.URL_HOST)
          }
        }
      }
    val urlPanel = createPanelWithTextFieldAndWarningLabel(urlTextField, urlWarningLabel)
    (urlTextField.document as AbstractDocument).documentFilter =
      ClearWarningLabelDocumentFilter(urlWarningLabel)

    val portWarningLabel =
      createWarningLabel("Port should be an integer between 0 and 65535", "portWarningLabel")
    val portTextField =
      createTextField(rule.criteria.port, "80", "portTextField") { text ->
        if (text == "-0" || !validateIntegerInput(text, true, 0, 65535)) {
          portWarningLabel.isVisible = true
          return@createTextField
        }
        rule.criteria.apply {
          if (port != text) {
            port = text
            usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PORT)
          }
        }
      }
    (portTextField.document as AbstractDocument).documentFilter =
      ClearWarningLabelDocumentFilter(portWarningLabel)
    val portPanel = createPanelWithTextFieldAndWarningLabel(portTextField, portWarningLabel)

    val pathTextField =
      createTextField(rule.criteria.path, "search", "pathTextField") { input ->
        val text = input.applyIf(input.isNotBlank() && !input.startsWith('/')) { "/$input" }
        rule.criteria.apply {
          if (path != text) {
            path = text
            usageTracker.trackRuleUpdated(InterceptionCriteria.URL_PATH)
          }
        }
      }
    val queryTextField =
      createTextField(rule.criteria.query, "q=android+studio", "queryTextField") { text ->
        rule.criteria.apply {
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
        selectedItem = rule.criteria.method
        addActionListener {
          (selectedItem as? Method)?.let {
            if (rule.criteria.method != it) {
              rule.criteria.method = it
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
      JLabel("Method:") to methodComboBox
    )
  }

  private fun createRulesTable(
    model: ListTableModel<RuleData.TransformationRuleData>,
    name: String
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
}

private class BorderlessComboBox(model: DefaultCommonComboBoxModel<String>) :
  CommonComboBox<String, DefaultCommonComboBoxModel<String>>(model) {
  override fun setUI(ui: ComboBoxUI?) {
    super.setUI(BorderlessComboBoxUI())
  }
}

private class BorderlessComboBoxUI :
  DarculaComboBoxUI(DarculaUIUtil.COMPONENT_ARC.float, JBUI.insets(0), true) {
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

/** Document filter to make the [warningLabel] invisible when addition/deletion occurs in input. */
private class ClearWarningLabelDocumentFilter(private val warningLabel: JLabel) : DocumentFilter() {

  override fun remove(fb: FilterBypass?, offset: Int, length: Int) {
    warningLabel.isVisible = false
    super.remove(fb, offset, length)
  }

  override fun replace(
    fb: FilterBypass?,
    offset: Int,
    length: Int,
    text: String?,
    attrs: AttributeSet?
  ) {
    warningLabel.isVisible = false
    super.replace(fb, offset, length, text, attrs)
  }
}

/**
 * Validate the input in text field to be a valid host. Compare the host field of the [url] to the
 * [host].
 */
private fun validateHostInput(url: String, host: String): Boolean {
  // Empty host is acceptable.
  if (host.isEmpty()) return true
  return try {
    URI(url).host == host
  } catch (ignored: Exception) {
    false
  }
}

/** Validate the input in text field to be an integer. */
private fun validateIntegerInput(
  text: String,
  isEmptyValid: Boolean,
  lowerBound: Int = Int.MIN_VALUE,
  upperBound: Int = Int.MAX_VALUE
): Boolean {
  if (text.isEmpty()) return isEmptyValid
  val intInput = text.toIntOrNull() ?: return false
  return intInput in lowerBound..upperBound
}
