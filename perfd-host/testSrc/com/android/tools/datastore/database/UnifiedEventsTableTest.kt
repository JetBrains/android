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
import org.junit.Ignore
import org.junit.Test
import java.util.function.Consumer

class UnifiedEventsTableTest : DatabaseTest<UnifiedEventsTable>() {

  companion object {
    val SESSION_1_1_1 = 0
    val SESSION_1_1_2 = 1
    val SESSION_1_1_3 = 2
    val SESSION_1_1_4 = 3
    val SESSION_2_1_5 = 4
    val SESSION_2_1_6 = 5
    val PROCESS_2_1_10 = 6 // Added to ensure filtering works properly.
    val SESSION_1_2_7 = 7
    val SESSION_2_2_8 = 8
    val SESSION_2_2_9 = 9
  }

  // List of events to generate in the database. This list is broken up by event id to make things easier to validate.
  val events = mutableListOf(eventBuilder(Common.Event.Kind.SESSION, false, 1, 1, 1),
                             eventBuilder(Common.Event.Kind.SESSION, false, 1, 1, 2),
                             eventBuilder(Common.Event.Kind.SESSION, false, 1, 1, 3),
                             eventBuilder(Common.Event.Kind.SESSION, false, 1, 1, 4),
                             eventBuilder(Common.Event.Kind.SESSION, false, 2, 1, 5),
                             eventBuilder(Common.Event.Kind.SESSION, true, 2, 1, 6),
                             eventBuilder(Common.Event.Kind.PROCESS, true, 2, 1, 10),
                             eventBuilder(Common.Event.Kind.SESSION, false, 1, 2, 7),
                             eventBuilder(Common.Event.Kind.SESSION, false, 2, 2, 8),
                             eventBuilder(Common.Event.Kind.SESSION, true, 2, 2, 9))

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
                               false,
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
      isEnded = false
      sessionId = 1
      groupId = 1
      session = Common.SessionData.newBuilder().setSessionStarted(Common.SessionData.SessionStarted.newBuilder().setPid(1)).build()
    }.build()
    table.insertUnifiedEvent(1, event)

    val updatedEvent = event.toBuilder().apply {
      session = Common.SessionData.newBuilder().setSessionStarted(Common.SessionData.SessionStarted.newBuilder().setPid(2)).build()
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
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.PROCESS).build(),
                   PROCESS_2_1_10)
  }

  @Test
  fun filterKindFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setFromTimestamp(3).build(),
                   SESSION_1_1_2, // Expected due to -1
                   SESSION_1_1_3,
                   SESSION_1_1_4,
                   SESSION_1_2_7,
                   SESSION_2_1_5,
                   SESSION_2_1_6,
                   SESSION_2_2_8,
                   SESSION_2_2_9)
  }

  @Test
  fun filterKindToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setToTimestamp(3).build(),
                   SESSION_1_1_1,
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4) // Expected due to +1
                // SESSION_2_1_5 Groups that start after our to timestamp are also not expected.
  }

  @Test
  fun filterKindFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setFromTimestamp(3)
                     .setToTimestamp(6).build(),
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4,
                   SESSION_2_1_5,
                   SESSION_2_1_6)
  }

  @Test
  fun filterKindSession() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1).build(),
                   SESSION_1_1_1,
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4,
                   SESSION_1_2_7)
  }

  @Test
  fun filterKindSessionFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(3).build(),
                   SESSION_1_1_2, // Expected due to -1
                   SESSION_1_1_3,
                   SESSION_1_1_4,
                   SESSION_1_2_7)
  }

  @Test
  fun filterKindSessionToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setToTimestamp(3).build(),
                   SESSION_1_1_1,
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4)
  }

  @Test
  fun filterKindSessionFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setFromTimestamp(3)
                     .setToTimestamp(4).build(),
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4)
  }

  @Test
  fun filterKindGroupId() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1).setGroupId(1).build(),
                   SESSION_1_1_1,
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4)
  }

  @Test
  fun filterKindGroupIdSessionFromTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(1)
                     .setFromTimestamp(3).build(),
                   SESSION_1_1_2, // Included from the -1.
                   SESSION_1_1_3,
                   SESSION_1_1_4)
  }

  @Test
  fun filterKindGroupIdSessionToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(2)
                     .setToTimestamp(8).build(),
                   SESSION_1_2_7)
  }

  @Test
  fun filterKindGroupIdSessionFromTimestampToTimestamp() {
    validateFilter(Profiler.GetEventGroupsRequest.newBuilder().setKind(Common.Event.Kind.SESSION)
                     .setSessionId(1)
                     .setGroupId(1)
                     .setFromTimestamp(3)
                     .setToTimestamp(3).build(),
                   SESSION_1_1_2,
                   SESSION_1_1_3,
                   SESSION_1_1_4)
  }

  @Test
  fun queryReturnsSameStatement() {
    val results = table.executeOneTimeQuery("SELECT * FROM [UnifiedEventsTable]", arrayOf())
    val repeatedResults = table.executeOneTimeQuery("SELECT * FROM [UnifiedEventsTable]", arrayOf())
    assertThat(results.statement).isSameAs(repeatedResults.statement)
  }

  private fun validateFilter(request: Profiler.GetEventGroupsRequest, vararg expectedIndices: Int) {
    val expectedResults = mutableListOf<Common.Event>()
    // Insert elements from our fixed list into the database.
    for (event in events) {
      table.insertUnifiedEvent(1, event)
    }

    // Build our expected results
    for (index in expectedIndices) {
      expectedResults.add(events[index])
    }

    // Query the database
    val result = table.queryUnifiedEventGroups(request)
    // Flatten the event groups.
    val actualResults = mutableListOf<Common.Event>()
    for (group in result) {
      actualResults.addAll(group.eventsList)
    }

    assertThat(actualResults).containsExactlyElementsIn(expectedResults)
  }

  private fun eventBuilder(kind: Common.Event.Kind,
                           isEnded: Boolean,
                           sessionId: Long,
                           eventId: Long,
                           timestamp: Long): Common.Event {
    return Common.Event.newBuilder()
      .setKind(kind)
      .setIsEnded(isEnded)
      .setSessionId(sessionId)
      .setGroupId(eventId)
      .setTimestamp(timestamp)
      .build()
  }
}