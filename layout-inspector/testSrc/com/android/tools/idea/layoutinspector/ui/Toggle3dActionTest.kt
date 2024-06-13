/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.BITMAP_AS_REQUESTED
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.Toggle3dAction
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class Toggle3dActionTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val cleaner = MockitoCleanerRule()

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  private val scheduler = VirtualTimeScheduler()

  private val inspectorModel = model(disposableRule.disposable) { view(1) { view(2) { image() } } }
  private lateinit var layoutInspector: LayoutInspector
  private lateinit var renderModel: RenderModel

  private val capabilities = mutableSetOf(InspectorClient.Capability.SUPPORTS_SKP)
  private val mockDevice: DeviceDescriptor = mock()

  @Before
  fun setUp() {
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(
      PropertiesComponent::class.java,
      PropertiesComponentMock(),
      disposableRule.disposable,
    )
    val notificationModel = NotificationModel(projectRule.project)
    val treeSettings = FakeTreeSettings()

    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(capabilities)
    whenever(client.isConnected).thenReturn(true)
    whenever(client.inLiveMode).thenReturn(true)

    val process =
      object : ProcessDescriptor {
        override val device = mockDevice
        override val abiCpuArch = "abi"
        override val name = "name"
        override val packageName = "packageName"
        override val isRunning = true
        override val pid = 0
        override val streamId = 0L
      }

    whenever(mockDevice.apiLevel).thenReturn(29)
    whenever(client.process).thenReturn(process)

    val launcher: InspectorClientLauncher = mock()
    whenever(launcher.activeClient).thenReturn(client)

    renderModel = RenderModel(inspectorModel, mock(), treeSettings) { DisconnectedClient }
    layoutInspector =
      LayoutInspector(
        coroutineScope = AndroidCoroutineScope(disposableRule.disposable),
        processModel = mock(),
        deviceModel = mock(),
        foregroundProcessDetection = null,
        inspectorClientSettings = InspectorClientSettings(projectRule.project),
        launcher = launcher,
        layoutInspectorModel = inspectorModel,
        notificationModel = notificationModel,
        treeSettings = treeSettings,
        executor = MoreExecutors.directExecutor(),
        renderModel = renderModel,
      )
  }

  @Test
  fun testUnrotated() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isTrue()
    assertThat(fakeEvent.presentation.text).isEqualTo("3D Mode")
    assertThat(fakeEvent.presentation.description)
      .isEqualTo(
        "Visually inspect the hierarchy by clicking and dragging to rotate the layout. Enabling this " +
          "mode consumes more device resources and might impact runtime performance."
      )
    assertThat(fakeEvent.presentation.icon).isEqualTo(StudioIcons.LayoutInspector.Toolbar.MODE_3D)
  }

  @Test
  fun testRotated() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    renderModel.xOff = 1.0
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isTrue()
    assertThat(fakeEvent.presentation.text).isEqualTo("2D Mode")
    assertThat(fakeEvent.presentation.description)
      .isEqualTo(
        "Inspect the layout in 2D mode. " +
          "Enabling this mode has less impact on your device's runtime performance."
      )
    assertThat(fakeEvent.presentation.icon)
      .isEqualTo(StudioIcons.LayoutInspector.Toolbar.RESET_VIEW)
  }

  @Test
  fun testOverlay() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    renderModel.overlay = mock()
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isFalse()
    assertThat(fakeEvent.presentation.text)
      .isEqualTo("Rotation not available when overlay is active")
  }

  @Test
  fun testNoCapability() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    capabilities.clear()
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isFalse()
    assertThat(fakeEvent.presentation.text)
      .isEqualTo("Error while rendering device image, rotation not available")
  }

  @Test
  fun testOldDevice() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    whenever(mockDevice.apiLevel).thenReturn(28)
    capabilities.clear()
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isFalse()
    assertThat(fakeEvent.presentation.text)
      .isEqualTo("Rotation not available for devices below API 29")
  }

  @Test
  fun testNoRendererFallback() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    val window =
      window(3, 1, imageType = BITMAP_AS_REQUESTED) {
        image()
        view(2)
      }
    capabilities.clear()
    inspectorModel.update(window, listOf(3), 0)
    toggle3dAction.update(fakeEvent)
    assertThat(fakeEvent.presentation.isEnabled).isFalse()
    assertThat(fakeEvent.presentation.text)
      .isEqualTo("Error while rendering device image, rotation not available")
  }

  @Test
  fun testRotationAnimation() {
    val toggle3dAction = Toggle3dAction { renderModel }
    val fakeEvent = createFakeEvent(toggle3dAction)
    toggle3dAction.executorFactory = { scheduler }
    toggle3dAction.getCurrentTimeMillis = { scheduler.currentTimeNanos / 1000000 }

    toggle3dAction.actionPerformed(fakeEvent)
    assertThat(renderModel.xOff).isEqualTo(0.0)
    assertThat(renderModel.yOff).isEqualTo(0.0)
    scheduler.advanceBy(30, TimeUnit.MILLISECONDS)
    // imageType is bitmap, so we shouldn't rotate yet
    assertThat(renderModel.xOff).isEqualTo(0.0)
    assertThat(renderModel.yOff).isEqualTo(0.0)

    // Update to be SKP, now we can rotate
    inspectorModel.windows.values.first().skpLoadingComplete()
    scheduler.advanceBy(15, TimeUnit.MILLISECONDS)
    assertThat(renderModel.xOff).isEqualTo(0.0225)
    assertThat(renderModel.yOff).isEqualTo(0.003)
    scheduler.advanceBy(15, TimeUnit.MILLISECONDS)
    assertThat(renderModel.xOff).isEqualTo(0.045)
    assertThat(renderModel.yOff).isEqualTo(0.006)
    // Advance a lot, we should be at the final state
    scheduler.advanceBy(500, TimeUnit.MILLISECONDS)
    assertThat(renderModel.xOff).isEqualTo(0.45)
    assertThat(renderModel.yOff).isEqualTo(0.06)
  }

  private fun createFakeEvent(anAction: AnAction): AnActionEvent {
    return AnActionEvent.createFromAnAction(anAction, null, "") {
      when (it) {
        LAYOUT_INSPECTOR_DATA_KEY.name -> layoutInspector
        else -> null
      }
    }
  }
}
