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

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.layoutinspector.FakeForegroundProcessDetection
import com.android.tools.idea.layoutinspector.FakeSessionStats
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.android.tools.idea.streaming.emulator.FakeEmulator
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Rectangle
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class RenderingComponentsTest {
  @get:Rule val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector

  @Before
  fun setUp() {
    val processModel = ProcessesModel(TestProcessDiscovery())
    val deviceModel = DeviceModel(displayViewRule.disposable, processModel)
    val notificationModel = NotificationModel(displayViewRule.project)

    val coroutineScope = displayViewRule.disposable.createCoroutineScope()
    val launcher =
      InspectorClientLauncher(
        processes = processModel,
        clientFactories = emptyList(),
        project = displayViewRule.project,
        notificationModel = notificationModel,
        scope = coroutineScope,
        parentDisposable = displayViewRule.disposable,
        metrics = mock(),
      )

    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()
    val inspectorModel =
      model(displayViewRule.disposable) { view(ROOT, Rectangle(0, 0, 100, 100)) {} }

    layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = processModel,
        deviceModel = deviceModel,
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(displayViewRule.project),
        launcher = launcher,
        layoutInspectorModel = inspectorModel,
        notificationModel = notificationModel,
        treeSettings = FakeTreeSettings(),
      )
  }

  @Test
  fun testAddAndRemoveRenderer() {
    val displayView = displayViewRule.newEmulatorView()
    val renderingComponents = createRenderingComponents(displayView)
    assertThat(renderingComponents.renderer.parent).isNull()

    renderingComponents.addRenderer()
    assertThat(renderingComponents.renderer.parent).isInstanceOf(AbstractDisplayView::class.java)

    renderingComponents.removeRenderer()
    assertThat(renderingComponents.renderer.parent).isNull()
  }

  @Test
  fun testDisposeRemovesRenderer() {
    val displayView = displayViewRule.newEmulatorView()
    val renderingComponents = createRenderingComponents(displayView)
    assertThat(renderingComponents.renderer.parent).isNull()

    renderingComponents.addRenderer()
    assertThat(renderingComponents.renderer.parent).isInstanceOf(AbstractDisplayView::class.java)

    Disposer.dispose(renderingComponents)
    assertThat(renderingComponents.renderer.parent).isNull()
  }

  @Test
  fun testDisposedWhenEitherParentDisposableOrDisplayViewIsDisposed() {
    val disposable1 = Disposer.newDisposable(displayViewRule.disposable)
    val displayView1 = displayViewRule.newEmulatorView()
    var isRenderingComponentsDisposed1 = false

    val renderingComponents1 =
      createRenderingComponents(disposable = disposable1, displayView = displayView1)
    Disposer.register(renderingComponents1) { isRenderingComponentsDisposed1 = true }

    // Rendering components should be disposed when the parent disposable (the tab) is disposed.
    Disposer.dispose(disposable1)
    assertThat(isRenderingComponentsDisposed1).isTrue()

    val disposable2 = Disposer.newDisposable(displayViewRule.disposable)
    val displayView2 = displayViewRule.newEmulatorView()
    var isRenderingComponentsDisposed2 = false

    val renderingComponents2 =
      createRenderingComponents(disposable = disposable2, displayView = displayView2)
    Disposer.register(renderingComponents2) { isRenderingComponentsDisposed2 = true }

    // Rendering components should be disposed when the display view is disposed.
    Disposer.dispose(displayView2)
    assertThat(isRenderingComponentsDisposed2).isTrue()
  }

  @Test
  fun testOnDeviceRenderingIsLoggedToMetrics() {
    val xrDisplay =
      displayViewRule.newEmulatorView(
        avdCreator = { path -> FakeEmulator.createXrHeadsetAvd(path) }
      )
    val fakeSessionStats = FakeSessionStats()
    createRenderingComponents(
      disposable = displayViewRule.disposable,
      displayList = listOf(xrDisplay),
      layoutInspector = layoutInspector,
      statsProvider = { fakeSessionStats },
    )

    assertThat(fakeSessionStats.setOnDeviceRenderingInvocations).isEqualTo(1)
  }

  @Test
  fun testOnDeviceRenderingSharesBetweenDifferentRenderers() {
    val xrDisplay1 =
      displayViewRule.newEmulatorView(
        avdCreator = { path -> FakeEmulator.createXrHeadsetAvd(path) }
      )
    val xrDisplay2 =
      displayViewRule.newEmulatorView(
        avdCreator = { path -> FakeEmulator.createXrHeadsetAvd(path) }
      )

    val fakeSessionStats = FakeSessionStats()
    val renderingComponents =
      createRenderingComponents(
        disposable = displayViewRule.disposable,
        displayList = listOf(xrDisplay1, xrDisplay2),
        layoutInspector = layoutInspector,
        statsProvider = { fakeSessionStats },
      )

    assertThat(renderingComponents).hasSize(2)
    assertThat(renderingComponents[0].model).isEqualTo(renderingComponents[1].model)
    assertThat(renderingComponents[0].renderer).isNotEqualTo(renderingComponents[1].renderer)
  }

  private fun createRenderingComponents(
    displayView: AbstractDisplayView,
    disposable: Disposable = displayViewRule.disposable,
  ): RenderingComponents {
    return createRenderingComponents(
        disposable = disposable,
        displayList = listOf(displayView),
        layoutInspector = layoutInspector,
      )
      .first()
  }
}
