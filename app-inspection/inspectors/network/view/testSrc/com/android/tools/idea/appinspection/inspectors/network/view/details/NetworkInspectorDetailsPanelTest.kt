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
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.rules.RulesTableModel
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
import org.junit.rules.RuleChain
import javax.swing.JPanel

@RunsInEdt
class NetworkInspectorDetailsPanelTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    private var lastInterceptedUrl: String? = null
    private var lastInterceptedBody: String? = null

    override suspend fun getStartTimeStampNs() = 0L

    override suspend fun interceptResponse(url: String, body: String) {
      lastInterceptedUrl = url
      lastInterceptedBody = body
    }
  }

  private val setFlagRule = SetFlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule
  val ruleChain = RuleChain.outerRule(ProjectRule()).around(EdtRule()).around(setFlagRule)!!

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
      private val dataList = listOf(DEFAULT_DATA)
      override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
        return dataList.filter { it.requestStartTimeUs >= timeCurrentRangeUs.min && it.requestStartTimeUs <= timeCurrentRangeUs.max }
      }
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
  fun viewIsVisibleWhenDataIsNotNull() {
    detailsPanel.isVisible = false
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

    model.setSelectedRule(RulesTableModel.RuleInfo())
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
}
