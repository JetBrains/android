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
import com.android.tools.profiler.proto.EventProfiler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.function.Consumer

class EventsTest : DatabaseTest<EventsTable>() {
  override fun getTableQueryMethodsForVerification(): MutableList<Consumer<EventsTable>> {
    val methodCalls = mutableListOf<Consumer<EventsTable>>()
    methodCalls.add(
      Consumer { t -> t.insertOrReplace(0, Common.Session.getDefaultInstance(), EventProfiler.ActivityData.getDefaultInstance()) })
    methodCalls.add(
      Consumer { t -> t.insertOrReplace(0, Common.Session.getDefaultInstance(), EventProfiler.SystemData.getDefaultInstance()) })
    methodCalls.add(Consumer { t -> assertThat(t.findActivityDataOrNull(Common.Session.getDefaultInstance(), 0)).isNull() })
    methodCalls.add(Consumer { t -> assertThat(t.getActivityDataBySession(Common.Session.getDefaultInstance())).isEmpty() })
    methodCalls.add(Consumer { t -> assertThat(t.getSystemDataByRequest(EventProfiler.EventDataRequest.getDefaultInstance())).isEmpty() })
    return methodCalls
  }

  override fun createTable(): EventsTable {
    return EventsTable()
  }

  @Test
  fun insertAndGetSystemData() {
    // Test no end time alone.
    table.insertOrReplace(0, MAIN_SESSION, NO_END_SYSTEM_DATA)
    var systemEvents = table.getSystemDataByRequest(
      EventProfiler.EventDataRequest.newBuilder().setSession(MAIN_SESSION).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build())
    assertThat(systemEvents).containsExactly(NO_END_SYSTEM_DATA)

    // Test no end time, and an event that ends
    table.insertOrReplace(1, MAIN_SESSION, LONG_SYSTEM_DATA)
    systemEvents = table.getSystemDataByRequest(
      EventProfiler.EventDataRequest.newBuilder().setSession(MAIN_SESSION).setStartTimestamp(0).setEndTimestamp(Long.MAX_VALUE).build())
    assertThat(systemEvents).containsExactly(NO_END_SYSTEM_DATA, LONG_SYSTEM_DATA)

    // Test getting the no end time event after an event has already ended.
    systemEvents = table.getSystemDataByRequest(
      EventProfiler.EventDataRequest.newBuilder().setSession(MAIN_SESSION).setStartTimestamp(200).setEndTimestamp(Long.MAX_VALUE).build())
    assertThat(systemEvents).containsExactly(NO_END_SYSTEM_DATA)
  }

  @Test
  fun insertAndGetActivityData() {
    // Insert a simple activity to get back
    table.insertOrReplace(0, MAIN_SESSION, SIMPLE_ACTIVITY_DATA)
    var activity = table.getActivityDataBySession(MAIN_SESSION)
    assertThat(activity).containsExactly(SIMPLE_ACTIVITY_DATA)

    // Update activity and validate we get back the updated one.
    table.insertOrReplace(0, MAIN_SESSION, ACTIVITY_DATA_UPDATE)
    activity = table.getActivityDataBySession(MAIN_SESSION)
    assertThat(activity).containsExactly(ACTIVITY_DATA_UPDATE)
  }

  @Test
  fun findActivity() {
    // Test no activities found.
    var activity = table.findActivityDataOrNull(MAIN_SESSION, 0)
    assertThat(activity).isNull()

    // Insert an activity and find it.
    table.insertOrReplace(0, MAIN_SESSION, SIMPLE_ACTIVITY_DATA)
    activity = table.findActivityDataOrNull(MAIN_SESSION, 0)
    assertThat(activity).isEqualTo(SIMPLE_ACTIVITY_DATA)

    // Test no activity found for a different id.
    activity = table.findActivityDataOrNull(MAIN_SESSION, 1)
    assertThat(activity).isNull()
  }

  companion object {
    protected val TEST_APP_ID = 5678
    private val ACTION_ID = 1234
    private val MAIN_SESSION = Common.Session.newBuilder().setSessionId(1L).setDeviceId(1234).build()
    private val NO_END_SYSTEM_DATA = EventProfiler.SystemData
      .newBuilder()
      .setPid(TEST_APP_ID)
      .setActionId(ACTION_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(0)
      .setEventId(1)
      .build()
    private val LONG_SYSTEM_DATA = EventProfiler.SystemData
      .newBuilder()
      .setPid(TEST_APP_ID)
      .setActionId(ACTION_ID)
      .setStartTimestamp(0)
      .setEndTimestamp(100)
      .setEventId(2)
      .build()
    private val SIMPLE_ACTIVITY_DATA = EventProfiler.ActivityData
      .newBuilder()
      .setPid(TEST_APP_ID)
      .setName("Test")
      .setHash("Test".hashCode().toLong())
      .addStateChanges(
        EventProfiler.ActivityStateData
          .newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.CREATED).setTimestamp(0).build()
      ).build()
    private val ACTIVITY_DATA_UPDATE = EventProfiler.ActivityData
      .newBuilder()
      .setPid(TEST_APP_ID)
      .setName("Test")
      .setHash("Test".hashCode().toLong())
      .addStateChanges(
        EventProfiler.ActivityStateData
          .newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.STARTED).setTimestamp(10).build())
      .addStateChanges(
        EventProfiler.ActivityStateData
          .newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.PAUSED).setTimestamp(20).build())
      .addStateChanges(
        EventProfiler.ActivityStateData
          .newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.RESUMED).setTimestamp(30).build())
      .addStateChanges(
        EventProfiler.ActivityStateData
          .newBuilder().setState(EventProfiler.ActivityStateData.ActivityState.PAUSED).setTimestamp(40).build())
      .build()
  }
}
