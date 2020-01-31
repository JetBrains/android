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
import com.android.tools.profiler.proto.Memory

import java.sql.Connection

class MemoryLiveAllocationGenerator(connection: Connection) : DataGenerator(connection) {

  private val myTable = MemoryLiveAllocationTable(FakeLogService())
  private val stackIds = mutableListOf<Int>()
  private val threadIds = mutableListOf<Int>()
  private val methodIds = mutableListOf<Long>()
  private var hasGeneratedInfo = false

  init {
    myTable.initialize(connection)
  }

  override fun generate(timestamp: Long, properties: GeneratorProperties) {
    if (!hasGeneratedInfo || isWithinProbability(.05)) {
      generateAllocationContexts(properties)
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
    val events = mutableListOf<Memory.AllocationEvent>()
    for (i in 0..eventCount) {
      events.add(Memory.AllocationEvent.newBuilder()
                   .setAllocData(Memory.AllocationEvent.Allocation.newBuilder()
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
    val sample = Memory.BatchAllocationEvents.newBuilder()
      .addAllEvents(events)
      .build()
    myTable.insertAllocationEvents(properties.session, sample)
  }

  private fun generateJniRefData(properties: GeneratorProperties) {
    val sample = Memory.BatchJNIGlobalRefEvent.newBuilder()
      .build()
    myTable.insertJniReferenceData(properties.session, sample)
  }

  private fun generateAllocationContexts(properties: GeneratorProperties) {
    val method = mutableListOf<Memory.AllocationStack.StackFrame>()
    val methodCount = random.nextInt(128)
    for (i in 0..methodCount) {
      methodIds.add(random.nextLong())
      method.add(Memory.AllocationStack.StackFrame.newBuilder()
                   .setClassName("Test")
                   .setFileName("SomeFile" + i)
                   .setLineNumber(random.nextInt())
                   .setMethodId(methodIds[i])
                   .setMethodName("Some Name" + i)
                   .build())
    }

    val stacks = mutableListOf<Memory.AllocationStack>()
    val stackCount = random.nextInt(128)
    val frameCount = random.nextInt(methodIds.size)
    for (i in 0..stackCount) {
      val frame = Memory.AllocationStack.EncodedFrameWrapper.newBuilder()
      for (j in 0..frameCount) {
        frame.addFrames(Memory.AllocationStack.EncodedFrame.newBuilder()
                          .setMethodId(methodIds[j])
                          .setLineNumber(random.nextInt())
                          .build())
      }
      stackIds.add(random.nextInt())
      stacks.add(Memory.AllocationStack.newBuilder().setEncodedStack(frame).build())
    }

    val info = mutableListOf<Memory.ThreadInfo>()
    val threads = random.nextInt(50)
    for (i in 0..threads) {
      threadIds.add(random.nextInt())
      info.add(Memory.ThreadInfo.newBuilder()
                 .setThreadId(threadIds[i])
                 .setThreadName("Some Name " + i)
                 .build())
    }

    val batchContexts = Memory.BatchAllocationContexts.newBuilder()
      .addAllMethods(method)
      .addAllEncodedStacks(stacks)
      .addAllThreadInfos(info)
      .build()
    myTable.insertAllocationContexts(properties.session, batchContexts)
  }
}
