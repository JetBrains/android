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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.PropertySetterRule
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.BITMAP_AS_REQUESTED
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.SKP
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.window
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import icons.StudioIcons
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

class Toggle3dActionTest {

  @get:Rule
  val appRule = ApplicationRule()

  private val scheduler = VirtualTimeScheduler()

  @get:Rule
  val setExecutorRule = PropertySetterRule({ scheduler }, Toggle3dAction::executorFactory)

  private val inspectorModel = model {
    view(1) {
      view(2) {
        image()
      }
    }
  }
  private lateinit var inspector: LayoutInspector
  private lateinit var viewModel: DeviceViewPanelModel

  private val event: AnActionEvent = mock()
  private val presentation: Presentation = mock()

  private val capabilities = mutableSetOf(InspectorClient.Capability.SUPPORTS_SKP)
  private val device: DeviceDescriptor = mock()

  @Before
  fun setUp() {
    appRule.testApplication.registerService(PropertiesComponent::class.java, PropertiesComponentMock())
    val client: InspectorClient = mock()
    `when`(client.capabilities).thenReturn(capabilities)
    `when`(client.isConnected).thenReturn(true)
    `when`(client.isCapturing).thenReturn(true)
    `when`(device.apiLevel).thenReturn(29)
    val launcher: InspectorClientLauncher = mock()
    `when`(launcher.activeClient).thenReturn(client)
    inspector = LayoutInspector(launcher, inspectorModel, SessionStatistics(inspectorModel, mock()), mock(), MoreExecutors.directExecutor())
    viewModel = DeviceViewPanelModel(inspectorModel, inspector.stats, inspector.treeSettings)
    val process: ProcessDescriptor = mock()
    `when`(process.device).thenReturn(device)
    `when`(client.process).thenReturn(process)
    `when`(event.getData(DEVICE_VIEW_MODEL_KEY)).thenReturn(viewModel)
    `when`(event.getData(LAYOUT_INSPECTOR_DATA_KEY)).thenReturn(inspector)
    `when`(event.presentation).thenReturn(presentation)
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
    `when`(device.apiLevel).thenReturn(28)
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