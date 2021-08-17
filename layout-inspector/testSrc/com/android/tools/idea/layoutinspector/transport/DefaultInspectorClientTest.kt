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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.view
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.TimeUnit

private val PROCESS = MODERN_DEVICE.createProcess()

class TransportInspectorClientTest {
  private val transportRule = TransportInspectorRule()
  private val inspectorRule = LayoutInspectorRule(transportRule.createClientProvider()) { listOf(PROCESS.name) }

  @get:Rule
  val ruleChain = RuleChain.outerRule(transportRule).around(inspectorRule).around(EdtRule())!!

  @Test
  fun testCorrectEventsRun() {
    inspectorRule.processNotifier.fireConnected(PROCESS)
    val client = inspectorRule.inspectorClient as TransportInspectorClient
    var called = 0
    // Need to register as a group that's not already used.
    client.register(Common.Event.EventGroupIds.INVALID) { called++ }
    val window1 = view(123)
    val window2 = view(321)
    val window3 = view(456)

    // Run an event and verify it's run
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window1, window2))
    transportRule.scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with the same window id and verify only one is run
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window1, window2))
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window1, window2))
    transportRule.scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with different window ids and verify they're both run
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window1, window2))
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window2, window1))
    transportRule.scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
    assertEquals(2, called)
    called = 0

    // Send a couple events, and then another event with only a new window, and verify only one is run.
    // Advance time a little between each, and then when we poll we'll get them in reverse order.
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window1, window2))
    transportRule.scheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window2, window1))
    transportRule.scheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent(window3))
    transportRule.scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Verify that an event with no windows gets run
    transportRule.transportService.addEventToStream(PROCESS.streamId, createEvent())
    transportRule.scheduler.advanceBy(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0
  }

  @Test
  fun clientCanConnectDisconnectAndReconnect() {
    inspectorRule.processNotifier.fireConnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    inspectorRule.processNotifier.fireConnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageSetAndReset() {
    inspectorRule.processNotifier.fireConnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(PROCESS.name)

    // Disconnect directly - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    // No other attributes were modified
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageOverriddenAndReset() {
    inspectorRule.attachDevice(PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package com.example.another-app")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo("com.example.another-app")

    inspectorRule.processNotifier.fireConnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(PROCESS.name)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageNotOverriddenIfMatching() {
    inspectorRule.attachDevice(PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package ${PROCESS.name}")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.processNotifier.fireConnected(PROCESS)
    transportRule.scheduler.advanceBy(1, TimeUnit.SECONDS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun testViewDebugAttributesNotOverridden() {
    inspectorRule.attachDevice(PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(PROCESS.device,
                                                     "settings put global debug_view_attributes 1")

    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributes).isEqualTo("1")
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  private fun createEvent(
    vararg windows: ViewNode
  ): Common.Event {
    val viewRoot = windows.firstOrNull()
    val originalTemplateEvent =
      viewRoot.intoComponentTreeEvent(inspectorRule.project, PROCESS.pid, transportRule.scheduler.currentTimeNanos,
                                      inspectorRule.inspectorModel.lastGeneration)

    return Common.Event.newBuilder(originalTemplateEvent).apply {
      groupId = Common.Event.EventGroupIds.INVALID_VALUE.toLong()
      layoutInspectorEvent = LayoutInspectorProto.LayoutInspectorEvent.newBuilder(originalTemplateEvent.layoutInspectorEvent).apply {
        val originalTree = originalTemplateEvent.layoutInspectorEvent.tree
        tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(originalTree).apply {
          windows.drop(1).forEach { addAllWindowIds(it.drawId) }
        }.build()
      }.build()
    }.build()
  }
}