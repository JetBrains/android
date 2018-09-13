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

import com.android.tools.datastore.FakeLogService
import com.android.tools.datastore.database.MemoryLiveAllocationTable
import com.android.tools.profiler.proto.MemoryProfiler

import java.sql.Connection

class MemoryLiveAllocationGenerator(connection: Connection) : DataGenerator(connection) {

  private val myTable =  MemoryLiveAllocationTable(FakeLogService())
  private val stackIds = mutableListOf<Int>()
  private val threadIds = mutableListOf<Int>()
  private val methodIds = mutableListOf<Long>()
  private var hasGeneratedInfo = false

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    if (!hasGeneratedInfo || isWithinProbability(.05)) {
      generateMethodInfo(properties)
      generateStackInfo(timestamp, properties)
      generateThreadInfo(timestamp, properties)
      hasGeneratedInfo = true
    }
    // Generate new allocations roughy a third of the time
    if (isWithinProbability(.5)) {
      // Uses data generated from the thread / stack info.
      generateAllocationData(properties)
      generateJniRefData(properties)
    }
  }

  private fun generateAllocationData(properties: GeneratorProperties) {
    val eventCount = random.nextInt(100)
    val events = mutableListOf<MemoryProfiler.AllocationEvent>()
    for(i in 0..eventCount) {
      events.add(MemoryProfiler.AllocationEvent.newBuilder()
                   .setAllocData(MemoryProfiler.AllocationEvent.Allocation.newBuilder()
                                   .addLocationIds(1)
                                   .addMethodIds(2)
                                   .setClassTag(random.nextInt())
                                   .setHeapId(random.nextInt())
                                   .setLength(random.nextInt())
                                   .setSize(random.nextLong())
                                   .setStackId(stackIds[random.nextInt(stackIds.size)])
                                   .setTag(random.nextInt())
                                   .setThreadId(threadIds[random.nextInt(threadIds.size)]))
        .build())
    }
    val sample = MemoryProfiler.BatchAllocationSample.newBuilder()
      .addAllEvents(events)
      .build()
    myTable.insertAllocationData(properties.session, sample)
  }

  private fun generateJniRefData(properties: GeneratorProperties) {
    val sample = MemoryProfiler.BatchJNIGlobalRefEvent.newBuilder()
      .build()
    myTable.insertJniReferenceData(properties.session, sample)
  }

  private fun generateMethodInfo(properties: GeneratorProperties) {
    val method = mutableListOf<MemoryProfiler.AllocationStack.StackFrame>()
    val methodCount = random.nextInt(128)
    for(i in 0..methodCount) {
      methodIds.add(random.nextLong())
      method.add(MemoryProfiler.AllocationStack.StackFrame.newBuilder()
                   .setClassName("Test")
                   .setFileName("SomeFile" + i)
                   .setLineNumber(random.nextInt())
                   .setMethodId(methodIds[i])
                   .setMethodName("Some Name" + i)
                   .build())
    }
    myTable.insertMethodInfo(properties.session, method)
  }

  private fun generateStackInfo(timestamp: Long, properties: GeneratorProperties) {
    val stacks = mutableListOf<MemoryProfiler.EncodedAllocationStack>()
    val stackCount = random.nextInt(128)
    val methodCount = random.nextInt(methodIds.size)
    for(i in 0..stackCount) {
      stackIds.add(random.nextInt())
      stacks.add(MemoryProfiler.EncodedAllocationStack.newBuilder()
                   .setStackId(stackIds[i])
                   .setTimestamp(timestamp)
                   .addAllMethodIds(methodIds.subList(0, methodCount))
                   .addAllLineNumbers(random.ints(methodCount.toLong()).toArray().asIterable())
                   .build())
    }
    myTable.insertStackInfo(properties.session, stacks)
  }

  private fun generateThreadInfo(timestamp: Long, properties: GeneratorProperties) {
    val info = mutableListOf<MemoryProfiler.ThreadInfo>()
    val threads = random.nextInt(50)
    for(i in 0..threads) {
      threadIds.add(random.nextInt())
      info.add(MemoryProfiler.ThreadInfo.newBuilder()
                 .setThreadId(threadIds[i])
                 .setThreadName("Some Name " + i)
                 .setTimestamp(timestamp)
                 .build())
    }
    myTable.insertThreadInfo(properties.session, info)
  }
}
