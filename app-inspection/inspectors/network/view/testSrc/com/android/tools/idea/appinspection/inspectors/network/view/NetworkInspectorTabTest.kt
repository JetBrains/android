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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClientImpl
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.view.utils.findComponentWithUniqueName
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol

class NetworkInspectorTabTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
  private val timer = FakeTimer().apply { start() }

  private val services =
    TestNetworkInspectorServices(
      FakeCodeNavigationProvider(),
      timer,
      NetworkInspectorClientImpl(
        object : AppInspectorMessenger {
          override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
            return NetworkInspectorProtocol.Response.newBuilder()
              .apply {
                startInspectionResponse =
                  NetworkInspectorProtocol.StartInspectionResponse.newBuilder()
                    .apply { timestamp = 12345 }
                    .build()
              }
              .build()
              .toByteArray()
          }

          override val eventFlow = emptyFlow<ByteArray>()
          override val scope = this@NetworkInspectorTabTest.scope
        }
      ),
    )

  @Test
  fun pressActionButtons(): Unit = runBlocking {
    val dataSource = FakeNetworkInspectorDataSource(listOf(), listOf())
    val tab =
      NetworkInspectorTab(
        projectRule.project,
        FakeUiComponentsProvider(),
        dataSource,
        services,
        scope,
        projectRule.fixture.testRootDisposable,
      )

    tab.launchJob.join()

    val clearData = findComponentWithUniqueName(tab.actionsToolBar, "Clear data") as CommonButton
    val zoomOut = findComponentWithUniqueName(tab.actionsToolBar, "Zoom out") as CommonButton
    val zoomIn = findComponentWithUniqueName(tab.actionsToolBar, "Zoom in") as CommonButton
    val resetZoom = findComponentWithUniqueName(tab.actionsToolBar, "Reset zoom") as CommonButton
    val zoomToSelection =
      findComponentWithUniqueName(tab.actionsToolBar, "Zoom to selection") as CommonButton

    tab.model.timeline.dataRange.set(0.0, 10.0)
    tab.model.timeline.selectionRange.set(0.0, 4.0)
    zoomToSelection.doClick()
    timer.step()
    assertThat(tab.model.timeline.viewRange.isSameAs(Range(0.0, 4.0)))

    val defaultViewRange = Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    zoomIn.doClick()
    timer.step()
    val zoomedInViewRange =
      Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    assertThat(zoomedInViewRange.length).isLessThan(defaultViewRange.length)

    zoomOut.doClick()
    timer.step()
    val zoomedOutViewRange =
      Range(tab.model.timeline.viewRange.min, tab.model.timeline.viewRange.max)
    assertThat(zoomedOutViewRange.length).isGreaterThan(zoomedInViewRange.length)

    resetZoom.doClick()
    timer.step()
    assertThat(tab.model.timeline.viewRange.length).isGreaterThan(defaultViewRange.length)

    clearData.doClick()
    assertThat(tab.model.timeline.dataRange.isEmpty).isTrue()
    assertThat(tab.model.timeline.viewRange.isEmpty).isTrue()
    assertThat(tab.model.timeline.selectionRange.isEmpty).isTrue()
    assertThat(dataSource.resetCalledCount).isEqualTo(1)
  }

  @Test
  fun zoomToSelection_enableState() = runBlocking {
    val tab =
      NetworkInspectorTab(
        projectRule.project,
        FakeUiComponentsProvider(),
        FakeNetworkInspectorDataSource(),
        services,
        scope,
        projectRule.fixture.testRootDisposable,
      )

    tab.launchJob.join()

    val zoomToSelection =
      findComponentWithUniqueName(tab.actionsToolBar, "Zoom to selection") as CommonButton

    assertThat(zoomToSelection.isEnabled).isFalse()

    tab.model.timeline.dataRange.set(0.0, 10.0)
    assertThat(zoomToSelection.isEnabled).isFalse()

    tab.model.timeline.selectionRange.set(0.0, 4.0)
    assertThat(zoomToSelection.isEnabled).isTrue()
  }
}
