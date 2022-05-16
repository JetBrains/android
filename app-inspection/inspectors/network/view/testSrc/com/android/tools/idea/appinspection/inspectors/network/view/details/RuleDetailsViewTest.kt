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

import com.android.flags.junit.SetFlagRule
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RuleData
import com.android.tools.idea.appinspection.inspectors.network.view.FakeUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.table.TableView
import com.intellij.util.containers.getIfSingle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.InterceptCommand
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type
import java.awt.Component
import java.awt.Container
import java.awt.event.FocusEvent
import java.util.stream.Stream
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

@RunsInEdt
class RuleDetailsViewTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    private var latestCommand = InterceptCommand.getDefaultInstance()

    override suspend fun getStartTimeStampNs() = 0L

    override suspend fun interceptResponse(command: InterceptCommand) {
      latestCommand = command
    }

    fun verifyLatestCommand(checker: (InterceptCommand) -> Unit) {
      checker(latestCommand)
      latestCommand = InterceptCommand.getDefaultInstance()
    }
  }

  @get:Rule
  val setFlagRule = SetFlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  private lateinit var client: TestNetworkInspectorClient
  private lateinit var services: TestNetworkInspectorServices
  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var detailsPanel: NetworkInspectorDetailsPanel
  private val timer: FakeTimer = FakeTimer()
  private lateinit var scope: CoroutineScope

  @Before
  fun before() {
    enableHeadlessDialogs(testRootDisposable)

    val codeNavigationProvider = FakeCodeNavigationProvider()
    client = TestNetworkInspectorClient()
    services = TestNetworkInspectorServices(codeNavigationProvider, timer, client)
    model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), object : HttpDataModel {
      override fun getData(timeCurrentRangeUs: Range) = listOf<HttpData>()
    })
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    inspectorView = NetworkInspectorView(model, FakeUiComponentsProvider(), component, services, scope)
    parentPanel.add(inspectorView.component)
    detailsPanel = inspectorView.detailsPanel
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun addAndRemoveRulesFromTable() {
    val firstRule = addNewRule()
    val table = inspectorView.rulesView.table
    assertThat(firstRule.name).isEqualTo("New Rule")
    assertThat(table.selectedRow).isEqualTo(0)
    firstRule.name = "rule1"
    val secondRule = addNewRule()
    assertThat(secondRule.name).isEqualTo("New Rule")
    assertThat(table.selectedRow).isEqualTo(1)

    val remove = findAction(inspectorView.rulesView.component, "Remove")
    remove.actionPerformed(TestActionEvent())
    assertThat(table.selectedRow).isEqualTo(-1)
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleRemoved()).isTrue()
      assertThat(command.interceptRuleRemoved.ruleId).isEqualTo(secondRule.id)
    }
  }

  @Test
  fun disableAndEnableRulesFromTable() {
    val rule = addNewRule()
    val table = inspectorView.rulesView.table
    assertThat(rule.name).isEqualTo("New Rule")
    assertThat(table.selectedRow).isEqualTo(0)

    table.selectedObject!!.isActive = false
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleRemoved()).isTrue()
      assertThat(command.interceptRuleRemoved.ruleId).isEqualTo(rule.id)
    }

    table.selectedObject!!.isActive = true
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleAdded()).isTrue()
      assertThat(command.interceptRuleAdded.ruleId).isEqualTo(rule.id)
    }

    // Removing inactive rule does not send command
    table.selectedObject!!.isActive = false
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleRemoved()).isTrue()
      assertThat(command.interceptRuleRemoved.ruleId).isEqualTo(rule.id)
    }
    val remove = findAction(inspectorView.rulesView.component, "Remove")
    remove.actionPerformed(TestActionEvent())
    client.verifyLatestCommand { command ->
      assertThat(command).isEqualTo(InterceptCommand.getDefaultInstance())
    }
  }

  @Test
  fun enableAndDisableRulesFromTable() {
    val rule = addNewRule()
    val tableModel = inspectorView.rulesView.tableModel
    tableModel.setValueAt(false, 0, 0)
    assertThat(rule.isActive).isFalse()
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleRemoved()).isTrue()
      assertThat(command.interceptRuleRemoved.ruleId).isEqualTo(rule.id)
    }
    tableModel.setValueAt(true, 0, 0)
    assertThat(rule.isActive).isTrue()
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleAdded()).isTrue()
      assertThat(command.interceptRuleAdded.ruleId).isEqualTo(rule.id)
    }
  }

  @Test
  fun updateRuleNameFromDetailsView() {
    val rule = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView

    val nameComponent = findComponentWithUniqueName(ruleDetailsView, "nameTextField") as JTextField
    assertThat(nameComponent.text).isEqualTo("New Rule")
    nameComponent.text = "Test"
    nameComponent.onFocusLost()
    assertThat(rule.name).isEqualTo("Test")

    val tableModel = inspectorView.rulesView.tableModel
    assertThat(tableModel.getValueAt(0, 1)).isEqualTo("Test")
  }

  @Test
  fun updateRuleOriginFromDetailsView() {
    val rule = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val protocolComponent = findComponentWithUniqueName(ruleDetailsView, "protocolComboBox") as CommonComboBox<*, *>
    assertThat(protocolComponent.getModel().text).isEqualTo("https")
    protocolComponent.setSelectedIndex(1)

    val urlComponent = findComponentWithUniqueName(ruleDetailsView, "urlTextField") as JTextField
    assertThat(urlComponent.text).isEmpty()
    val url = "www.google.com"
    urlComponent.text = url
    urlComponent.onFocusLost()

    val portComponent = findComponentWithUniqueName(ruleDetailsView, "portTextField") as JTextField
    assertThat(portComponent.text).isEmpty()
    portComponent.text = "8080"
    portComponent.onFocusLost()

    val pathComponent = findComponentWithUniqueName(ruleDetailsView, "pathTextField") as JTextField
    assertThat(pathComponent.text).isEmpty()
    pathComponent.text = "/path"
    pathComponent.onFocusLost()

    val queryComponent = findComponentWithUniqueName(ruleDetailsView, "queryTextField") as JTextField
    assertThat(queryComponent.text).isEmpty()
    queryComponent.text = "/query"
    queryComponent.onFocusLost()

    val methodComponent = findComponentWithUniqueName(ruleDetailsView, "methodComboBox") as CommonComboBox<*, *>
    assertThat(methodComponent.getModel().text).isEqualTo("GET")
    methodComponent.setSelectedIndex(1)

    assertThat(rule.criteria.host).isEqualTo(url)
    client.verifyLatestCommand {
      it.interceptRuleAdded.rule.criteria.also { criteria ->
        assertThat(criteria.protocol).isEqualTo("http")
        assertThat(criteria.host).isEqualTo(url)
        assertThat(criteria.port).isEqualTo("8080")
        assertThat(criteria.path).isEqualTo("/path")
        assertThat(criteria.query).isEqualTo("/query")
        assertThat(criteria.method).isEqualTo("POST")
      }
    }
  }

  @Test
  fun updateStatusCodeFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val findCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val newCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox
    findCodeTextField.text = "200"
    findCodeTextField.onFocusLost()
    assertThat(newCodeTextField.isEnabled).isFalse()
    isActiveCheckBox.doClick()
    assertThat(newCodeTextField.isEnabled).isTrue()
    newCodeTextField.text = "404"
    newCodeTextField.onFocusLost()

    client.verifyLatestCommand {
      val transformation = it.interceptRuleAdded.rule.getTransformation(0)
      assertThat(transformation.hasStatusCodeReplaced()).isTrue()
      transformation.statusCodeReplaced.also { statusCodeReplaced ->
        assertThat(statusCodeReplaced.newCode).isEqualTo("404")
        assertThat(statusCodeReplaced.targetCode.type).isEqualTo(Type.PLAIN)
        assertThat(statusCodeReplaced.targetCode.text).isEqualTo("200")
      }
    }

    // Add a new rule and select the old rule back to verify if the data are saved.
    addNewRule()
    val table = inspectorView.rulesView.table
    table.selectionModel.addSelectionInterval(0, 0)
    val savedFindCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val savedNewCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val savedIsActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox
    assertThat(savedFindCodeTextField.text).isEqualTo("200")
    assertThat(savedFindCodeTextField.isEnabled).isTrue()
    assertThat(savedNewCodeTextField.text).isEqualTo("404")
    assertThat(savedIsActiveCheckBox.isSelected).isTrue()
  }

  @Test
  fun addAndRemoveHeaderAddedRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val addAction = findAction(headerTable.parent, "Add")
    val newAddedNameText = "newAddedName"
    val newAddedValueText = "newAddedValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Switches between add and replace mode
      assertThat(dialog.addRadioButton.isSelected).isTrue()
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isTrue()
      assertThat(dialog.newReplacedNameLabel.isVisibleToRoot(dialog.rootPane)).isFalse()
      dialog.replaceRadioButton.doClick()
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isFalse()
      assertThat(dialog.newReplacedNameLabel.isVisibleToRoot(dialog.rootPane)).isTrue()
      dialog.addRadioButton.doClick()

      dialog.newAddedNameLabel.text = newAddedNameText
      dialog.newAddedValueLabel.text = newAddedValueText
      dialog.clickDefaultButton()
    }

    assertThat(headerTable.rowCount).isEqualTo(1)
    assertThat(headerTable.getValueAt(0, 0)).isEqualTo(newAddedNameText)
    assertThat(headerTable.getValueAt(0, 1)).isEqualTo(newAddedValueText)
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      transformations[0].headerAdded.also { headerAdded ->
        assertThat(headerAdded.name).isEqualTo(newAddedNameText)
        assertThat(headerAdded.value).isEqualTo(newAddedValueText)
      }
    }

    headerTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(headerTable.parent, "Remove")
    removeAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(0)
    }
  }

  @Test
  fun addAndRemoveHeaderReplacedRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val addAction = findAction(headerTable.parent, "Add")
    val findNameText = "findName"
    val findValueText = "findValue"
    val replaceNameText = "replaceName"
    val replaceValueText = "replaceValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      dialog.replaceRadioButton.doClick()
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isFalse()
      assertThat(dialog.newReplacedNameLabel.isVisibleToRoot(dialog.rootPane)).isTrue()

      dialog.findNameLabel.text = findNameText
      dialog.findNameRegexCheckBox.isSelected = true
      dialog.findValueLabel.text = findValueText
      dialog.newReplacedNameLabel.text = replaceNameText
      dialog.newReplacedValueLabel.text = replaceValueText
      dialog.clickDefaultButton()
    }

    assertThat(headerTable.rowCount).isEqualTo(1)
    assertThat(headerTable.getValueAt(0, 0)).isEqualTo(findNameText)
    assertThat(headerTable.getValueAt(0, 1)).isEqualTo(findValueText)
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      transformations[0].headerReplaced.also { headerReplaced ->
        assertThat(headerReplaced.targetName.text).isEqualTo(findNameText)
        assertThat(headerReplaced.targetName.type).isEqualTo(Type.REGEX)
        assertThat(headerReplaced.targetValue.text).isEqualTo(findValueText)
        assertThat(headerReplaced.targetValue.type).isEqualTo(Type.PLAIN)
        assertThat(headerReplaced.newName).isEqualTo(replaceNameText)
        assertThat(headerReplaced.newValue).isEqualTo(replaceValueText)
      }
    }

    headerTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(headerTable.parent, "Remove")
    removeAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(0)
    }
  }

  @Test
  fun editExistingHeaderRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val addAction = findAction(headerTable.parent, "Add")
    val newAddedNameText = "newAddedName"
    val newAddedValueText = "newAddedValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      dialog.newAddedNameLabel.text = newAddedNameText
      dialog.newAddedValueLabel.text = newAddedValueText
      dialog.clickDefaultButton()
    }

    val editAction = findAction(headerTable.parent, "Edit")
    val findNameText = "findName"
    val findValueText = "findValue"
    val replaceNameText = "replaceName"
    val replaceValueText = "replaceValue"
    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Check existing rule data.
      assertThat(dialog.addRadioButton.isSelected).isTrue()
      assertThat(dialog.newAddedNameLabel.text).isEqualTo(newAddedNameText)
      assertThat(dialog.newAddedValueLabel.text).isEqualTo(newAddedValueText)

      // Change to replaced rule.
      dialog.replaceRadioButton.isSelected = true
      dialog.findNameLabel.text = findNameText
      dialog.findNameRegexCheckBox.isSelected = true
      dialog.findValueLabel.text = findValueText
      dialog.newReplacedNameLabel.text = replaceNameText
      dialog.newReplacedValueLabel.text = replaceValueText
      dialog.clickDefaultButton()
    }

    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Check existing rule data.
      assertThat(dialog.replaceRadioButton.isSelected).isTrue()
      assertThat(dialog.findNameLabel.text).isEqualTo(findNameText)
      assertThat(dialog.findValueLabel.text).isEqualTo(findValueText)
      assertThat(dialog.newReplacedNameLabel.text).isEqualTo(replaceNameText)
      assertThat(dialog.newReplacedValueLabel.text).isEqualTo(replaceValueText)
      dialog.clickDefaultButton()
    }
  }

  @Test
  fun changeHeaderRulesOrder() {
    val ruleData = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val model = ruleData.headerRuleTableModel
    val headerAddedRule = RuleData.HeaderAddedRuleData("name", "value")
    val headerReplacedRule = RuleData.HeaderReplacedRuleData(
      "findName",
      true,
      "findValue",
      false,
      "replaceName",
      "replaceValue"
    )
    model.addRow(headerAddedRule)
    model.addRow(headerReplacedRule)
    assertThat(headerTable.rowCount).isEqualTo(2)
    ruleData.toProto().let {
      assertThat(it.transformationList[0].hasHeaderAdded()).isTrue()
      assertThat(it.transformationList[1].hasHeaderReplaced()).isTrue()
    }

    headerTable.selectionModel.addSelectionInterval(0, 0)
    val moveDownAction = findAction(headerTable.parent, "Down")
    moveDownAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(2)
      assertThat(transformations[0].hasHeaderReplaced()).isTrue()
      assertThat(transformations[1].hasHeaderAdded()).isTrue()
    }
  }

  @Test
  fun addAndRemoveBodyReplacedRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val bodyTable = findComponentWithUniqueName(ruleDetailsView, "bodyRules") as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)

    val addAction = findAction(bodyTable.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      // Switches between add and replace mode

      dialog.findTextArea.text = ""
      dialog.replaceTextArea.text = "Test"
      dialog.clickDefaultButton()
    }

    assertThat(bodyTable.rowCount).isEqualTo(1)
    assertThat(bodyTable.getValueAt(0, 0)).isEqualTo("Replace All")
    assertThat(bodyTable.getValueAt(0, 1)).isEqualTo("Test")
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      assertThat(transformations[0].bodyReplaced.body.toStringUtf8()).isEqualTo("Test")
    }

    bodyTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(bodyTable.parent, "Remove")
    removeAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(0)
    }
  }

  @Test
  fun addAndRemoveBodyModifiedRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val bodyTable = findComponentWithUniqueName(ruleDetailsView, "bodyRules") as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)

    val addAction = findAction(bodyTable.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      // Switches between add and replace mode

      dialog.findTextArea.text = "Find"
      dialog.regexCheckBox.isSelected = true
      dialog.replaceTextArea.text = "Test"
      dialog.clickDefaultButton()
    }

    assertThat(bodyTable.rowCount).isEqualTo(1)
    assertThat(bodyTable.getValueAt(0, 0)).isEqualTo("Replace \"Find\"")
    assertThat(bodyTable.getValueAt(0, 1)).isEqualTo("Test")
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      transformations[0].bodyModified.apply {
        assertThat(targetText.type).isEqualTo(Type.REGEX)
        assertThat(targetText.text).isEqualTo("Find")
        assertThat(newText).isEqualTo("Test")
      }
    }

    bodyTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(bodyTable.parent, "Remove")
    removeAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(0)
    }
  }

  @Test
  fun editExistingBodyRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val bodyTable = findComponentWithUniqueName(ruleDetailsView, "bodyRules") as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)

    val addAction = findAction(bodyTable.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      dialog.findTextArea.text = ""
      dialog.replaceTextArea.text = "Test"
      dialog.clickDefaultButton()
    }

    val editAction = findAction(bodyTable.parent, "Edit")
    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      assertThat(dialog.findTextArea.text).isEmpty()
      assertThat(dialog.replaceTextArea.text).isEqualTo("Test")

      dialog.findTextArea.text = "Find"
      dialog.clickDefaultButton()
    }

    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      assertThat(dialog.findTextArea.text).isEqualTo("Find")
      assertThat(dialog.replaceTextArea.text).isEqualTo("Test")
      dialog.clickDefaultButton()
    }
  }

  @Test
  fun changeBodyRulesOrder() {
    val ruleData = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val bodyTable = findComponentWithUniqueName(ruleDetailsView, "bodyRules") as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)

    val model = ruleData.bodyRuleTableModel
    val bodyReplacedRule = RuleData.BodyReplacedRuleData("WholeReplacedBody")
    val bodyModifiedRule = RuleData.BodyModifiedRuleData("Find", true, "ReplacedBody")
    model.addRow(bodyReplacedRule)
    model.addRow(bodyModifiedRule)
    assertThat(bodyTable.rowCount).isEqualTo(2)
    ruleData.toProto().let {
      assertThat(it.transformationList[0].hasBodyReplaced()).isTrue()
      assertThat(it.transformationList[1].hasBodyModified()).isTrue()
    }

    bodyTable.selectionModel.addSelectionInterval(0, 0)
    val moveDownAction = findAction(bodyTable.parent, "Down")
    moveDownAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleAdded.rule.transformationList
      assertThat(transformations.size).isEqualTo(2)
      assertThat(transformations[0].hasBodyModified()).isTrue()
      assertThat(transformations[1].hasBodyReplaced()).isTrue()
    }
  }

  private fun addNewRule(): RuleData {
    val rulesView = inspectorView.rulesView
    val addAction = findAction(rulesView.component, "Add")
    addAction.actionPerformed(TestActionEvent())
    return model.selectedRule!!
  }

  private fun JComponent.onFocusLost() {
    focusListeners.forEach { it.focusLost(FocusEvent(this, FocusEvent.FOCUS_LOST)) }
  }

  private fun findAction(decoratedTable: Component, templateText: String) = TreeWalker(decoratedTable)
    .descendants().filterIsInstance<ActionToolbar>()[0].actions.first { it.templateText?.contains(templateText) == true }

  private fun Component.isVisibleToRoot(root: Component): Boolean {
    var current = this
    while (current != root) {
      current = current.parent
      if (!current.isVisible) {
        return false
      }
    }
    return true
  }
}
