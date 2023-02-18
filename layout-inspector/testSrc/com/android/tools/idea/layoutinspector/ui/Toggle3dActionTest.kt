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
import com.android.testutils.PropertySetterRule
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.BITMAP_AS_REQUESTED
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import icons.StudioIcons
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

class Toggle3dActionTest {

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val cleaner = MockitoCleanerRule()

  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  private val scheduler = VirtualTimeScheduler()

  @get:Rule
  val setExecutorRule = PropertySetterRule({ scheduler }, Toggle3dAction::executorFactory)

  @get:Rule
  val timeRule = PropertySetterRule({ scheduler.currentTimeNanos / 1000000 }, Toggle3dAction::getCurrentTimeMillis)

  private val inspectorModel = model {
    view(1) {
      view(2) {
        image()
      }
    }
  }
  private lateinit var inspector: LayoutInspector
  private lateinit var viewModel: RenderModel

  private val event: AnActionEvent = mock()
  private val presentation: Presentation = mock()

  private val capabilities = mutableSetOf(InspectorClient.Capability.SUPPORTS_SKP)
  private val device: DeviceDescriptor = mock()

  @Before
  fun setUp() {
    val application = ApplicationManager.getApplication()
    application.registerServiceInstance(PropertiesComponent::class.java, PropertiesComponentMock(), disposableRule.disposable)
    val client: InspectorClient = mock()
    whenever(client.capabilities).thenReturn(capabilities)
    whenever(client.isConnected).thenReturn(true)
    whenever(client.isCapturing).thenReturn(true)
    whenever(device.apiLevel).thenReturn(29)
    val launcher: InspectorClientLauncher = mock()
    whenever(launcher.activeClient).thenReturn(client)
    val coroutineScope = AndroidCoroutineScope(disposableRule.disposable)
    inspector = LayoutInspector(
      coroutineScope,
      mock(),
      mock(),
      null,
      mock(),
      launcher,
      inspectorModel,
      mock(),
      MoreExecutors.directExecutor()
    )
    viewModel = RenderModel(inspectorModel, inspector.treeSettings)
    val process: ProcessDescriptor = mock()
    whenever(process.device).thenReturn(device)
    whenever(client.process).thenReturn(process)
    whenever(event.getData(DEVICE_VIEW_MODEL_KEY)).thenReturn(viewModel)
    whenever(event.getData(LAYOUT_INSPECTOR_DATA_KEY)).thenReturn(inspector)
    whenever(event.presentation).thenReturn(presentation)
  }

  @Test
  fun testUnrotated() {
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = true
    verify(presentation).text = "3D Mode"
    verify(presentation).description = "Visually inspect the hierarchy by clicking and dragging to rotate the layout. Enabling this " +
                                       "mode consumes more device resources and might impact runtime performance."
    verify(presentation).icon = StudioIcons.LayoutInspector.MODE_3D
  }

  @Test
  fun testRotated() {
    viewModel.xOff = 1.0
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = true
    verify(presentation).text = "2D Mode"
    verify(presentation).description =
      "Inspect the layout in 2D mode. Enabling this mode has less impact on your device's runtime performance."
    verify(presentation).icon = StudioIcons.LayoutInspector.RESET_VIEW
  }

  @Test
  fun testOverlay() {
    viewModel.overlay = mock()
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Rotation not available when overlay is active"
  }

  @Test
  fun testNoCapability() {
    capabilities.clear()
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Error while rendering device image, rotation not available"
  }

  @Test
  fun testOldDevice() {
    whenever(device.apiLevel).thenReturn(28)
    capabilities.clear()
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Rotation not available for devices below API 29"
  }

  @Test
  fun testNoRendererFallback() {
    val window = window(3, 1, imageType = BITMAP_AS_REQUESTED) {
      image()
      view(2)
    }
    capabilities.clear()
    inspectorModel.update(window, listOf(3), 0)
    Toggle3dAction.update(event)
    verify(presentation).isEnabled = false
    verify(presentation).text = "Error while rendering device image, rotation not available"
  }

  @Test
  fun testRotationAnimation() {
    Toggle3dAction.actionPerformed(event)
    assertThat(viewModel.xOff).isEqualTo(0.0)
    assertThat(viewModel.yOff).isEqualTo(0.0)
    scheduler.advanceBy(30, TimeUnit.MILLISECONDS)
    // imageType is bitmap, so we shouldn't rotate yet
    assertThat(viewModel.xOff).isEqualTo(0.0)
    assertThat(viewModel.yOff).isEqualTo(0.0)

    // Update to be SKP, now we can rotate
    inspectorModel.windows.values.first().skpLoadingComplete()
    scheduler.advanceBy(15, TimeUnit.MILLISECONDS)
    assertThat(viewModel.xOff).isEqualTo(0.0225)
    assertThat(viewModel.yOff).isEqualTo(0.003)
    scheduler.advanceBy(15, TimeUnit.MILLISECONDS)
    assertThat(viewModel.xOff).isEqualTo(0.045)
    assertThat(viewModel.yOff).isEqualTo(0.006)
    // Advance a lot, we should be at the final state
    scheduler.advanceBy(500, TimeUnit.MILLISECONDS)
    assertThat(viewModel.xOff).isEqualTo(0.45)
    assertThat(viewModel.yOff).isEqualTo(0.06)
  }
}