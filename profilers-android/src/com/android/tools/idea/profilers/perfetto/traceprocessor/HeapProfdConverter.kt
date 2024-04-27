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

import com.android.tools.profiler.perfetto.proto.Memory.NativeAllocationContext
import com.android.tools.profiler.perfetto.proto.Memory.StackFrame
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.NativeAllocationInstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import java.util.Base64

/**
 * Helper class to convert from perfetto memory proto to profiler protos.
 * The {@link NativeMemoryHeapSet} passed into the constructor is populated by calling {@link populateHeapSet}.
 */
class HeapProfdConverter(private val memorySet: NativeMemoryHeapSet, private val demangler: NameDemangler) {

  companion object {
    private val UNKNOWN_FRAME = Memory.AllocationStack.StackFrame.newBuilder().setMethodName("unknown").build()
  }

  /**
   * Given a {@link Memory.StackFrame} this method converts it to a StackFrameInfo using the provided name.
   * When we have a symbolized frame we return a frame with a method name in the form of
   * Symbol (File:Line) eg.. operator new (new.cpp:256)
   * The file name and line number are also populated if available.
   */
  private fun toStackFrameInfo(rawFrame: StackFrame): StackFrameInfo {
    val base64 = Base64.getDecoder()

    val module = base64.decode(rawFrame.module).toString(Charsets.UTF_8)
    val file = if (rawFrame.lineNumber > 0) base64.decode(rawFrame.sourceFile).toString(Charsets.UTF_8) else ""
    val name = base64.decode(rawFrame.name).toString(Charsets.UTF_8)

    // If there is a file name (source file), then we will have a line number.
    val fullName = if (file.isNotEmpty()) {
      "$name ($file:${rawFrame.lineNumber})"
    } else {
      name
    }

    return StackFrameInfo(name = fullName, fileName = file, lineNumber = rawFrame.lineNumber, moduleName = module)
  }

  /**
   * Given a context all values will be enumerated and added to the {@link NativeMemoryHeapSet}. If the context has an allocation with
   * a count > 0 it will be added as an allocation. If the count is <= 0 it will be added as a free.
   */
  fun populateHeapSet(context: NativeAllocationContext) {
    val frameIdToFrame: MutableMap<Long, MutableList<StackFrameInfo>> = HashMap()
    val frames: MutableMap<Long, List<Memory.AllocationStack.StackFrame>> = HashMap()
    val classDb = ClassDb()

    context.framesList.forEach {
      frameIdToFrame.getOrPut(it.id) { ArrayList() }.add(toStackFrameInfo(it))
    }
    // Demangle in place is significantly faster than passing in names 1 by 1
    demangler.demangleInplace(frameIdToFrame.values.flatten())
    // On windows the llvm-symbolizer holds a file lock on the last file loaded. This can be a pain if you want to rebuild / redeploy the
    // app after a single line change. Stopping the symbolizer kills the llvm-symbolizer process.
    // Reduce duplication of UI StackFrame elements, by doing a one time conversion between StackFrameInfo objects and StackFrame protos
    val it = frameIdToFrame.iterator()
    while(it.hasNext()) {
      val next = it.next()
      // Inlined functions can map to the same key, for more info see
      // https://perfetto.dev/docs/analysis/sql-tables#stack_profile_symbol
      frames[next.key] = next.value.map {
        Memory.AllocationStack.StackFrame.newBuilder()
          .setModuleName(it.moduleName)
          .setMethodName(it.name)
          .setFileName(it.fileName)
          .setLineNumber(it.lineNumber)
          .build()
      }
      it.remove() //Remove to reduce temp space required.
    }
    val pointerMap = context.pointersMap
    context.allocationsList.forEach { allocation ->
      // Some callstacks are recursive. Instead of having a fixed callstack length we track what site ids we have visited.
      val visitedCallSiteIds = mutableSetOf<Long>()

      // Build allocation stack proto
      val fullStack = Memory.AllocationStack.StackFrameWrapper.newBuilder()
      var callSiteId = pointerMap[allocation.stackId]?.parentId ?: -1
      while (callSiteId > 0L && !visitedCallSiteIds.contains(callSiteId)) {
        visitedCallSiteIds.add(callSiteId)
        val frame = pointerMap[callSiteId]?.let { frames[it.frameId] } ?: emptyList()
        fullStack.addAllFrames(frame)
        callSiteId = pointerMap[callSiteId]?.parentId ?: -1
      }
      // Found a recursive callstack
      if (callSiteId > 0L) {
        val frameName = pointerMap[callSiteId]?.let { frames[it.frameId]?.last()?.methodName } ?: UNKNOWN_FRAME.methodName
        fullStack.addFrames(0, Memory.AllocationStack.StackFrame.newBuilder()
          .setMethodName("[Recursive] $frameName"))
      }

      val stack = Memory.AllocationStack.newBuilder()
        .setFullStack(fullStack)
        .build()

      // Build allocation event proto
      val event = Memory.AllocationEvent.Allocation.newBuilder()
        .setSize(Math.abs(allocation.size))
        .build()

      // Build allocation instance object
      val allocationMethod = pointerMap[allocation.stackId]?.let { frames[it.frameId]?.last() } ?: UNKNOWN_FRAME
      val instanceObject = NativeAllocationInstanceObject(
        event, classDb.registerClass(0, 0, allocationMethod.methodName), stack, allocation.count)

      // Add it to the heapset bookkeeping.
      if (allocation.count > 0) {
        memorySet.addDeltaInstanceObject(instanceObject)
      }
      else {
        memorySet.freeDeltaInstanceObject(instanceObject)
      }
    }
  }
}

data class StackFrameInfo(override var name: String,
                          val fileName: String = "",
                          val lineNumber: Int = 0,
                          val moduleName: String = "") : NameHolder