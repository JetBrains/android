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

import com.android.tools.datastore.database.CpuTable
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.Trace

import java.sql.Connection

class CpuGenerator(connection: Connection) : DataGenerator(connection) {
  companion object {
    private val NUMBER_OF_THREADS = 50
  }

  private val myTable = CpuTable()
  private var lastTraceInfoTimestamp = 0L

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    if (lastTraceInfoTimestamp == 0L) {
      lastTraceInfoTimestamp = timestamp
    }
    generateUsage(timestamp, properties)
    generateThreadSnapshots(timestamp, properties)
    if (isWithinProbability(0.01)) {
      generateTraceInfo(timestamp, properties)
      lastTraceInfoTimestamp = timestamp
    }

  }

  private fun generateTraceInfo(timestamp: Long, properties: GeneratorProperties) {
    val threadIds = mutableListOf<Int>()
    for (i in 0..NUMBER_OF_THREADS) {
      threadIds.add(i)
    }
    val trace = Cpu.CpuTraceInfo.newBuilder()
      .setFromTimestamp((lastTraceInfoTimestamp + timestamp) / 2)
      .setToTimestamp(timestamp)
      .setTraceId(random.nextLong())
      .setConfiguration(Trace.TraceConfiguration.newBuilder()
                          .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_UI)
                          .setUserOptions(Trace.UserOptions.newBuilder()
                                            .setTraceMode(Trace.TraceMode.SAMPLED)))
      .build()
    myTable.insertTraceInfo(properties.session, trace)
  }

  private fun generateThreadSnapshots(timestamp: Long, properties: GeneratorProperties) {
    val snapshots = mutableListOf<CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot>()
    for (i in 0..NUMBER_OF_THREADS) {
      snapshots.add(CpuProfiler.GetThreadsResponse.ThreadSnapshot.Snapshot.newBuilder()
                      .setName(i.toString())
                      .setTid(i)
                      .setState(Cpu.CpuThreadData.State.RUNNING)
                      .build()
      )
    }
    myTable.insertSnapshot(properties.session, timestamp, snapshots)
  }

  private fun generateUsage(timestamp: Long, properties: GeneratorProperties) {
    val data = Cpu.CpuUsageData.newBuilder()
      .addCores(Cpu.CpuCoreUsageData.newBuilder()
                  .setCore(0)
                  .setFrequencyInKhz(1024)
                  .setElapsedTimeInMillisec(timestamp)
                  .setSystemCpuTimeInMillisec(timestamp))
      .addCores(Cpu.CpuCoreUsageData.newBuilder()
                  .setCore(1)
                  .setFrequencyInKhz(1024)
                  .setElapsedTimeInMillisec(timestamp)
                  .setSystemCpuTimeInMillisec(timestamp))
      .addCores(Cpu.CpuCoreUsageData.newBuilder()
                  .setCore(2)
                  .setFrequencyInKhz(1024)
                  .setElapsedTimeInMillisec(timestamp)
                  .setSystemCpuTimeInMillisec(timestamp))
      .addCores(Cpu.CpuCoreUsageData.newBuilder()
                  .setCore(3)
                  .setFrequencyInKhz(1024)
                  .setElapsedTimeInMillisec(timestamp)
                  .setSystemCpuTimeInMillisec(timestamp))
      .setEndTimestamp(timestamp)
      .setElapsedTimeInMillisec(timestamp)
      .setSystemCpuTimeInMillisec(timestamp)
      .setAppCpuTimeInMillisec(timestamp)
      .build()
    myTable.insert(properties.session, data)
  }
}