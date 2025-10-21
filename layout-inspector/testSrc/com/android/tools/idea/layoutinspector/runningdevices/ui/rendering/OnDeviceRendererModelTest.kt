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
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.LABEL_FONT_SIZE
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.RenderingDimensions.EMPHASIZED_BORDER_THICKNESS
import com.android.tools.idea.layoutinspector.model.RenderingDimensions.NORMAL_BORDER_THICKNESS
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.ui.BASE_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.HOVER_COLOR_ARGB
import com.android.tools.idea.layoutinspector.ui.SELECTION_COLOR_ARGB
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS =
  MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class OnDeviceRendererModelTest {
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

  private lateinit var inspectorModel: InspectorModel
  /** Render model tied to inspector rule */
  private lateinit var renderModelRule: EmbeddedRendererModel
  /** Render model tied to local [inspectorModel] */
  private lateinit var renderModel: EmbeddedRendererModel

  @Before
  fun setUp() {
    inspectorModel =
      model(projectRule.testRootDisposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(VIEW1, 10, 15, 25, 25) {}
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

    renderModel =
      EmbeddedRendererModel(
        parentDisposable = projectRule.testRootDisposable,
        inspectorModel = inspectorModel,
        treeSettings = FakeTreeSettings(),
        renderSettings = FakeRenderSettings(),
        navigateToSelectedViewOnDoubleClick = {},
      )

    renderModelRule =
      EmbeddedRendererModel(
        parentDisposable = projectRule.testRootDisposable,
        inspectorModel = inspectorRule.inspectorModel,
        treeSettings = FakeTreeSettings(),
        renderSettings = FakeRenderSettings(),
        navigateToSelectedViewOnDoubleClick = {},
      )
  }

  @Ignore("b/454014907")
  @Test
  fun testClientConnectionStartsOnDeviceRendering() {
    inspectorRule.launchSynchronously = false

    val commands = mutableListOf<ByteArray>()
    appInspectorRule.viewInspector.listenWhen(
      condition = { true },
      listener = { command -> commands.add(command.toByteArray()) },
    )

    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = projectRule.testRootDisposable,
        scope = inspectorRule.inspector.coroutineScope,
        renderModel = renderModelRule,
      )

    connectAppInspectionClient()

    waitForCondition(2.seconds) { commands.size == 3 }

    val enableCommand = commands.last()
    assertThat(enableCommand).isEqualTo(enableOnDeviceRenderingCommand)

    inspectorRule.inspectorModel.updateConnection(DisconnectedClient)

    assertThat(onDeviceRendererModel.onNewClientJob?.isActive).isFalse()
  }

  @Test
  fun testListenersAreRemovedOnDispose() {
    val onDeviceRendererModel =
      OnDeviceRendererModel(
        disposable = projectRule.testRootDisposable,
        scope = inspectorRule.inspector.coroutineScope,
        renderModel = renderModelRule,
      )

    assertThat(inspectorRule.inspectorModel.connectionListeners.size()).isEqualTo(1)

    Disposer.dispose(onDeviceRendererModel)

    assertThat(inspectorRule.inspectorModel.connectionListeners.size()).isEqualTo(0)
  }

  @Test
  fun testRenderModelEventsAreSent() = runTest {
    val (receivedMessages, messenger) = buildMessenger()
    val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

    val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

    val model =
      OnDeviceRendererModel(
        disposable = projectRule.testRootDisposable,
        scope = scope,
        renderModel = renderModel,
        client = onDeviceRenderingClient,
      )

    // enable on-device rendering
    testScheduler.advanceUntilIdle()
    assertThat(receivedMessages[0]).isEqualTo(enableOnDeviceRenderingCommand)

    // intercept clicks
    renderModel.setInterceptClicks(true)
    testScheduler.advanceUntilIdle()
    assertThat(receivedMessages.last()).isEqualTo(enableInterceptTouchEventsCommand)

    // select
    renderModel.selectNode(15.0, 15.0)
    testScheduler.advanceUntilIdle()

    val drawCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          color = SELECTION_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.SELECTED_NODES,
          label =
            inspectorModel[VIEW1]?.unqualifiedName?.let {
              DrawInstruction.Label(text = it, size = LABEL_FONT_SIZE)
            },
          strokeThickness = EMPHASIZED_BORDER_THICKNESS,
        )
        .toByteArray()

    assertThat(receivedMessages.last()).isEqualTo(drawCommand)

    // hover
    renderModel.hoverNode(15.0, 15.0)
    testScheduler.advanceUntilIdle()

    val hoverCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds = listOf(inspectorModel[VIEW1]!!.layoutBounds),
          color = HOVER_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.HOVERED_NODES,
          label = null,
          strokeThickness = EMPHASIZED_BORDER_THICKNESS,
        )
        .toByteArray()

    assertThat(receivedMessages.last()).isEqualTo(hoverCommand)

    // visible nodes
    val visibleNodesCommand =
      buildDrawNodeCommand(
          rootId = ROOT,
          bounds =
            listOf(
              inspectorModel[COMPOSE1]!!.layoutBounds,
              inspectorModel[VIEW1]!!.layoutBounds,
              inspectorModel[ROOT]!!.layoutBounds,
            ),
          color = BASE_COLOR_ARGB,
          type = LayoutInspectorViewProtocol.DrawCommand.Type.VISIBLE_NODES,
          label = null,
          strokeThickness = NORMAL_BORDER_THICKNESS,
        )
        .toByteArray()

    assertThat(receivedMessages.find { it.contentEquals(visibleNodesCommand) }).isNotNull()
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

private fun buildMessenger(): Pair<MutableList<ByteArray>, AppInspectorMessenger> {
  val receivedMessages = mutableListOf<ByteArray>()
  val messenger =
    object : AppInspectorMessenger {
      override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
        receivedMessages.add(rawData)
        return ByteArray(0)
      }

      override val eventFlow: Flow<ByteArray> = emptyFlow()
      override val scope: CoroutineScope = CoroutineScope(Job())
    }

  return receivedMessages to messenger
}
