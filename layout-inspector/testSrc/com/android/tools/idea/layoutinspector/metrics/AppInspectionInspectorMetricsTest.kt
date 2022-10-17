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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.analytics.LoggedUsage
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorRule
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewBounds
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewRect
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.stats.AnonymizerUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.SkiaViewNode
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.anyDouble
import java.util.concurrent.TimeUnit

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

class AppInspectionInspectorMetricsTest {
  val disposableRule = DisposableRule()

  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable)
  private val inspectorRule = LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider()), projectRule) {
    it.name == MODERN_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule).around(disposableRule)!!

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Before
  fun before() {
    inspectorRule.attachDevice(MODERN_DEVICE)
  }

  @Test
  fun attachMetricsLoggedAfterProcessSuccessfullyAttached() {
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    var usages = usageTrackerRule.testTracker.usages
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

    inspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    usages = waitForEvents(3)
    usages[2].studioEvent.let { studioEvent ->
      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.SESSION_DATA)
      assertThat(inspectorEvent.session.attach.clientType).isEqualTo(APP_INSPECTION_CLIENT)
      assertThat(inspectorEvent.session.attach.success).isTrue()
      assertThat(inspectorEvent.session.attach.errorInfo.attachErrorState).isEqualTo(AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE)
    }

    assertThat(usages[0].studioEvent.deviceInfo).isEqualTo(usages[1].studioEvent.deviceInfo)
    assertThat(usages[0].studioEvent.projectId).isEqualTo(usages[1].studioEvent.projectId)
  }

  @Test
  fun attachMetricsLoggedAfterProcessFailedToAttach() {
    inspectionRule.viewInspector.interceptWhen({it.hasStartFetchCommand()}) {
      LayoutInspectorViewProtocol.Response.newBuilder().apply {
        startFetchResponseBuilder.apply {
          error = "failed to start"
        }
      }.build()
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    val usages = waitForEvents(4)
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
      val deviceInfo = studioEvent.deviceInfo
      assertThat(deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(MODERN_DEVICE.serial))
      assertThat(deviceInfo.model).isEqualTo(MODERN_DEVICE.model)
      assertThat(deviceInfo.manufacturer).isEqualTo(MODERN_DEVICE.manufacturer)
      assertThat(deviceInfo.deviceType).isEqualTo(DeviceInfo.DeviceType.LOCAL_PHYSICAL)

      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_SUCCESS)
    }
    usages[2].studioEvent.let { studioEvent ->
      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_ERROR)
      assertThat(inspectorEvent.errorInfo.attachErrorState).isEqualTo(AttachErrorState.START_REQUEST_SENT)
    }
    usages[3].studioEvent.let { studioEvent ->
      val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
      assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.SESSION_DATA)
      assertThat(inspectorEvent.session.attach.clientType).isEqualTo(APP_INSPECTION_CLIENT)
      assertThat(inspectorEvent.session.attach.success).isFalse()
      assertThat(inspectorEvent.session.attach.errorInfo.attachErrorState).isEqualTo(AttachErrorState.START_REQUEST_SENT)
    }
  }

  @Test
  fun testInitialRenderLogging() {
    inspectorRule.launchSynchronously = false
    inspectionRule.viewInspector.listenWhen({ true }) {
      inspectorRule.inspectorModel.update(window("w1", 1L), listOf("w1"), 1)
    }

    val getUsages = { usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.dynamicLayoutInspectorEvent.type == DynamicLayoutInspectorEventType.INITIAL_RENDER } }

    inspectorRule.startLaunch(2)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.awaitLaunch()
    waitForCondition(10, TimeUnit.SECONDS) { inspectorRule.inspectorClient.isConnected }
    var rootId = 1L
    val skiaParser = mock<SkiaParser>().also {
      whenever(it.getViewTree(any(), any(), anyDouble(), any())).thenAnswer { SkiaViewNode(rootId, listOf()) }
    }
    (inspectorRule.inspectorClient.treeLoader as AppInspectionTreeLoader).skiaParser = skiaParser

    // Load the tree with root id 1. The subsequent refreshImages() should generate an initial load event.
    val (window, _, _) = inspectorRule.inspectorClient.treeLoader.loadComponentTree(createFakeData(rootId),
                                                                                    ResourceLookup(inspectorRule.project),
                                                                                    inspectorRule.inspectorClient.process)!!
    window!!.refreshImages(1.0)
    assertThat(getUsages()).hasSize(1)

    // A further refreshImages() shouldn't generate another event.
    window.refreshImages(1.0)
    assertThat(getUsages()).hasSize(1)

    // Load a new window with root id 2. This still shouldn't generate another event.
    rootId = 2
    val (window2, _, _) = inspectorRule.inspectorClient.treeLoader.loadComponentTree(createFakeData(rootId),
                                                                                     ResourceLookup(inspectorRule.project),
                                                                                     inspectorRule.inspectorClient.process)!!
    window2!!.refreshImages(1.0)
    assertThat(getUsages()).hasSize(1)
    // disconnecting causes two separate events
    inspectorRule.startLaunch(4)
    // Now disconnect and reconnect. This should generate another event.
    inspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    inspectorRule.awaitLaunch()
    waitForCondition(10, TimeUnit.SECONDS) { !inspectorRule.inspectorClient.isConnected }

    inspectorRule.startLaunch(2)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.awaitLaunch()
    waitForCondition(10, TimeUnit.SECONDS) { inspectorRule.inspectorClient.isConnected }

    (inspectorRule.inspectorClient.treeLoader as AppInspectionTreeLoader).skiaParser = skiaParser
    val (window3, _, _) = inspectorRule.inspectorClient.treeLoader.loadComponentTree(createFakeData(rootId),
                                                                                     ResourceLookup(inspectorRule.project),
                                                                                     inspectorRule.inspectorClient.process)!!
    window3!!.refreshImages(1.0)
    assertThat(getUsages()).hasSize(2)
  }

  private fun waitForEvents(expectedLayoutInspectorMetricsEventCount: Int): List<LoggedUsage> {
    var usages: List<LoggedUsage> = emptyList()
    waitForCondition(10, TimeUnit.SECONDS) {
      usages = usageTrackerRule.testTracker.usages
        .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
      usages.size >= expectedLayoutInspectorMetricsEventCount
    }
    return usages
  }

  private fun createFakeData(
    rootId: Long,
    screenshotType: LayoutInspectorViewProtocol.Screenshot.Type = LayoutInspectorViewProtocol.Screenshot.Type.SKP)
    : ViewLayoutInspectorClient.Data {
    val viewLayoutEvent = LayoutInspectorViewProtocol.LayoutEvent.newBuilder().apply {
      ViewString(1, "en-us")
      ViewString(2, "com.example")
      ViewString(3, "MyViewClass1")

      appContextBuilder.apply {
        configurationBuilder.apply {
          countryCode = 1
        }
      }

      rootView = ViewNode {
        id = rootId
        packageName = 2
        className = 3
        bounds = ViewBounds(ViewRect(100, 200))
      }

      screenshotBuilder.apply {
        type = screenshotType
        bytes = ByteString.copyFrom(byteArrayOf(1, 2, 3))
      }
    }.build()

    return ViewLayoutInspectorClient.Data(
      11,
      listOf(123, 456),
      viewLayoutEvent,
      null
    )
  }
}