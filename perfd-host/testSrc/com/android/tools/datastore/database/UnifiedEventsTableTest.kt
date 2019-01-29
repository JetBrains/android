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

import com.android.tools.profiler.proto.Profiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.function.Consumer

class UnifiedEventsTableTest : DatabaseTest<UnifiedEventsTable>() {

  // List of events to generate in the database. This list is broken up by event id to make things easier to validate.
  val events = mutableListOf(mutableListOf(eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 1, 1, 1),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 1, 1, 2),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 1, 1, 3),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 1, 1, 4),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 2, 1, 3),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 2, 1, 4)),
                             mutableListOf(eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 1, 2, 3),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 2, 2, 2),
                                           eventBuilder(Profiler.Event.Kind.SESSION, Profiler.Event.Type.SESSION_ENDED, 2, 2, 3)))

  override fun createTable(): UnifiedEventsTable {
    return UnifiedEventsTable()
  }

  override fun getTableQueryMethodsForVerification(): List<Consumer<UnifiedEventsTable>> {
    val events = mutableListOf(Profiler.Event.newBuilder().build())
    return mutableListOf(
      (Consumer { it.insertUnifiedEvents(1, events) }),
      (Consumer {
        it.queryUnifiedEventGroups(
          Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION).setSessionId(1).setToTimestamp(10).build())
      }),
      (Consumer { it.queryUnifiedEvents(Profiler.GetEventsRequest.getDefaultInstance()) }))
  }

  private fun insertData(count: Int, incrementSession: Boolean, incrementEventId: Boolean): List<Profiler.Event> {
    val events = mutableListOf<Profiler.Event>()
    for (i in 0 until count) {
      events.add(eventBuilder(Profiler.Event.Kind.SESSION,
                              Profiler.Event.Type.SESSION_STARTED,
                              if (!incrementSession) 1L else i + 1L,
                              if (!incrementEventId) 1L else i + 1L,
                              i + 1L))
    }
    table.insertUnifiedEvents(1, events)
    return events
  }

  @Test
  fun insertDuplicatedData() {
    // This validates that sql should not throw an exception
    val events = mutableListOf(Profiler.Event.newBuilder().apply {
      kind = Profiler.Event.Kind.SESSION
      type = Profiler.Event.Type.SESSION_STARTED
      sessionId = 1
      eventId = 1
      sessionStarted = Profiler.SessionStarted.newBuilder().setPid(1).build()
    }.build())
    table.insertUnifiedEvents(1, events)

    val updatedEvents = mutableListOf(events[0].toBuilder().apply {
      sessionStarted = Profiler.SessionStarted.newBuilder().setPid(2).build()
    }.build())
    table.insertUnifiedEvents(1, updatedEvents)
    // Validate that no data got updated.
    var eventResult = table.queryUnifiedEvents(Profiler.GetEventsRequest.newBuilder().setFromTimestamp(0)
                                                 .setToTimestamp(3).build())
    assertThat(eventResult).containsExactlyElementsIn(events)
  }

  @Test
  fun queryEvents() {
    val events = insertData(2, true, true)
    // Validate we have data inserted
    var eventResult = table.queryUnifiedEvents(Profiler.GetEventsRequest.newBuilder().setFromTimestamp(0)
                                                 .setToTimestamp(3).build())
    assertThat(eventResult).containsExactlyElementsIn(events)
    // Validate request filters on timestamp.
    eventResult = table.queryUnifiedEvents(Profiler.GetEventsRequest.newBuilder().setFromTimestamp(0)
                                             .setToTimestamp(1).build())
    assertThat(eventResult).containsExactly(events[0])
  }

  @Test
  fun filterNoKind() {
    insertData(5, false, true)
    val result = table.queryUnifiedEventGroups(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.NONE).build())
    assertThat(result).isEmpty()
  }

  @Test
  fun filterKind() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION).build())
  }

  @Test
  fun filterKindFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setFromTimestamp(2).build())
  }

  @Test
  fun filterKindToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setToTimestamp(2).build())
  }

  @Test
  fun filterKindFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setFromTimestamp(2)
                     .setToTimestamp(4).build())
  }

  @Test
  fun filterKindSession() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setSessionId(1).build())
  }

  @Test
  fun filterKindSessionFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(3).build())
  }

  @Test
  fun filterKindSessionToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setToTimestamp(3).build())
  }

  @Test
  fun filterKindSessionFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Profiler.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(2)
                     .setToTimestamp(3).build())
  }

  private fun validateFilter(request: Profiler.GetEventGroupsRequest) {
    val results = mutableListOf<List<Profiler.Event>>()
    for (eventGroup in events) {
      // Insert elements from our fixed list into the database.
      table.insertUnifiedEvents(1, eventGroup)
      val expected = mutableListOf<Profiler.Event>()

      // Filter our expected results to what we expect from the database.
      eventGroup.filterTo(expected, {
        var result = true
        if (it.kind != request.kind) {
          result = false
        }
        if (request.sessionId != 0L && request.sessionId != it.sessionId) {
          result = false
        }
        if (request.fromTimestamp != 0L && it.timestamp < request.fromTimestamp) {
          result = false
        }
        if (request.toTimestamp != 0L && it.timestamp > request.toTimestamp) {
          result = false
        }
        result
      })

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

  private fun eventBuilder(kind: Profiler.Event.Kind,
                           type: Profiler.Event.Type,
                           sessionId: Long,
                           eventId: Long,
                           timestamp: Long): Profiler.Event {
    return Profiler.Event.newBuilder()
      .setKind(kind)
      .setType(type)
      .setSessionId(sessionId)
      .setEventId(eventId)
      .setTimestamp(timestamp)
      .build()
  }
}