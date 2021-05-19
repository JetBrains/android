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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.layoutinspector.DEFAULT_DEVICE
import com.android.tools.idea.layoutinspector.DEFAULT_PROCESS
import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.view
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand.Type
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultInspectorClientTest {
  @get:Rule
  val transportRule = LayoutInspectorTransportRule().withDefaultDevice()

  @Test
  fun testCorrectEventsRun() {
    val client = transportRule.inspectorClient
    var called = 0
    // Need to register as a group that's not already used.
    client.register(Common.Event.EventGroupIds.INVALID) { called++ }
    transportRule.attach()
    val window1 = view(123)
    val window2 = view(321)
    val window3 = view(456)

    // Run an event and verify it's run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window1, window2))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with the same window id and verify only one is run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window1, window2))
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window1, window2))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with different window ids and verify they're both run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window1, window2))
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window2, window1))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(2, called)
    called = 0

    // Send a couple events, and then another event with only a new window, and verify only one is run.
    // Advance time a little between each, and then when we poll we'll get them in reverse order.
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window1, window2))
    transportRule.advanceTime(1, TimeUnit.MILLISECONDS)
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window2, window1))
    transportRule.advanceTime(1, TimeUnit.MILLISECONDS)
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(window3))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Verify that an event with no windows gets run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent())
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0
  }

  @Test
  fun testRestartWillWaitOnDisconnection() {
    val startedLatch = CountDownLatch(2)
    transportRule.withCommandHandler(Type.START) { _,_ -> startedLatch.countDown() }
    transportRule.attach()
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    val client = transportRule.inspectorClient
    val autoLaunch = LayoutInspectorPreferredProcess(
      DEFAULT_DEVICE.manufacturer,
      DEFAULT_DEVICE.model,
      DEFAULT_DEVICE.serial,
      DEFAULT_PROCESS.name,
      DEFAULT_DEVICE.featureLevel)
    assertThat(client.isConnected).isTrue()
    client.attachIfSupported(autoLaunch)?.get()
    transportRule.advanceTime(1, TimeUnit.SECONDS)
    transportRule.advanceTime(1, TimeUnit.SECONDS)
    transportRule.advanceTime(1, TimeUnit.SECONDS)
    transportRule.advanceTime(1, TimeUnit.SECONDS)

    client.disconnect().get()
    assertThat(client.isConnected).isFalse()
    transportRule.advanceTime(1, TimeUnit.SECONDS)
    transportRule.advanceTime(1, TimeUnit.SECONDS)
    assertThat(startedLatch.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(client.isConnected).isTrue()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageSetAndReset() {
    transportRule.attach()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isEqualTo("myProcess")
    transportRule.inspectorClient.disconnect().get()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isNull()
    assertThat(transportRule.debugViewAttributesChanges).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageOverriddenAndReset() {
    transportRule.withDebugViewAttributesApplicationPackage("com.example.another-app").attach()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isEqualTo("myProcess")
    transportRule.inspectorClient.disconnect().get()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isNull()
    assertThat(transportRule.debugViewAttributesChanges).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageNotOverriddenIfMatching() {
    transportRule.withDebugViewAttributesApplicationPackage("myProcess").attach()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isEqualTo("myProcess")
    transportRule.inspectorClient.disconnect().get()
    assertThat(transportRule.debugViewAttributes).isNull()
    assertThat(transportRule.debugViewAttributesApplicationPackage).isEqualTo("myProcess")
    assertThat(transportRule.debugViewAttributesChanges).isEqualTo(0)
  }

  @Test
  fun testViewDebugAttributesNotOverridden() {
    transportRule.withDebugViewAttributes("1").attach()
    assertThat(transportRule.debugViewAttributes).isEqualTo("1")
    assertThat(transportRule.debugViewAttributesApplicationPackage).isNull()
    transportRule.inspectorClient.disconnect().get()
    assertThat(transportRule.debugViewAttributes).isEqualTo("1")
    assertThat(transportRule.debugViewAttributesApplicationPackage).isNull()
    assertThat(transportRule.debugViewAttributesChanges).isEqualTo(0)
  }

  private fun createEvent(
    vararg windows: ViewNode
  ): Common.Event {
    val originalTemplateEvent = transportRule.createComponentTreeEvent(windows.firstOrNull())
    return Common.Event.newBuilder(originalTemplateEvent).apply {
      this.timestamp = transportRule.getCurrentTimeNanos()
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