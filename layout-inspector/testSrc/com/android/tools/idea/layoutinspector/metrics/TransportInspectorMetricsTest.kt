/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.transport.TransportInspectorRule
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_SUCCESS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

class TransportInspectorMetricsTest {
  private val transportRule = TransportInspectorRule()
  private val inspectorRule = LayoutInspectorRule(transportRule.createClientProvider())

  @get:Rule
  val ruleChain = RuleChain.outerRule(transportRule).around(inspectorRule)!!

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachSuccessAfterProcessConnected() {
    inspectorRule.processes.selectedProcess = MODERN_DEVICE.createProcess() // Simulates user clicking on a process directly
    transportRule.scheduler.advanceBy(110, TimeUnit.MILLISECONDS)

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertEquals(AnonymizerUtil.anonymizeUtf8(MODERN_DEVICE.serial), deviceInfo.anonymizedSerialNumber)
    assertEquals(MODERN_DEVICE.model, deviceInfo.model)
    assertEquals(MODERN_DEVICE.manufacturer, deviceInfo.manufacturer)
    assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertEquals(ATTACH_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    assertEquals(deviceInfo, studioEvent.deviceInfo)
    assertEquals(ATTACH_SUCCESS, studioEvent.dynamicLayoutInspectorEvent.type)
    assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }

  @Test
  fun testAttachFailAfterProcessConnected() {
    transportRule.rejectAttachRequests = true

    inspectorRule.processes.selectedProcess = MODERN_DEVICE.createProcess() // Simulates user clicking on a process directly
    transportRule.scheduler.advanceBy(110, TimeUnit.MILLISECONDS)

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertEquals(1, usages.size)
    val studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertEquals(AnonymizerUtil.anonymizeUtf8(MODERN_DEVICE.serial), deviceInfo.anonymizedSerialNumber)
    assertEquals(MODERN_DEVICE.model, deviceInfo.model)
    assertEquals(MODERN_DEVICE.manufacturer, deviceInfo.manufacturer)
    assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertEquals(ATTACH_REQUEST, inspectorEvent.type)
    assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }
}
