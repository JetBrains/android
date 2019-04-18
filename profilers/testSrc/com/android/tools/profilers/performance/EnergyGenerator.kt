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

import com.android.tools.datastore.database.EnergyTable
import com.android.tools.profiler.proto.Energy
import com.android.tools.profiler.proto.EnergyProfiler

import java.sql.Connection

class EnergyGenerator(connection: Connection) : DataGenerator(connection) {

  private val myTable = EnergyTable()

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    generateEnergySample(timestamp, properties)
    // Since events don't occur every frame we reduce the generation rate
    if (isWithinProbability(.1)) {
      generateEnergyEvent(timestamp, properties)
    }
  }

  private fun generateEnergyEvent(timestamp: Long, properties: GeneratorProperties) {
    val event = EnergyProfiler.EnergyEvent.newBuilder()
      .setEventId(random.nextInt())
      .setPid(properties.pid)
      .setJobStarted(EnergyProfiler.JobStarted.newBuilder()
                       .setParams(EnergyProfiler.JobParameters.newBuilder()
                                    .setExtras("Test")))
      .setJobFinished(EnergyProfiler.JobFinished.getDefaultInstance())
      .setIsTerminal(true)
      .build()
    myTable.insertOrReplace(properties.session, event)
  }

  private fun generateEnergySample(timestamp: Long, properties: GeneratorProperties) {
    val sample = EnergyProfiler.EnergySample.newBuilder()
      .setEnergyUsage(
        Energy.EnergyUsageData.newBuilder()
          .setCpuUsage(random.nextInt() % 100)
          .setLocationUsage(random.nextInt() % 100)
          .setNetworkUsage(random.nextInt() % 100))
      .setTimestamp(timestamp)
      .build()
    myTable.insertOrReplace(properties.session, sample)
  }
}