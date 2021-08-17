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
package com.android.tools.idea.layoutinspector.metrics

import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyTreeLoader
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class LegacyInspectorMetricsTest {

  private val windowIds = mutableListOf<String>()
  private val legacyClientProvider = object : InspectorClientProvider {
    override fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient {
      val loader = Mockito.mock(LegacyTreeLoader::class.java)
      Mockito.`when`(loader.getAllWindowIds(ArgumentMatchers.any())).thenReturn(windowIds)
      return LegacyClientProvider(loader).create(params, inspector) as LegacyClient
    }
  }

  @get:Rule
  val inspectorRule = LayoutInspectorRule(legacyClientProvider)

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachSuccessAfterProcessConnected() {
    windowIds.addAll(listOf("window1", "window2", "window3"))
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    Assert.assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(LEGACY_DEVICE.serial), deviceInfo.anonymizedSerialNumber)
    Assert.assertEquals(LEGACY_DEVICE.model, deviceInfo.model)
    Assert.assertEquals(LEGACY_DEVICE.manufacturer, deviceInfo.manufacturer)
    Assert.assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    Assert.assertEquals(deviceInfo, studioEvent.deviceInfo)
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS, studioEvent.dynamicLayoutInspectorEvent.type)
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }

  @Test
  fun testAttachFailAfterProcessConnected() {
    Assert.assertTrue(windowIds.isEmpty()) // No window IDs will cause attaching to fail
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    Assert.assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(LEGACY_DEVICE.serial), deviceInfo.anonymizedSerialNumber)
    Assert.assertEquals(LEGACY_DEVICE.model, deviceInfo.model)
    Assert.assertEquals(LEGACY_DEVICE.manufacturer, deviceInfo.manufacturer)
    Assert.assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    Assert.assertEquals(deviceInfo, studioEvent.deviceInfo)
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE, studioEvent.dynamicLayoutInspectorEvent.type)
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }
}
