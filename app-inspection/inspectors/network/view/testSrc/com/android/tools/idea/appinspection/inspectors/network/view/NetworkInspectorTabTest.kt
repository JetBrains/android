/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.stdui.CommonToggleButton
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.network.model.CodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClientImpl
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol
import java.util.concurrent.Executors
import javax.swing.JPanel

class NetworkInspectorTabTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun pressActionButtons() = runBlocking<Unit> {
    val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    val timer = FakeTimer()
    timer.start()
    val services = TestNetworkInspectorServices(
      FakeCodeNavigationProvider(),
      timer,
      NetworkInspectorClientImpl(
        object : AppInspectorMessenger {
          override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
            return NetworkInspectorProtocol.Response.newBuilder().apply {
              startInspectionResponse = NetworkInspectorProtocol.StartInspectionResponse.newBuilder().apply {
                timestamp = 12345
              }.build()
            }.build().toByteArray()
          }

          override val eventFlow = emptyFlow<ByteArray>()
          override val scope = scope
        }
      )
    )
    val tab = NetworkInspectorTab(
      projectRule.project,
      FakeUiComponentsProvider(),
      FakeNetworkInspectorDataSource(),
      services,
      scope,
      projectRule.fixture.testRootDisposable
    )

    tab.launchJob.join()

    assertThat(tab.actionsToolBar.componentCount).isEqualTo(5)

    val zoomOut = tab.actionsToolBar.getComponent(0) as CommonButton
    val zoomIn = tab.actionsToolBar.getComponent(1) as CommonButton
    val resetZoom = tab.actionsToolBar.getComponent(2) as CommonButton
    val zoomToSelection = tab.actionsToolBar.getComponent(3) as CommonButton
    val subToolBar = tab.actionsToolBar.getComponent(4) as JPanel
    val goLive = subToolBar.getComponent(1) as CommonToggleButton

    tab.model.timeline.selectionRange[0.0] = 4.0
    zoomToSelection.doClick()
    timer.step()
    assertThat(tab.model.timeline.viewRange.isSameAs(Range(0.0, 4.0)))
    assertThat(tab.model.timeline.isStreaming).isFalse()

    val defaultViewRange = Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    zoomIn.doClick()
    timer.step()
    val zoomedInViewRange = Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    assertThat(zoomedInViewRange.length).isLessThan(defaultViewRange.length)

    zoomOut.doClick()
    timer.step()
    val zoomedOutViewRange = Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    assertThat(zoomedOutViewRange.length).isGreaterThan(zoomedInViewRange.length)

    resetZoom.doClick()
    timer.step()
    assertThat(tab.model.timeline.viewRange.length).isGreaterThan(defaultViewRange.length)

    goLive.doClick()
    assertThat(tab.model.timeline.isStreaming).isTrue()
    goLive.doClick()
    assertThat(tab.model.timeline.isStreaming).isFalse()
  }
}