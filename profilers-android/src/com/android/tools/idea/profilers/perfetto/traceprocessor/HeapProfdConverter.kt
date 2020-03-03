/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.tools.profiler.perfetto.proto.Memory
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.NativeAllocationInstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import java.util.HashMap

/**
 * Helper class to convert from perfetto memory proto to profiler protos.
 * The {@link NativeMemoryHeapSet} passed into the constructor is populated by calling {@link populateHeapSet}.
 */
class HeapProfdConverter(private val memorySet: NativeMemoryHeapSet) {

  /**
   * Function to convert empty or null string to unknown. Heapprofd can sometimes return callstacks that do not have a frame name.
   * This also mirrors behavior with Perfetto ui.
   */
  private fun toNameOrUnknown(name: String?): String {
    if (name.isNullOrBlank()) {
      return "unknown"
    }
    return name
  }

  /**
   * Given a context all values will be enumerated and added to the {@link NativeMemoryHeapSet}. If the context has an allocation with
   * a count > 0 it will be added as an allocation. If the count is <= 0 it will be added as a free.
   */
  fun populateHeapSet(context: Memory.NativeAllocationContext) {
    val frameIdToName: MutableMap<Long, String> = HashMap()
    val stackPointerIdToParentId: MutableMap<Long, Long> = HashMap()
    val stackPointerIdToFrameName: MutableMap<Long, String> = HashMap()
    val callSites: Map<Long, NativeAllocationInstanceObject?> = HashMap()
    val classDb = ClassDb()
    context.framesList.forEach {
      frameIdToName[it.id] = it.name
    }
    context.pointersList.forEach {
      stackPointerIdToFrameName[it.id] = toNameOrUnknown(frameIdToName[it.frameId])
      stackPointerIdToParentId[it.id] = it.parentId
    }
    context.allocationsList.forEach {
      val fullStack = com.android.tools.profiler.proto.Memory.AllocationStack.StackFrameWrapper.newBuilder()
      var callSiteId = stackPointerIdToParentId[it.stackId]
      if (!callSites.containsKey(it.stackId)) {
        while (callSiteId != null && callSiteId != 0L) {
          val name = toNameOrUnknown(stackPointerIdToFrameName[callSiteId])
          fullStack.addFrames(com.android.tools.profiler.proto.Memory.AllocationStack.StackFrame.newBuilder().setMethodName(name).build())
          callSiteId = stackPointerIdToParentId[callSiteId]
        }
        val event = com.android.tools.profiler.proto.Memory.AllocationEvent.Allocation.newBuilder()
          .setSize(Math.abs(it.size))
          .build()
        val name = toNameOrUnknown(stackPointerIdToFrameName[it.stackId])
        val stack = com.android.tools.profiler.proto.Memory.AllocationStack.newBuilder()
          .setFullStack(fullStack)
          .build()
        val instanceObject = NativeAllocationInstanceObject(
          event, classDb.registerClass(0, 0, name), stack, it.count)
        if (it.count > 0) {
          memorySet.addDeltaInstanceObject(instanceObject)
        }
        else {
          memorySet.freeDeltaInstanceObject(instanceObject)
        }
      }
    }
  }
}