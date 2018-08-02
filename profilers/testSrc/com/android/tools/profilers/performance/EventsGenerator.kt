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
package com.android.tools.profilers.performance

import com.android.tools.datastore.database.EventsTable
import com.android.tools.profiler.proto.EventProfiler

import java.sql.Connection

class EventsGenerator(connection: Connection) : DataGenerator(connection) {

  private val myTable = EventsTable()
  private var myCurrentActivity: EventProfiler.ActivityData? = null
  private var myGenerateResumeState = false

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    generateActivityData(timestamp, properties)
    // Since events don't occur every frame we reduce the generation rate
    if (isWithinProbability(.1)) {
      generateSystemData(timestamp, properties)
    }
  }

  private fun generateSystemData(timestamp: Long, properties: GeneratorProperties) {
    val system = EventProfiler.SystemData.newBuilder()
      .setActionId(random.nextInt())
      .setType(EventProfiler.SystemData.SystemEventType.TOUCH)
      .setStartTimestamp(timestamp)
      .setEndTimestamp(timestamp)
      .setPid(properties.pid)
      .setEventId(random.nextInt().toLong())
      .setEventData("")
      .build()
    myTable.insertOrReplace(system.eventId, properties.session, system)
  }

  private fun generateActivityData(timestamp: Long, properties: GeneratorProperties) {
    // Since activities don't change that frequently reduce the generation rate.
    if (myCurrentActivity == null || isWithinProbability(.25)) {
      myCurrentActivity = EventProfiler.ActivityData.newBuilder()
        .setName(random.nextLong().toString())
        .setHash(random.nextLong())
        .setPid(properties.pid)
        .build()
      myGenerateResumeState = true
    }
    val state =
      if (myGenerateResumeState)
        EventProfiler.ActivityStateData.ActivityState.RESUMED
      else
        EventProfiler.ActivityStateData.ActivityState.PAUSED
    myCurrentActivity = myCurrentActivity!!.toBuilder().addStateChanges(EventProfiler.ActivityStateData.newBuilder()
                                                                          .setTimestamp(timestamp)
                                                                          .setState(state)
                                                                          .build())
      .build()
    myTable.insertOrReplace(myCurrentActivity!!.hash, properties.session, myCurrentActivity)
    myGenerateResumeState = !myGenerateResumeState
  }
}