/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.database

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Profiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.function.Consumer

class UnifiedEventsTableTest : DatabaseTest<UnifiedEventsTable>() {

  // List of events to generate in the database. This list is broken up by event id to make things easier to validate.
  val events = mutableListOf(mutableListOf(eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 1, 1, 1),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 1, 1, 2),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 1, 1, 3),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 1, 1, 4),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 2, 1, 3),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 2, 1, 4)),
                             mutableListOf(eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 1, 2, 3),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 2, 2, 2),
                                           eventBuilder(Common.Event.Kind.SESSION, Common.Event.Type.SESSION_ENDED, 2, 2, 3)))

  override fun createTable(): UnifiedEventsTable {
    return UnifiedEventsTable()
  }

  override fun getTableQueryMethodsForVerification(): List<Consumer<UnifiedEventsTable>> {
    val events = mutableListOf(Common.Event.newBuilder().build())
    return mutableListOf(
      (Consumer { it.insertUnifiedEvent(1, events[0]) }),
      (Consumer {
        it.queryUnifiedEventGroups(
          Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION).setSessionId(1).setToTimestamp(10).build())
      }),
      (Consumer { it.queryUnifiedEvents() }))
  }

  private fun insertData(count: Int, incrementSession: Boolean, incrementGroupId: Boolean): List<Common.Event> {
    val events = mutableListOf<Common.Event>()
    for (i in 0 until count) {
      val event = eventBuilder(Common.Event.Kind.SESSION,
                               Common.Event.Type.SESSION_STARTED,
                               if (!incrementSession) 1L else i + 1L,
                               if (!incrementGroupId) 1L else i + 1L,
                               i + 1L)
      events.add(event)
      table.insertUnifiedEvent(1, event)
    }
    return events
  }

  @Test
  fun insertDuplicatedData() {
    // This validates that sql should not throw an exception
    val event = Common.Event.newBuilder().apply {
      kind = Common.Event.Kind.SESSION
      type = Common.Event.Type.SESSION_STARTED
      sessionId = 1
      groupId = 1
      sessionStarted = Common.SessionStarted.newBuilder().setPid(1).build()
    }.build()
    table.insertUnifiedEvent(1, event)

    val updatedEvent = event.toBuilder().apply {
      sessionStarted = Common.SessionStarted.newBuilder().setPid(2).build()
    }.build()

    table.insertUnifiedEvent(1, updatedEvent)

    // Validate that no data got updated.
    val eventResult = table.queryUnifiedEvents()
    assertThat(eventResult).containsExactlyElementsIn(listOf(event))
  }

  @Test
  fun queryEvents() {
    val events = insertData(2, true, true)
    // Validate we have data inserted
    val eventResult = table.queryUnifiedEvents()
    assertThat(eventResult).containsExactlyElementsIn(events)
  }

  @Test
  fun filterNoKind() {
    insertData(5, false, true)
    val result = table.queryUnifiedEventGroups(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.NONE).build())
    assertThat(result).isEmpty()
  }

  @Test
  fun filterKind() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION).build())
  }

  @Test
  fun filterKindFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setFromTimestamp(2).build())
  }

  @Test
  fun filterKindToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setToTimestamp(2).build())
  }

  @Test
  fun filterKindFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setFromTimestamp(2)
                     .setToTimestamp(4).build())
  }

  @Test
  fun filterKindSession() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1).build())
  }

  @Test
  fun filterKindSessionFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(3).build())
  }

  @Test
  fun filterKindSessionToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setToTimestamp(3).build())
  }

  @Test
  fun filterKindSessionFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(2)
                     .setToTimestamp(3).build())
  }

  @Test
  fun filterKindGroupId() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1).setGroupId(1).build())
  }

  @Test
  fun filterKindGroupIdSessionFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(1)
                     .setFromTimestamp(3).build())
  }

  @Test
  fun filterKindGroupIdSessionToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(2)
                     .setToTimestamp(3).build())
  }

  @Test
  fun filterKindGroupIdSessionFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(2)
                     .setFromTimestamp(2)
                     .setToTimestamp(3).build())
  }

  private fun validateFilter(request: Profiler.GetEventGroupsRequest) {
    val results = mutableListOf<List<Common.Event>>()
    for (eventGroup in events) {
      // Insert elements from our fixed list into the database.
      eventGroup.forEach { table.insertUnifiedEvent(1, it) }
      val expected = mutableListOf<Common.Event>()

      // Filter our expected results to what we expect from the database.
      eventGroup.filterTo(expected) {
        var result = true
        if (it.kind != request.kind) {
          result = false
        }
        if (request.sessionId != 0L && request.sessionId != it.sessionId) {
          result = false
        }
        if (request.groupId != 0L && request.groupId != it.groupId) {
          result = false
        }
        if (request.fromTimestamp != 0L && it.timestamp < request.fromTimestamp) {
          result = false
        }
        if (request.toTimestamp != 0L && it.timestamp > request.toTimestamp) {
          result = false
        }
        result
      }

      // Add only list with elements to our expected results.
      if (!expected.isEmpty()) {
        results.add(expected)
      }
    }
    // Query the database and validate results.
    val result = table.queryUnifiedEventGroups(request)
    assertThat(result).hasSize(results.size)
    var index = 0
    for (group in result) {
      assertThat(group.eventsList).containsExactlyElementsIn(results[index])
      index++
    }
  }

  private fun eventBuilder(kind: Common.Event.Kind,
                           type: Common.Event.Type,
                           sessionId: Long,
                           eventId: Long,
                           timestamp: Long): Common.Event{
    return Common.Event.newBuilder()
      .setKind(kind)
      .setType(type)
      .setSessionId(sessionId)
      .setGroupId(eventId)
      .setTimestamp(timestamp)
      .build()
  }
}