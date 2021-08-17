/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.app.inspection.AppInspection.CreateInspectorResponse
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionInspectorMetricsTest {
  private val inspectionRule = AppInspectionInspectorRule()
  private val inspectorRule = LayoutInspectorRule(inspectionRule.createInspectorClientProvider()) { listOf(MODERN_PROCESS.name) }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule)

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun attachMetricsLoggedAfterProcessSuccessfullyAttached() {
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(2)

    usages[0].studioEvent.let { studioEvent ->
      val deviceInfo = studioEvent.deviceInfo
      assertThat(deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(MODERN_DEVICE.serial))
      assertThat(deviceInfo.model).isEqualTo(MODERN_DEVICE.model)
      assertThat(deviceInfo.manufacturer).isEqualTo(MODERN_DEVICE.manufacturer)
      assertThat(deviceInfo.deviceType).isEqualTo(DeviceInfo.DeviceType.LOCAL_PHYSICAL)

      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_REQUEST)

      assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))
    }

    usages[1].studioEvent.let { studioEvent ->
      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_SUCCESS)
      assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))
    }

    assertThat(usages[0].studioEvent.deviceInfo).isEqualTo(usages[1].studioEvent.deviceInfo)
    assertThat(usages[0].studioEvent.projectId).isEqualTo(usages[1].studioEvent.projectId)
  }

  @Test
  fun attachMetricsLoggedAfterProcessFailedToAttach() {
    inspectionRule.viewInspector.createResponseStatus = CreateInspectorResponse.Status.GENERIC_SERVICE_ERROR
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertThat(usages).hasSize(1)

    usages[0].studioEvent.let { studioEvent ->
      val deviceInfo = studioEvent.deviceInfo
      assertThat(deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(MODERN_DEVICE.serial))
      assertThat(deviceInfo.model).isEqualTo(MODERN_DEVICE.model)
      assertThat(deviceInfo.manufacturer).isEqualTo(MODERN_DEVICE.manufacturer)
      assertThat(deviceInfo.deviceType).isEqualTo(DeviceInfo.DeviceType.LOCAL_PHYSICAL)

      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_REQUEST)

      assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))
    }
  }
}