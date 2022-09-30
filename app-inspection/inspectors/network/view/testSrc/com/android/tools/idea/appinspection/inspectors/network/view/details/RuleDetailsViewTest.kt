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
import com.android.tools.idea.appinspection.inspectors.network.ide.analytics.IdeNetworkInspectorTracker
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
import com.android.tools.idea.appinspection.inspectors.network.view.TestNetworkInspectorUsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AppInspectionEvent.NetworkInspectorEvent
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.components.JBLabel
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
import studio.network.inspection.NetworkInspectorProtocol.InterceptCriteria
import studio.network.inspection.NetworkInspectorProtocol.MatchingText.Type
import java.awt.Component
import java.awt.event.FocusEvent
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

@RunsInEdt
class RuleDetailsViewTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    private var latestRegularCommand = InterceptCommand.getDefaultInstance()
    private var latestReorderCommand = InterceptCommand.getDefaultInstance()

    override suspend fun getStartTimeStampNs() = 0L

    override suspend fun interceptResponse(command: InterceptCommand) {
      if (command.hasReorderInterceptRules()) {
        latestReorderCommand = command
      }
      else {
        latestRegularCommand = command
      }
    }

    /**
     * Verifies the latest command not for reordering.
     */
    fun verifyLatestCommand(checker: (InterceptCommand) -> Unit) {
      checker(latestRegularCommand)
      latestRegularCommand = InterceptCommand.getDefaultInstance()
    }

    /**
     * Verifies the latest command for reordering.
     */
    fun verifyLatestReorderCommand(checker: (InterceptCommand) -> Unit) {
      checker(latestReorderCommand)
      latestReorderCommand = InterceptCommand.getDefaultInstance()
    }
  }

  @get:Rule
  val setFlagRule = SetFlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val testRootDisposable
    get() = projectRule.fixture.testRootDisposable

  private lateinit var client: TestNetworkInspectorClient
  private lateinit var tracker: TestNetworkInspectorUsageTracker
  private lateinit var services: TestNetworkInspectorServices
  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var detailsPanel: NetworkInspectorDetailsPanel
  private val timer: FakeTimer = FakeTimer()
  private lateinit var scope: CoroutineScope

  @Before
  fun before() {
    enableHeadlessDialogs(testRootDisposable)
    TestDialogManager.setTestDialog(TestDialog.YES)

    val codeNavigationProvider = FakeCodeNavigationProvider()
    client = TestNetworkInspectorClient()
    tracker = TestNetworkInspectorUsageTracker()
    Disposer.register(testRootDisposable, tracker)
    services = TestNetworkInspectorServices(
      codeNavigationProvider,
      timer,
      client,
      IdeNetworkInspectorTracker(projectRule.project)
    )
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), scope, object : HttpDataModel {
      override fun getData(timeCurrentRangeUs: Range) = listOf<HttpData>()
    })
    model.detailContent = NetworkInspectorModel.DetailContent.RULE
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    inspectorView = NetworkInspectorView(projectRule.project, model, FakeUiComponentsProvider(), component, services, scope)
    parentPanel.add(inspectorView.component)
    detailsPanel = inspectorView.detailsPanel
  }

  @After
  fun tearDown() {
    TestDialogManager.setTestDialog(null)
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
    TestDialogManager.setTestDialog(TestDialog.NO)
    remove.actionPerformed(TestActionEvent())
    assertThat(table.selectedRow).isEqualTo(1)
    TestDialogManager.setTestDialog(TestDialog.YES)
    remove.actionPerformed(TestActionEvent())
    assertThat(table.selectedRow).isEqualTo(0)
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
      assertThat(command.hasInterceptRuleUpdated()).isTrue()
      assertThat(command.interceptRuleUpdated.ruleId).isEqualTo(rule.id)
      assertThat(command.interceptRuleUpdated.rule.enabled).isFalse()
    }

    table.selectedObject!!.isActive = true
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleUpdated()).isTrue()
      assertThat(command.interceptRuleUpdated.ruleId).isEqualTo(rule.id)
      assertThat(command.interceptRuleUpdated.rule.enabled).isTrue()
    }

    table.selectedObject!!.isActive = false
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleUpdated()).isTrue()
      assertThat(command.interceptRuleUpdated.ruleId).isEqualTo(rule.id)
      assertThat(command.interceptRuleUpdated.rule.enabled).isFalse()
    }
    val remove = findAction(inspectorView.rulesView.component, "Remove")
    remove.actionPerformed(TestActionEvent())
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleRemoved()).isTrue()
      assertThat(command.interceptRuleRemoved.ruleId).isEqualTo(rule.id)
    }
  }

  @Test
  fun reorderRulesFromTable() {
    val rule1 = addNewRule()
    val rule2 = addNewRule()
    val table = inspectorView.rulesView.table
    assertThat(table.selectedRow).isEqualTo(1)

    val moveUp = findAction(inspectorView.rulesView.component, "Up")
    moveUp.actionPerformed(TestActionEvent())
    assertThat(table.selectedRow).isEqualTo(0)
    client.verifyLatestReorderCommand { command ->
      assertThat(command.reorderInterceptRules.ruleIdList).isEqualTo(listOf(rule2.id, rule1.id))
    }

    val moveDown = findAction(inspectorView.rulesView.component, "Down")
    moveDown.actionPerformed(TestActionEvent())
    assertThat(table.selectedRow).isEqualTo(1)
    client.verifyLatestReorderCommand { command ->
      assertThat(command.reorderInterceptRules.ruleIdList).isEqualTo(listOf(rule1.id, rule2.id))
    }
  }

  @Test
  fun enableAndDisableRulesFromTable() {
    val rule = addNewRule()
    val tableModel = inspectorView.rulesView.tableModel
    tableModel.setValueAt(false, 0, 0)
    assertThat(rule.isActive).isFalse()
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleUpdated()).isTrue()
      assertThat(command.interceptRuleUpdated.ruleId).isEqualTo(rule.id)
      assertThat(command.interceptRuleUpdated.rule.enabled).isFalse()
    }
    tableModel.setValueAt(true, 0, 0)
    assertThat(rule.isActive).isTrue()
    client.verifyLatestCommand { command ->
      assertThat(command.hasInterceptRuleUpdated()).isTrue()
      assertThat(command.interceptRuleUpdated.ruleId).isEqualTo(rule.id)
      assertThat(command.interceptRuleUpdated.rule.enabled).isTrue()
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
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.URL_PROTOCOL)
    }

    val urlComponent = findComponentWithUniqueName(ruleDetailsView, "urlTextField") as JTextField
    assertThat(urlComponent.text).isEmpty()
    val url = "www.google.com"
    urlComponent.text = url
    urlComponent.onFocusLost()
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.URL_HOST)
    }

    val portComponent = findComponentWithUniqueName(ruleDetailsView, "portTextField") as JTextField
    assertThat(portComponent.text).isEmpty()
    portComponent.text = "8080"
    portComponent.onFocusLost()
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.URL_PORT)
    }

    val pathComponent = findComponentWithUniqueName(ruleDetailsView, "pathTextField") as JTextField
    assertThat(pathComponent.text).isEmpty()
    pathComponent.text = "/path"
    pathComponent.onFocusLost()
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.URL_PATH)
    }

    val queryComponent = findComponentWithUniqueName(ruleDetailsView, "queryTextField") as JTextField
    assertThat(queryComponent.text).isEmpty()
    queryComponent.text = "title=Query_string&action=edit"
    queryComponent.onFocusLost()
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.URL_QUERY)
    }

    val methodComponent = findComponentWithUniqueName(ruleDetailsView, "methodComboBox") as CommonComboBox<*, *>
    assertThat(methodComponent.getModel().text).isEqualTo("GET")
    methodComponent.setSelectedIndex(1)

    assertThat(rule.criteria.host).isEqualTo(url)
    assertThat(inspectorView.rulesView.table.getValueAt(0, 2)).isEqualTo("http://www.google.com:8080/path?title=Query_string&action=edit")
    client.verifyLatestCommand {
      it.interceptRuleUpdated.rule.criteria.also { criteria ->
        assertThat(criteria.protocol).isEqualTo(InterceptCriteria.Protocol.PROTOCOL_HTTP)
        assertThat(criteria.host).isEqualTo(url)
        assertThat(criteria.port).isEqualTo("8080")
        assertThat(criteria.path).isEqualTo("/path")
        assertThat(criteria.query).isEqualTo("title=Query_string&action=edit")
        assertThat(criteria.method).isEqualTo(InterceptCriteria.Method.METHOD_POST)
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

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_CODE)
    }
    assertThat(newCodeTextField.isEnabled).isFalse()
    isActiveCheckBox.doClick()
    assertThat(newCodeTextField.isEnabled).isTrue()
    newCodeTextField.text = "404"
    newCodeTextField.onFocusLost()

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_REPLACE_CODE)
    }
    client.verifyLatestCommand {
      val transformation = it.interceptRuleUpdated.rule.getTransformation(0)
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

    val addAction = findAction(headerTable.parent.parent.parent, "Add")
    val newAddedNameText = "newAddedName"
    val newAddedValueText = "newAddedValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Switches between add and replace mode
      assertThat(dialog.tabs.selectedComponent).isEqualTo(dialog.newHeaderPanel)
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isTrue()
      assertThat(dialog.newReplacedNameTextField.isVisibleToRoot(dialog.rootPane)).isFalse()
      dialog.tabs.selectedComponent = dialog.editHeaderPanel
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isFalse()
      assertThat(dialog.newReplacedNameTextField.isVisibleToRoot(dialog.rootPane)).isTrue()
      dialog.tabs.selectedComponent = dialog.newHeaderPanel

      dialog.newAddedNameLabel.text = newAddedNameText
      dialog.newAddedValueLabel.text = newAddedValueText
      dialog.clickDefaultButton()
    }
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.ADD_HEADER)
    }

    assertThat(headerTable.rowCount).isEqualTo(1)
    assertThat(headerTable.getValueAt(0, 0)).isEqualTo("Add")
    assertThat(headerTable.getValueAt(0, 1)).isEqualTo(newAddedNameText to null)
    assertThat(headerTable.getValueAt(0, 2)).isEqualTo(newAddedValueText to null)
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      transformations[0].headerAdded.also { headerAdded ->
        assertThat(headerAdded.name).isEqualTo(newAddedNameText)
        assertThat(headerAdded.value).isEqualTo(newAddedValueText)
      }
    }

    headerTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(headerTable.parent.parent.parent, "Remove")
    TestDialogManager.setTestDialog(TestDialog.NO)
    assertThat(headerTable.rowCount).isEqualTo(1)
    removeAction.actionPerformed(TestActionEvent())
    TestDialogManager.setTestDialog(TestDialog.YES)
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

    val addAction = findAction(headerTable.parent.parent.parent, "Add")
    val findNameText = "findName"
    val findValueText = "findValue"
    val replaceNameText = "replaceName"
    val replaceValueText = "replaceValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      dialog.tabs.selectedComponent = dialog.editHeaderPanel
      assertThat(dialog.newAddedNameLabel.isVisibleToRoot(dialog.rootPane)).isFalse()
      assertThat(dialog.newReplacedNameTextField.isVisibleToRoot(dialog.rootPane)).isTrue()

      dialog.findNameCheckBox.isSelected = true
      dialog.findNameTextField.text = findNameText
      dialog.findNameRegexCheckBox.isSelected = true
      dialog.findValueCheckBox.isSelected = true
      dialog.findValueTextField.text = findValueText
      dialog.replaceNameCheckBox.isSelected = true
      dialog.newReplacedNameTextField.text = replaceNameText
      dialog.replaceValueCheckBox.isSelected = true
      dialog.newReplacedValueTextField.text = replaceValueText
      dialog.clickDefaultButton()
    }

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_REPLACE_HEADER)
    }
    assertThat(headerTable.rowCount).isEqualTo(1)
    assertThat(headerTable.getValueAt(0, 0)).isEqualTo("Edit")
    assertThat(headerTable.getValueAt(0, 1)).isEqualTo(findNameText to replaceNameText)
    assertThat(headerTable.getValueAt(0, 2)).isEqualTo(findValueText to replaceValueText)
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
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
    val removeAction = findAction(headerTable.parent.parent.parent, "Remove")
    removeAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(0)
    }
  }

  @Test
  fun editExistingHeaderRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val addAction = findAction(headerTable.parent.parent.parent, "Add")
    val newAddedNameText = "newAddedName"
    val newAddedValueText = "newAddedValue"
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      dialog.newAddedNameLabel.text = newAddedNameText
      dialog.newAddedValueLabel.text = newAddedValueText
      dialog.clickDefaultButton()
    }
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.ADD_HEADER)
    }

    val editAction = findAction(headerTable.parent.parent.parent, "Edit")
    val findNameText = "findName"
    val findValueText = "findValue"
    val replaceNameText = "replaceName"
    val replaceValueText = "replaceValue"
    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Check existing rule data.
      assertThat(dialog.tabs.selectedComponent).isEqualTo(dialog.newHeaderPanel)
      assertThat(dialog.newAddedNameLabel.text).isEqualTo(newAddedNameText)
      assertThat(dialog.newAddedValueLabel.text).isEqualTo(newAddedValueText)

      // Change to replaced rule.
      dialog.tabs.selectedComponent = dialog.editHeaderPanel
      dialog.findNameCheckBox.isSelected = true
      dialog.findNameTextField.text = findNameText
      dialog.findNameRegexCheckBox.isSelected = true
      dialog.findValueCheckBox.isSelected = true
      dialog.findValueTextField.text = findValueText
      dialog.replaceNameCheckBox.isSelected = true
      dialog.newReplacedNameTextField.text = replaceNameText
      dialog.replaceValueCheckBox.isSelected = true
      dialog.newReplacedValueTextField.text = replaceValueText
      dialog.clickDefaultButton()
    }
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_REPLACE_HEADER)
    }

    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as HeaderRuleDialog
      // Check existing rule data.
      assertThat(dialog.tabs.selectedComponent).isEqualTo(dialog.editHeaderPanel)
      assertThat(dialog.findNameTextField.text).isEqualTo(findNameText)
      assertThat(dialog.findValueTextField.text).isEqualTo(findValueText)
      assertThat(dialog.newReplacedNameTextField.text).isEqualTo(replaceNameText)
      assertThat(dialog.newReplacedValueTextField.text).isEqualTo(replaceValueText)
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
    val moveDownAction = findAction(headerTable.parent.parent.parent, "Down")
    moveDownAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(2)
      assertThat(transformations[0].hasHeaderReplaced()).isTrue()
      assertThat(transformations[1].hasHeaderAdded()).isTrue()
    }
  }

  @Test
  fun partialEditHeaderRule() {
    val ruleData = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val headerTable = findComponentWithUniqueName(ruleDetailsView, "headerRules") as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)

    val model = ruleData.headerRuleTableModel
    val headerAddedRule = RuleData.HeaderAddedRuleData("name", "value")
    val headerReplacedRule = RuleData.HeaderReplacedRuleData(
      "findName",
      true,
      null,
      false,
      null,
      "replaceValue"
    )
    model.addRow(headerAddedRule)
    model.addRow(headerReplacedRule)
    assertThat(headerTable.rowCount).isEqualTo(2)
    ruleData.toProto().let {
      assertThat(it.transformationList[0].hasHeaderAdded()).isTrue()
      assertThat(it.transformationList[1].hasHeaderReplaced()).isTrue()
      assertThat(it.transformationList[1].headerReplaced.targetName.text).isEqualTo("findName")
      assertThat(it.transformationList[1].headerReplaced.hasTargetValue()).isFalse()
      assertThat(it.transformationList[1].headerReplaced.hasNewName()).isFalse()
      assertThat(it.transformationList[1].headerReplaced.newValue).isEqualTo("replaceValue")
    }
  }

  @Test
  fun addAndRemoveBodyReplacedRulesFromDetailsView() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val bodyTable = findComponentWithUniqueName(ruleDetailsView, "bodyRules") as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)

    val addAction = findAction(bodyTable.parent.parent.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      // Switches between add and replace mode

      assertThat(dialog.isOKActionEnabled).isFalse()
      dialog.replaceEntireBodyCheckBox.isSelected = true
      assertThat(dialog.findTextArea.isEnabled).isFalse()
      assertThat(dialog.regexCheckBox.isEnabled).isFalse()
      dialog.replaceTextArea.text = "Test"
      assertThat(dialog.isOKActionEnabled).isTrue()
      dialog.clickDefaultButton()
    }

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.REPLACE_BODY)
    }
    assertThat(bodyTable.rowCount).isEqualTo(1)
    assertThat(bodyTable.getValueAt(0, 0)).isEqualTo("Replace")
    assertThat(bodyTable.getValueAt(0, 1)).isEqualTo("")
    assertThat(bodyTable.getValueAt(0, 2)).isEqualTo("Test")
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      assertThat(transformations[0].bodyReplaced.body.toStringUtf8()).isEqualTo("Test")
    }

    bodyTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(bodyTable.parent.parent.parent, "Remove")
    TestDialogManager.setTestDialog(TestDialog.NO)
    removeAction.actionPerformed(TestActionEvent())
    assertThat(bodyTable.rowCount).isEqualTo(1)
    TestDialogManager.setTestDialog(TestDialog.YES)
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

    val addAction = findAction(bodyTable.parent.parent.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      dialog.replaceEntireBodyCheckBox.isSelected = false
      assertThat(dialog.findTextArea.isEnabled).isTrue()
      assertThat(dialog.regexCheckBox.isEnabled).isTrue()
      dialog.findTextArea.text = "  "
      assertThat(dialog.isOKActionEnabled).isFalse()
      dialog.findTextArea.text = "Find"
      dialog.regexCheckBox.isSelected = true
      dialog.replaceTextArea.text = "Test"
      assertThat(dialog.isOKActionEnabled).isTrue()
      dialog.clickDefaultButton()
    }

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_REPLACE_BODY)
    }
    assertThat(bodyTable.rowCount).isEqualTo(1)
    assertThat(bodyTable.getValueAt(0, 0)).isEqualTo("Edit")
    assertThat(bodyTable.getValueAt(0, 1)).isEqualTo("Find")
    assertThat(bodyTable.getValueAt(0, 2)).isEqualTo("Test")
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(1)
      transformations[0].bodyModified.apply {
        assertThat(targetText.type).isEqualTo(Type.REGEX)
        assertThat(targetText.text).isEqualTo("Find")
        assertThat(newText).isEqualTo("Test")
      }
    }

    bodyTable.selectionModel.addSelectionInterval(0, 0)
    val removeAction = findAction(bodyTable.parent.parent.parent, "Remove")
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

    val addAction = findAction(bodyTable.parent.parent.parent, "Add")
    createModalDialogAndInteractWithIt({ addAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      dialog.replaceEntireBodyCheckBox.isSelected = true
      dialog.replaceTextArea.text = "Test"
      dialog.clickDefaultButton()
    }

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.REPLACE_BODY)
    }
    val editAction = findAction(bodyTable.parent.parent.parent, "Edit")
    createModalDialogAndInteractWithIt({ editAction.actionPerformed(TestActionEvent()) }) {
      val dialog = it as BodyRuleDialog
      assertThat(dialog.replaceEntireBodyCheckBox.isSelected).isTrue()
      assertThat(dialog.replaceTextArea.text).isEqualTo("Test")

      dialog.replaceEntireBodyCheckBox.isSelected = false
      dialog.findTextArea.text = "Find"
      dialog.clickDefaultButton()
    }

    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
      assertThat(it.ruleDetailUpdated.component).isEqualTo(NetworkInspectorEvent.RuleUpdatedEvent.Component.FIND_REPLACE_BODY)
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
    val moveDownAction = findAction(bodyTable.parent.parent.parent, "Down")
    moveDownAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      val transformations = it.interceptRuleUpdated.rule.transformationList
      assertThat(transformations.size).isEqualTo(2)
      assertThat(transformations[0].hasBodyModified()).isTrue()
      assertThat(transformations[1].hasBodyReplaced()).isTrue()
    }
  }

  @Test
  fun warningShownAndRuleNotUpdatedOnInvalidUrl() {
    val invalidUrl = "www google com"
    val rule = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView

    val urlComponent = findComponentWithUniqueName(ruleDetailsView, "urlTextField") as JTextField
    assertThat(urlComponent.text).isEmpty()
    urlComponent.text = invalidUrl
    urlComponent.onFocusLost()

    val urlWarningLabel = findComponentWithUniqueName(ruleDetailsView, "urlWarningLabel") as JBLabel
    assert(urlWarningLabel.isVisible)

    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }

    assertThat(rule.criteria.host).isNotEqualTo(invalidUrl)

    // Assert URL shown in table is the default URL
    assertThat(inspectorView.rulesView.table.getValueAt(0, 2)).isEqualTo("https://*")
    client.verifyLatestCommand {
      it.interceptRuleUpdated.rule.criteria.also { criteria ->
        assertThat(criteria.host).isNotEqualTo(invalidUrl)
        assertThat(criteria.host).isEqualTo("")
      }
    }
  }

  @Test
  fun warningShownAndRuleNotUpdatedOnInvalidPort() {
    val invalidPort = "-12345"
    val rule = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView

    val portComponent = findComponentWithUniqueName(ruleDetailsView, "portTextField") as JTextField
    val portWarningLabel = findComponentWithUniqueName(ruleDetailsView, "portWarningLabel") as JBLabel
    assertThat(portComponent.text).isEmpty()
    portComponent.text = invalidPort
    portComponent.onFocusLost()

    assert(portWarningLabel.isVisible)

    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }

    assertThat(rule.criteria.port).isNotEqualTo(invalidPort)

    // Assert URL shown in table is the default URL
    assertThat(inspectorView.rulesView.table.getValueAt(0, 2)).isEqualTo("https://*")
    client.verifyLatestCommand {
      it.interceptRuleUpdated.rule.criteria.also { criteria ->
        assertThat(criteria.port).isNotEqualTo(invalidPort)
        assertThat(criteria.port).isEqualTo("")
      }
    }
  }

  @Test
  fun warningShownForInvalidFindStatusCode() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val findCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val findCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "findCodeWarningLabel") as JBLabel
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox

    findCodeTextField.text = "ABC"
    findCodeTextField.onFocusLost()
    isActiveCheckBox.doClick()

    assert(findCodeWarningLabel.isVisible)


    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }
  }


  @Test
  fun warningShownForInvalidNewStatusCode() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val newCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val newCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "newCodeWarningLabel") as JBLabel
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox
    isActiveCheckBox.doClick()

    // Warning label is visible since the current input is "" which is invalid
    assert(newCodeWarningLabel.isVisible)

    newCodeTextField.text = "ABC"
    newCodeTextField.onFocusLost()

    assert(newCodeWarningLabel.isVisible)

    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }
  }

  @Test
  fun ruleNotUpdatedWhenNewCodeIsBlank() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val findCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val newCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox

    findCodeTextField.text = "123"
    isActiveCheckBox.doClick()
    assert(newCodeTextField.text.isBlank())

    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }
  }

  @Test
  fun ruleNotUpdatedWhenBothStatusCodeBlank() {
    addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val findCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val findCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "findCodeWarningLabel") as JBLabel
    val newCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val newCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "newCodeWarningLabel") as JBLabel
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox

    isActiveCheckBox.doClick()

    assert(findCodeTextField.text.isBlank())
    assert(findCodeWarningLabel.isVisible)
    assert(newCodeTextField.text.isBlank())
    assert(newCodeWarningLabel.isVisible)

    tracker.verifyLatestEvent {
      assertThat(it.type).isNotEqualTo(NetworkInspectorEvent.Type.RULE_UPDATED)
    }
  }

  @Test
  fun statusCodeInactiveWhenAnyStatusCodeInvalid() {
    val rule = addNewRule()
    val ruleDetailsView = detailsPanel.ruleDetailsView
    val findCodeTextField = findComponentWithUniqueName(ruleDetailsView, "findCodeTextField") as JTextField
    val findCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "findCodeWarningLabel") as JBLabel
    val newCodeTextField = findComponentWithUniqueName(ruleDetailsView, "newCodeTextField") as JTextField
    val newCodeWarningLabel = findComponentWithUniqueName(ruleDetailsView, "newCodeWarningLabel") as JBLabel
    val isActiveCheckBox = TreeWalker(ruleDetailsView).descendantStream().filter { it is JCheckBox }.getIfSingle() as JCheckBox

    findCodeTextField.text = "ABC"
    findCodeTextField.onFocusLost()

    assert(findCodeWarningLabel.isVisible)
    assert(!rule.statusCodeRuleData.isActive)

    isActiveCheckBox.doClick()

    assert(newCodeWarningLabel.isVisible)
    assert(!rule.statusCodeRuleData.isActive)

    newCodeTextField.text = "DEF"
    newCodeTextField.onFocusLost()

    assert(newCodeWarningLabel.isVisible)
    assert(!rule.statusCodeRuleData.isActive)

    // Valid new code. Rule should not update since find code is still invalid
    newCodeTextField.text = "123"
    newCodeTextField.onFocusLost()

    assert(!newCodeWarningLabel.isVisible)
    assert(!rule.statusCodeRuleData.isActive)

    // Valid find code. Invalid new code. Rule should not update
    newCodeTextField.text = "DEF"
    findCodeTextField.text =  "123"
    newCodeTextField.onFocusLost()
    findCodeTextField.onFocusLost()

    assert(newCodeWarningLabel.isVisible)
    assert(!findCodeWarningLabel.isVisible)
    assert(!rule.statusCodeRuleData.isActive)

    newCodeTextField.text = "456"
    newCodeTextField.onFocusLost()

    assert(!newCodeWarningLabel.isVisible)
    assert(rule.statusCodeRuleData.isActive)
  }

  private fun addNewRule(): RuleData {
    val rulesView = inspectorView.rulesView
    val addAction = findAction(rulesView.component, "Add")
    addAction.actionPerformed(TestActionEvent())
    client.verifyLatestCommand {
      assertThat(it.hasInterceptRuleAdded()).isTrue()
      assertThat(it.interceptRuleAdded.ruleId).isEqualTo(RuleData.getLatestId())
    }
    tracker.verifyLatestEvent {
      assertThat(it.type).isEqualTo(NetworkInspectorEvent.Type.RULE_CREATED)
    }
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
