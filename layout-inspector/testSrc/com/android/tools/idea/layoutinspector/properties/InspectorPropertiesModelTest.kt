/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.RenderModel
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class InspectorPropertiesModelTest {
  private val device1 =
    Common.Device.newBuilder()
      .setDeviceId(1)
      .setManufacturer("man1")
      .setModel("mod1")
      .setSerial("serial1")
      .setIsEmulator(false)
      .setApiLevel(1)
      .setVersion("version1")
      .setCodename("codename1")
      .setState(Common.Device.State.ONLINE)
      .build()

  @get:Rule val disposableRule = DisposableRule()

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testListenersAreClearedOnDispose() {
    val layoutInspector = createLayoutInspector()

    val inspectorPropertiesModel = InspectorPropertiesModel(disposableRule.disposable)
    inspectorPropertiesModel.layoutInspector = layoutInspector

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(1)
    assertThat(layoutInspector.inspectorModel.connectionListeners.size()).isEqualTo(1)
    assertThat(layoutInspector.inspectorModel.modificationListeners.size()).isEqualTo(3)

    Disposer.dispose(disposableRule.disposable)

    assertThat(layoutInspector.inspectorModel.selectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.inspectorModel.connectionListeners.size()).isEqualTo(0)
    assertThat(layoutInspector.inspectorModel.modificationListeners.size()).isEqualTo(2)
  }

  private fun createLayoutInspector(): LayoutInspector {
    val scope = AndroidCoroutineScope(disposableRule.disposable)
    val (deviceModel, processModel) = createDeviceModel(device1)
    val mockForegroundProcessDetection = mock<ForegroundProcessDetection>()
    val mockClientSettings = mock<InspectorClientSettings>()
    val mockLauncher = mock<InspectorClientLauncher>()
    val inspectorModel = model { view(ROOT, qualifiedName = "root") }
    val mockRenderModel = mock<RenderModel>()

    val mockTreeSettings = mock<TreeSettings>()
    return LayoutInspector(
      scope,
      processModel,
      deviceModel,
      mockForegroundProcessDetection,
      mockClientSettings,
      mockLauncher,
      inspectorModel,
      NotificationModel(projectRule.project),
      mockTreeSettings,
      renderModel = mockRenderModel
    )
  }

  private fun createDeviceModel(vararg devices: Common.Device): Pair<DeviceModel, ProcessesModel> {
    val testProcessDiscovery = TestProcessDiscovery()
    devices.forEach { testProcessDiscovery.addDevice(it.toDeviceDescriptor()) }
    val processModel = ProcessesModel(testProcessDiscovery)
    return DeviceModel(disposableRule.disposable, processModel) to processModel
  }
}
