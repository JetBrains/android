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

import com.android.tools.datastore.database.MemoryStatsTable
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.MemoryProfiler

import java.sql.Connection
import java.util.ArrayList

class MemoryGenerator(connection: Connection) : DataGenerator(connection) {

  private val table = MemoryStatsTable()

  init {
    table.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    generateAllocStatsSamples(timestamp, properties)
    generateMemorySamples(timestamp, properties)
    if (isWithinProbability(.1)) {
      generateGcStatsSamples(timestamp, properties)
    }
  }

  private fun generateGcStatsSamples(timestamp: Long, properties: GeneratorProperties) {
    val samples = ArrayList<MemoryProfiler.MemoryData.GcStatsSample>()
      samples.add(MemoryProfiler.MemoryData.GcStatsSample.newBuilder().setStartTime(timestamp).setEndTime(timestamp - 1).build())
    table.insertGcStats(properties.session, samples)
  }

  private fun generateMemorySamples(timestamp: Long, properties: GeneratorProperties) {
    val samples = ArrayList<MemoryProfiler.MemoryData.MemorySample>()
    samples.add(MemoryProfiler.MemoryData.MemorySample.newBuilder()
                  .setMemoryUsage(Memory.MemoryUsageData.newBuilder()
                    .setCodeMem(random.nextInt())
                    .setGraphicsMem(random.nextInt())
                    .setJavaMem(random.nextInt())
                    .setNativeMem(random.nextInt())
                    .setOthersMem(random.nextInt())
                    .setStackMem(random.nextInt())
                    .setTotalMem(random.nextInt()))
                  .setTimestamp(timestamp)
                  .build())
    table.insertMemory(properties.session, samples)
  }

  private fun generateAllocStatsSamples(timestamp: Long, properties: GeneratorProperties) {
    val stats = ArrayList<MemoryProfiler.MemoryData.AllocStatsSample>()
    stats.add(MemoryProfiler.MemoryData.AllocStatsSample.newBuilder()
                .setAllocStats(Memory.MemoryAllocStatsData.newBuilder()
                  .setJavaAllocationCount(random.nextInt())
                  .setJavaFreeCount(random.nextInt()))
                .setTimestamp(timestamp)
                .build())
    table.insertAllocStats(properties.session, stats)
  }
}
