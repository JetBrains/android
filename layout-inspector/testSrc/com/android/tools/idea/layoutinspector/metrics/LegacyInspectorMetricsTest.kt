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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.LoggedUsage
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.pipeline.CONNECT_TIMEOUT_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.CONNECT_TIMEOUT_SECONDS
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyTreeLoader
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.stats.AnonymizerUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.ListenerCollection
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.LEGACY_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LegacyInspectorMetricsTest {

  private val disposableRule = DisposableRule()
  private val scheduler = VirtualTimeScheduler()
  private val windowIdsRetrievedLock = CountDownLatch(1)

  private val windowIds = mutableListOf<String>()
  private val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk()
  private val legacyClientProvider = InspectorClientProvider { params, inspector ->
    val loader = Mockito.mock(LegacyTreeLoader::class.java)
    whenever(loader.getAllWindowIds(ArgumentMatchers.any())).thenAnswer {
      windowIdsRetrievedLock.countDown()
      windowIds
    }
    val client = LegacyClientProvider(disposableRule.disposable, loader).create(params, inspector) as LegacyClient
    client.launchMonitor = InspectorClientLaunchMonitor(projectRule.project, ListenerCollection.createWithDirectExecutor(), scheduler)
    client
  }

  private val inspectorRule = LayoutInspectorRule(listOf(legacyClientProvider), projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectorRule).around(disposableRule)!!

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Before
  fun setUp() {
    inspectorRule.attachDevice(LEGACY_DEVICE)
  }

  @Test
  fun testAttachSuccessAfterProcessConnected() {
    windowIds.addAll(listOf("window1", "window2", "window3"))
    val process = LEGACY_DEVICE.createProcess()
    inspectorRule.processes.selectedProcess = process
    inspectorRule.processNotifier.fireDisconnected(process)

    val usages = waitFor3Events()
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertThat(deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(LEGACY_DEVICE.serial))
    assertThat(deviceInfo.model).isEqualTo(LEGACY_DEVICE.model)
    assertThat(deviceInfo.manufacturer).isEqualTo(LEGACY_DEVICE.manufacturer)
    assertThat(deviceInfo.deviceType).isEqualTo(DeviceInfo.DeviceType.LOCAL_PHYSICAL)

    var inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST)

    studioEvent = usages[1].studioEvent
    assertThat(studioEvent.deviceInfo).isEqualTo(deviceInfo)
    assertThat(studioEvent.dynamicLayoutInspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS)
    assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))

    studioEvent = usages[2].studioEvent
    inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertThat(studioEvent.deviceInfo).isEqualTo(deviceInfo)
    assertThat(studioEvent.projectId).isEqualTo(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!))
    assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.SESSION_DATA)
    assertThat(inspectorEvent.session.attach.clientType).isEqualTo(LEGACY_CLIENT)
    assertThat(inspectorEvent.session.attach.success).isTrue()
  }

  @Test
  fun testAttachFailAfterProcessConnected() {
    assertThat(windowIds.isEmpty()).isTrue() // No window IDs will cause attaching to fail
    val connectThread = Thread {
      inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    }
    connectThread.start()
    windowIdsRetrievedLock.await()

    // Launch monitor will set a banner
    scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
    val banner = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    assertThat(banner.notifications.single().message).isEqualTo(LayoutInspectorBundle.message(CONNECT_TIMEOUT_MESSAGE_KEY))

    // User disconnects:
    banner.notifications.single().actions.last().actionPerformed(mock())
    connectThread.join()
    val usages = waitFor3Events()
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertThat(deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(LEGACY_DEVICE.serial))
    assertThat(deviceInfo.model).isEqualTo(LEGACY_DEVICE.model)
    assertThat(deviceInfo.manufacturer).isEqualTo(LEGACY_DEVICE.manufacturer)
    assertThat(deviceInfo.deviceType).isEqualTo(DeviceInfo.DeviceType.LOCAL_PHYSICAL)

    var inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertThat(inspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST)

    studioEvent = usages[1].studioEvent
    inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertThat(studioEvent.deviceInfo).isEqualTo(deviceInfo)
    assertThat(studioEvent.dynamicLayoutInspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.ATTACH_ERROR)
    assertThat(inspectorEvent.errorInfo.attachErrorState).isEqualTo(AttachErrorState.ADB_PING)
    assertThat(inspectorEvent.errorInfo.attachErrorCode).isEqualTo(AttachErrorCode.CONNECT_TIMEOUT)

    studioEvent = usages[2].studioEvent
    inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertThat(studioEvent.deviceInfo).isEqualTo(deviceInfo)
    assertThat(studioEvent.dynamicLayoutInspectorEvent.type).isEqualTo(DynamicLayoutInspectorEventType.SESSION_DATA)
    assertThat(inspectorEvent.session.attach.clientType).isEqualTo(LEGACY_CLIENT)
    assertThat(inspectorEvent.session.attach.success).isFalse()
    assertThat(inspectorEvent.session.attach.errorInfo.attachErrorState).isEqualTo(AttachErrorState.ADB_PING)
    assertThat(inspectorEvent.session.attach.errorInfo.attachErrorCode).isEqualTo(AttachErrorCode.CONNECT_TIMEOUT)
  }

  private fun waitFor3Events(): List<LoggedUsage> {
    var usages: List<LoggedUsage> = emptyList()
    waitForCondition(10, TimeUnit.SECONDS) {
      usages = usageTrackerRule.testTracker.usages
        .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
      usages.size >= 3
    }
    return usages
  }
}
