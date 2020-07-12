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

import com.android.tools.idea.layoutinspector.LayoutInspectorTransportRule
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.view
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test
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

    // Run an event and verify it's run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(2000, window1, window2))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with the same window id and verify only one is run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(3000, window1, window2))
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(3001, window1, window2))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(1, called)
    called = 0

    // Send two events with different window ids and verify they're both run
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(4000, window1, window2))
    transportRule.transportService.addEventToStream(client.selectedStream.streamId, createEvent(4001, window2, window1))
    transportRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    assertEquals(2, called)
    called = 0
  }

  private fun createEvent(
    timestamp: Long,
    window1: ViewNode,
    vararg otherWindows: ViewNode
  ): Common.Event {
    val originalTemplateEvent = transportRule.createComponentTreeEvent(window1)
    return Common.Event.newBuilder(originalTemplateEvent).apply {
      this.timestamp = timestamp
      groupId = Common.Event.EventGroupIds.INVALID_VALUE.toLong()
      layoutInspectorEvent = LayoutInspectorProto.LayoutInspectorEvent.newBuilder(originalTemplateEvent.layoutInspectorEvent).apply {
        val originalTree = originalTemplateEvent.layoutInspectorEvent.tree
        tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(originalTree).apply {
          otherWindows.forEach { addAllWindowIds(it.drawId) }
        }.build()
      }.build()
    }.build()
  }
}