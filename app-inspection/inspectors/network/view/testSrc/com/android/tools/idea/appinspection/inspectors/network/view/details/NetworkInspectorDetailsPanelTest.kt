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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.CommonTabbedPane
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.InterceptCommand
import java.awt.Component
import javax.swing.JPanel

@RunsInEdt
class NetworkInspectorDetailsPanelTest {

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
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), scope, object : HttpDataModel {
      private val dataList = listOf(DEFAULT_DATA)
      override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
        return dataList.filter { it.requestStartTimeUs >= timeCurrentRangeUs.min && it.requestStartTimeUs <= timeCurrentRangeUs.max }
      }
    })
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    inspectorView = NetworkInspectorView(projectRule.project, model, FakeUiComponentsProvider(), component, services, scope)
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
  fun viewIsVisibleWhenDataIsNotNull() {
    detailsPanel.isVisible = false
    model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
    model.setSelectedConnection(DEFAULT_DATA)
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isTrue()
    // Setting a null rule does not matter when we have a non-null connection data.
    model.setSelectedRule(null)
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isTrue()
    assertThat(detailsPanel.ruleDetailsView.isVisible).isFalse()
    model.setSelectedConnection(null)
    assertThat(detailsPanel.isVisible).isFalse()

    model.detailContent = NetworkInspectorModel.DetailContent.RULE
    model.setSelectedRule(RuleData(1, "NewRule", true))
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.ruleDetailsView.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isFalse()
    // Setting a null connection does not matter when we have a non-null rule data.
    model.setSelectedConnection(null)
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.ruleDetailsView.isVisible).isTrue()
    model.setSelectedRule(null)
    assertThat(detailsPanel.isVisible).isFalse()
  }

  @Test
  fun openAndCLoseDetailsPanelWhenSwitchingTabs() {
    val tabs = inspectorView.connectionsView.component.findParentIsInstance<CommonTabbedPane>()
    assertThat(tabs.selectedIndex == 0)
    detailsPanel.isVisible = false
    model.detailContent = NetworkInspectorModel.DetailContent.CONNECTION
    model.setSelectedConnection(DEFAULT_DATA)
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isTrue()

    // Switching between connection and threads view does not change details panel.
    tabs.selectedIndex = 1
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isTrue()

    // Switch to Rules tab
    tabs.selectedIndex = 2
    assertThat(detailsPanel.isVisible).isFalse()

    // Add a new rule.
    model.detailContent = NetworkInspectorModel.DetailContent.RULE
    model.setSelectedRule(RuleData(1, "NewRule", true))
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.ruleDetailsView.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isFalse()

    // Switching back to connections tab opens the selected connection.
    tabs.selectedIndex = 0
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isTrue()

    // Switching back to rule tab opens the selected rule.
    tabs.selectedIndex = 2
    assertThat(detailsPanel.isVisible).isTrue()
    assertThat(detailsPanel.ruleDetailsView.isVisible).isTrue()
    assertThat(detailsPanel.connectionDetailsView.isVisible).isFalse()
  }

  private inline fun <reified R : Component> Component.findParentIsInstance(): R {
    var component = this
    while (component !is R) {
      component = component.parent
    }
    return component
  }
}
