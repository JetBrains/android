/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui.rendering

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.runningdevices.allChildren
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class OnDeviceRendererPanelTest {
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val appInspectorRule = AppInspectionInspectorRule(projectRule)
  private val inspectorRule =
    LayoutInspectorRule(
      clientProviders = listOf(appInspectorRule.createInspectorClientProvider()),
      projectRule = projectRule,
      isPreferredProcess = { it.name == MODERN_PROCESS.name },
    )

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(projectRule)
      .around(appInspectorRule)
      .around(inspectorRule)
      .around(EdtRule())

  private lateinit var renderModel: OnDeviceRendererModel

  @Before
  fun setUp() {
    renderModel =
      OnDeviceRendererModel(
        parentDisposable = projectRule.testRootDisposable,
        inspectorModel = inspectorRule.inspectorModel,
        treeSettings = FakeTreeSettings(),
        renderSettings = FakeRenderSettings(),
      )
  }

  @Test
  fun testClientConnectionAddsPanel() = runTest {
    inspectorRule.launchSynchronously = false
    val onDeviceRendererPanel =
      OnDeviceRendererPanel(
        disposable = projectRule.testRootDisposable,
        scope = this,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    connectAppInspectionClient()

    waitForCondition(5.seconds) { onDeviceRendererPanel.allChildren().size == 1 }

    inspectorRule.inspectorModel.updateConnection(DisconnectedClient)

    assertThat(onDeviceRendererPanel.allChildren()).hasSize(0)
  }

  @Test
  fun testListenersAreRemoved() {
    val onDeviceRendererPanel =
      OnDeviceRendererPanel(
        disposable = projectRule.testRootDisposable,
        scope = inspectorRule.inspector.coroutineScope,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    assertThat(inspectorRule.inspectorModel.connectionListeners.size()).isEqualTo(1)

    Disposer.dispose(onDeviceRendererPanel)

    assertThat(inspectorRule.inspectorModel.connectionListeners.size()).isEqualTo(0)
  }

  @Test
  fun testMouseEventsAreDispatched() = runTest {
    val onDeviceRendererPanel =
      OnDeviceRendererPanel(
        disposable = projectRule.testRootDisposable,
        scope = inspectorRule.inspector.coroutineScope,
        renderModel = renderModel,
        enableSendRightClicksToDevice = {},
      )

    var lastMousePosition: Point? = null

    val fakePanel =
      object : BorderLayoutPanel() {
        private inner class MouseListener : MouseAdapter() {
          override fun mouseMoved(e: MouseEvent) {
            lastMousePosition = Point(e.x, e.y)
          }
        }

        init {
          addMouseMotionListener(MouseListener())
        }
      }

    onDeviceRendererPanel.add(fakePanel)

    val parent = BorderLayoutPanel()
    parent.add(onDeviceRendererPanel)
    parent.size = Dimension(100, 100)
    onDeviceRendererPanel.size = Dimension(100, 100)
    val fakeUi = FakeUi(parent)
    fakeUi.render()

    // move the cursor
    fakeUi.mouse.moveTo(42, 42)
    withContext(Dispatchers.EDT) { fakeUi.layoutAndDispatchEvents() }

    assertThat(lastMousePosition).isEqualTo(Point(42, 42))
  }

  private fun connectAppInspectionClient() {
    val latch = CountDownLatch(1)
    inspectorRule.inspectorModel.addConnectionListener {
      if (it.isConnected && it is AppInspectionInspectorClient) {
        latch.countDown()
      }
    }
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.startLaunch(2)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    inspectorRule.awaitLaunch()
    latch.await()
  }
}
