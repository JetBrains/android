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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.OnDeviceRenderingClient
import com.android.tools.idea.layoutinspector.runningdevices.allChildren
import com.android.tools.idea.layoutinspector.ui.FakeRenderSettings
import com.android.tools.idea.layoutinspector.ui.RenderLogic
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.viewWindow
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import java.awt.Rectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class RootRendererPanelTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val edtRule = EdtRule()

  private lateinit var inspectorModel: InspectorModel
  private lateinit var renderModel: RenderModel
  private lateinit var onDeviceRenderModel: OnDeviceRendererModel
  private lateinit var onDeviceRendererPanel: OnDeviceRendererPanel
  private lateinit var studioRendererPanel: StudioRendererPanel

  @Before
  fun setUp() {
    inspectorModel =
      model(disposableRule.disposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(VIEW1, 10, 15, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
        }
      }

    onDeviceRenderModel =
      OnDeviceRendererModel(
        disposableRule.disposable,
        inspectorModel,
        FakeTreeSettings(),
        FakeRenderSettings(),
      )

    renderModel =
      RenderModel(
        model = inspectorModel,
        notificationModel = mock(),
        treeSettings = FakeTreeSettings(showRecompositions = false),
        currentClientProvider = { DisconnectedClient },
      )
    val renderLogic = RenderLogic(renderModel, FakeRenderSettings())

    val scope = CoroutineScope(Job())

    onDeviceRendererPanel =
      createOnDeviceRenderer(disposableRule.disposable, scope, onDeviceRenderModel)
    studioRendererPanel =
      createStudioRenderer(disposableRule.disposable, scope, renderLogic, renderModel)
  }

  @Test
  fun testInterceptClicks() {
    val rootPanelRenderer =
      RootPanelRenderer(
        disposable = disposableRule.disposable,
        renderModel = renderModel,
        onDeviceRendererProvider = { onDeviceRendererPanel },
        studioRendererProvider = { studioRendererPanel },
      )

    rootPanelRenderer.interceptClicks = true

    assertThat(studioRendererPanel.interceptClicks).isTrue()

    rootPanelRenderer.interceptClicks = false

    assertThat(studioRendererPanel.interceptClicks).isFalse()

    inspectorModel.clear()
    val xrWindow =
      viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 25, 30, 50, 50) { image() } }
    inspectorModel.update(xrWindow, listOf(ROOT), 0)

    rootPanelRenderer.interceptClicks = true

    assertThat(onDeviceRendererPanel.interceptClicks).isTrue()

    rootPanelRenderer.interceptClicks = false

    assertThat(onDeviceRendererPanel.interceptClicks).isFalse()
  }

  @Test
  fun testDisablingInterceptClicksClearsSelection() {
    val rootPanelRenderer =
      RootPanelRenderer(
        disposable = disposableRule.disposable,
        renderModel = renderModel,
        onDeviceRendererProvider = { onDeviceRendererPanel },
        studioRendererProvider = { studioRendererPanel },
      )

    rootPanelRenderer.interceptClicks = true

    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)

    rootPanelRenderer.interceptClicks = false

    assertThat(inspectorModel.selection).isNull()
  }

  @Test
  @RunsInEdt
  fun testMouseClickIsDispatchedToChildRenderer() {
    val rootPanelRenderer =
      RootPanelRenderer(
        disposable = disposableRule.disposable,
        renderModel = renderModel,
        onDeviceRendererProvider = { onDeviceRendererPanel },
        studioRendererProvider = { studioRendererPanel },
      )

    rootPanelRenderer.size = Dimension(200, 250)
    rootPanelRenderer.interceptClicks = true

    assertThat(renderModel.model.selection).isNull()

    val fakeUi = FakeUi(rootPanelRenderer)

    fakeUi.render()

    // click mouse above ROOT.
    fakeUi.mouse.click(10, 15)

    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()

    assertThat(renderModel.model.selection).isEqualTo(renderModel.model[ROOT])
  }

  @Test
  fun testSwitchToXrChangesRenderer() {
    val rootPanelRenderer =
      RootPanelRenderer(
        disposable = disposableRule.disposable,
        renderModel = renderModel,
        onDeviceRendererProvider = { onDeviceRendererPanel },
        studioRendererProvider = { studioRendererPanel },
      )

    val studioRenderer1 =
      rootPanelRenderer.allChildren().filterIsInstance<StudioRendererPanel>().firstOrNull()
    val deviceRenderer1 =
      rootPanelRenderer.allChildren().filterIsInstance<OnDeviceRendererPanel>().firstOrNull()

    assertThat(studioRenderer1).isNotNull()
    assertThat(deviceRenderer1).isNull()

    inspectorModel.clear()
    val xrWindow =
      viewWindow(ROOT, 0, 0, 100, 200, isXr = true) { view(VIEW1, 25, 30, 50, 50) { image() } }
    inspectorModel.update(xrWindow, listOf(ROOT), 0)

    val studioRenderer2 =
      rootPanelRenderer.allChildren().filterIsInstance<StudioRendererPanel>().firstOrNull()
    val deviceRenderer2 =
      rootPanelRenderer.allChildren().filterIsInstance<OnDeviceRendererPanel>().firstOrNull()

    assertThat(studioRenderer2).isNull()
    assertThat(deviceRenderer2).isNotNull()
  }
}

private fun createOnDeviceRenderer(
  disposable: Disposable,
  scope: CoroutineScope,
  renderModel: OnDeviceRendererModel,
): OnDeviceRendererPanel {
  val messenger =
    object : AppInspectorMessenger {
      override suspend fun sendRawCommand(rawData: ByteArray) = ByteArray(0)

      override val eventFlow: Flow<ByteArray> = emptyFlow()
      override val scope: CoroutineScope = CoroutineScope(Job())
    }
  val onDeviceRenderingClient = OnDeviceRenderingClient(messenger = messenger)

  return OnDeviceRendererPanel(
    disposable = disposable,
    scope = scope,
    client = onDeviceRenderingClient,
    renderModel = renderModel,
    enableSendRightClicksToDevice = {},
  )
}

private fun createStudioRenderer(
  disposable: Disposable,
  scope: CoroutineScope,
  renderLogic: RenderLogic,
  renderModel: RenderModel,
): StudioRendererPanel {
  return StudioRendererPanel(
    disposable = disposable,
    coroutineScope = scope,
    renderLogic = renderLogic,
    renderModel = renderModel,
    notificationModel = mock(),
    displayRectangleProvider = { Rectangle(10, 10, 100, 150) },
    screenScaleProvider = { 1.0 },
    orientationQuadrantProvider = { 0 },
    currentSessionStatistics = { SessionStatisticsImpl(DisconnectedClient.clientType) },
  )
}
