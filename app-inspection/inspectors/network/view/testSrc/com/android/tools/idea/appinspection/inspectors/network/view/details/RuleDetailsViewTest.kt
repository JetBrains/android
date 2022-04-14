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
import com.android.tools.adtui.stdui.TooltipLayeredPane
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
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
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
import java.awt.Component
import java.awt.Container
import java.awt.event.FocusEvent
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

@RunsInEdt
class RuleDetailsViewTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    override suspend fun getStartTimeStampNs() = 0L

    override suspend fun interceptResponse(command: InterceptCommand) = Unit
  }

  @get:Rule
  val setFlagRule = SetFlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private lateinit var client: TestNetworkInspectorClient
  private lateinit var services: TestNetworkInspectorServices
  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var detailsPanel: NetworkInspectorDetailsPanel
  private val timer: FakeTimer = FakeTimer()
  private lateinit var scope: CoroutineScope
  private lateinit var disposable: Disposable

  @Before
  fun before() {
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
    disposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    scope.cancel()
    Disposer.dispose(disposable)
  }

  @Test
  fun displayRuleDetailsInfo() {
    val rule = RuleData(1, "", true)
    rule.name = "Test"
    model.setSelectedRule(rule)
    val ruleDetailsView = detailsPanel.ruleDetailsView

    val nameComponent = ruleDetailsView.getValueComponent("Name") as JTextField
    assertThat(nameComponent.text).isEqualTo("Test")
    nameComponent.text = "Test2"
    nameComponent.onFocusLost()
    assertThat(rule.name).isEqualTo("Test2")

    val originPanel = ruleDetailsView.getCategoryPanel("Origin") as JPanel
    val urlComponent = originPanel.getValueComponent("Host url") as JTextField
    assertThat(urlComponent.text).isEmpty()
    val url = "www.google.com"
    urlComponent.text = url
    urlComponent.onFocusLost()
    assertThat(rule.criteria.host).isEqualTo(url)

    val headerPanel = ruleDetailsView.getCategoryPanel("Header rules") as JPanel
    val headerTable = TreeWalker(headerPanel).descendantStream().filter { it is TableView<*> }.getIfSingle() as TableView<*>
    assertThat(headerTable.rowCount).isEqualTo(0)
    val headerAddedRule = RuleData.HeaderAddedRuleData("header", "value")
    rule.headerRuleTableModel.insertRow(0, headerAddedRule)
    assertThat(headerTable.rowCount).isEqualTo(1)
    assertThat(headerTable.items[0]).isEqualTo(headerAddedRule)

    val headerReplacedRule = RuleData.HeaderReplacedRuleData(
      "findHeader",
      false,
      "findValue",
      false,
      "newHeader",
      "newName"
    )
    rule.headerRuleTableModel.insertRow(0, headerReplacedRule)
    assertThat(headerTable.rowCount).isEqualTo(2)
    assertThat(headerTable.items[0]).isEqualTo(headerReplacedRule)


    val bodyPanel = ruleDetailsView.getCategoryPanel("Body rules") as JPanel
    val bodyTable = TreeWalker(bodyPanel).descendantStream().filter { it is TableView<*> }.getIfSingle() as TableView<*>
    assertThat(bodyTable.rowCount).isEqualTo(0)
    val bodyReplacedRule = RuleData.BodyReplacedRuleData("body")
    rule.bodyRuleTableModel.insertRow(0, bodyReplacedRule)
    assertThat(bodyTable.rowCount).isEqualTo(1)
    assertThat(bodyTable.items[0]).isEqualTo(bodyReplacedRule)

    val bodyModifiedRule = RuleData.BodyModifiedRuleData("body", true, "newBody")
    rule.bodyRuleTableModel.insertRow(0, bodyModifiedRule)
    assertThat(bodyTable.rowCount).isEqualTo(2)
    assertThat(bodyTable.items[0]).isEqualTo(bodyModifiedRule)
  }

  private fun JComponent.getValueComponent(key: String): Component = getCategoryPanel(key).getComponent(1)

  private fun JComponent.getCategoryPanel(key: String): Container = findLabels(key).findFirst().get().parent.parent

  private fun JComponent.findLabels(text: String): Stream<Component> =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == text }

  private fun JComponent.onFocusLost() {
    focusListeners.forEach { it.focusLost(FocusEvent(this, FocusEvent.FOCUS_LOST)) }
  }
}
