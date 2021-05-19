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
package com.android.tools.idea.layoutinspector

import com.android.testutils.MockitoKt
import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.legacydevice.LegacyTreeLoader
import com.android.tools.idea.layoutinspector.ui.SelectProcessAction
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class LegacyMetricsTest {

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withLegacyClient().withDefaultDevice()

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachSuccessViaSelectProcess() {
    val client = inspectorRule.inspectorClient as LegacyClient
    val loader = Mockito.mock(LegacyTreeLoader::class.java)
    Mockito.`when`(loader.getAllWindowIds(ArgumentMatchers.any(), MockitoKt.eq(client))).thenReturn(listOf("window1", "window2", "window3"))
    client.treeLoader = loader

    val event = Mockito.mock(AnActionEvent::class.java)
    val presentation = Presentation()
    Mockito.`when`(event.presentation).thenReturn(presentation)

    val menuAction = SelectProcessAction(inspectorRule.inspector)
    val connectAction = findFirstConnectAction(menuAction, DEFAULT_PROCESS.name)
    connectAction.connect().get()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    Assert.assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8("123488"), deviceInfo.anonymizedSerialNumber)
    Assert.assertEquals("My Legacy Model", deviceInfo.model)
    Assert.assertEquals("Google", deviceInfo.manufacturer)
    Assert.assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    Assert.assertEquals(deviceInfo, studioEvent.deviceInfo)
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS, studioEvent.dynamicLayoutInspectorEvent.type)
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }

  @Test
  fun testAttachFailViaSelectProcess() {
    val event = Mockito.mock(AnActionEvent::class.java)
    val presentation = Presentation()
    Mockito.`when`(event.presentation).thenReturn(presentation)

    val menuAction = SelectProcessAction(inspectorRule.inspector)
    val connectAction = findFirstConnectAction(menuAction, DEFAULT_PROCESS.name)
    connectAction.connect().get()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    Assert.assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8("123488"), deviceInfo.anonymizedSerialNumber)
    Assert.assertEquals("My Legacy Model", deviceInfo.model)
    Assert.assertEquals("Google", deviceInfo.manufacturer)
    Assert.assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    Assert.assertEquals(deviceInfo, studioEvent.deviceInfo)
    Assert.assertEquals(DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE, studioEvent.dynamicLayoutInspectorEvent.type)
    Assert.assertEquals(AnonymizerUtil.anonymizeUtf8(inspectorRule.project.basePath!!), studioEvent.projectId)
  }

  private fun findFirstConnectAction(selectProcessAction: SelectProcessAction, withProcessName: String): SelectProcessAction.ConnectAction {
    selectProcessAction.updateActions(DataContext.EMPTY_CONTEXT)
    return selectProcessAction.getChildren(null)
             .asSequence()
             .filterIsInstance<SelectProcessAction.DeviceAction>()
             .flatMap { it.getChildren(null).asSequence() }
             .filterIsInstance<SelectProcessAction.ConnectAction>()
             .find { it.process.name == withProcessName } ?: error("Process not found")
  }
}

class LegacyMetricsTest2 {
  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withLegacyClient()

  @get:Rule
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachOnLaunchWithDelay() {
    val client = inspectorRule.inspectorClient as LegacyClient
    val loader = Mockito.mock(LegacyTreeLoader::class.java)
    Mockito.`when`(loader.getAllWindowIds(ArgumentMatchers.any(), MockitoKt.eq(client))).thenReturn(listOf("window1", "window2", "window3"))
    client.treeLoader = loader

    val preferredProcess = LayoutInspectorPreferredProcess(LEGACY_DEVICE.manufacturer, LEGACY_DEVICE.model, LEGACY_DEVICE.serial,
                                                           DEFAULT_PROCESS.name, LEGACY_DEVICE.featureLevel)
    client.attachIfSupported(preferredProcess)!!.get()
    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)

    var usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // The process hasn't started on the device yet, so we haven't logged anything yet.
    Assert.assertEquals(0, usages.size)

    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // Still nothing
    Assert.assertEquals(0, usages.size)

    // Now start the process
    inspectorRule.addProcess(LEGACY_DEVICE, DEFAULT_PROCESS)
    Thread.sleep(3000)
    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // We should have the attach request and success event now
    Assert.assertEquals(listOf(DynamicLayoutInspectorEventType.COMPATIBILITY_REQUEST,
                               DynamicLayoutInspectorEventType.COMPATIBILITY_SUCCESS),
                        usages.map { it.studioEvent.dynamicLayoutInspectorEvent.type })

    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // And we shouldn't get any more than the two events
    Assert.assertEquals(2, usages.size)
  }
}
