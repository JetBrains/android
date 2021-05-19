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
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.intellij.util.Base64
import gnu.trove.TLongHashSet
import java.io.File
import java.util.HashMap

/**
 * Helper class to convert from perfetto memory proto to profiler protos.
 * The {@link NativeMemoryHeapSet} passed into the constructor is populated by calling {@link populateHeapSet}.
 */
class HeapProfdConverter(private val abi: String,
                         private val symbolizer: NativeFrameSymbolizer,
                         private val memorySet: NativeMemoryHeapSet,
                         private val demangler: NameDemangler) {

  companion object {
    private val UNKNOWN_FRAME = Memory.AllocationStack.StackFrame.newBuilder().setMethodName("unknown").build()
  }

  /**
   * Given a {@link Memory.StackFrame} from the trace processor we attempt to gather symbolized data. If we cannot get symbolized data
   * we return a frame with the original name if one was provided. If no name was found then we return {@link UNKNOWN_FRAME}
   * When we have a symbolized frame we return a frame with a method name in the form of
   * Symbol (File:Line) eg.. operator new (new.cpp:256)
   * The file name and line number are also populated if available.
   */
  private fun toBestAvailableStackFrame(rawFrame: StackFrame): StackFrameInfo {
    val module = String(Base64.decode(rawFrame.module))
    val symbolizedFrame = symbolizer.symbolize(abi, Memory.NativeCallStack.NativeFrame.newBuilder()
      .setModuleName(module)
      // +1 because the common symbolizer does -1 accounting for an offset heapprofd does not have.
      // see IntellijNativeFrameSymbolizer:getOffsetOfPreviousInstruction
      .setModuleOffset(rawFrame.relPc + 1)
      .build())
    val symbolName = symbolizedFrame.symbolName
    if (symbolName.startsWith("0x")) {
      val methodName = if (rawFrame.name.isNullOrBlank()) UNKNOWN_FRAME.methodName else String(Base64.decode(rawFrame.name))
      return StackFrameInfo(name = methodName, moduleName = module)
    }
    val file = File(symbolizedFrame.fileName).name
    val formattedName = "${symbolName} (${file}:${symbolizedFrame.lineNumber})"
    return StackFrameInfo(name = formattedName,
                          fileName = symbolizedFrame.fileName,
                          lineNumber = symbolizedFrame.lineNumber,
                          moduleName = symbolizedFrame.moduleName)
  }

  /**
   * Given a context all values will be enumerated and added to the {@link NativeMemoryHeapSet}. If the context has an allocation with
   * a count > 0 it will be added as an allocation. If the count is <= 0 it will be added as a free.
   */
  fun populateHeapSet(context: NativeAllocationContext) {
    val frameIdToFrame: MutableMap<Long, StackFrameInfo> = HashMap()
    val frames: MutableMap<Long, Memory.AllocationStack.StackFrame> = HashMap()
    val classDb = ClassDb()

    context.framesList.forEach {
      frameIdToFrame[it.id] = toBestAvailableStackFrame(it)
    }
    // Demangle in place is significantly faster than passing in names 1 by 1
    demangler.demangleInplace(frameIdToFrame.values)
    // On windows the llvm-symbolizer holds a file lock on the last file loaded. This can be a pain if you want to rebuild / redeploy the
    // app after a single line change. Stopping the symbolizer kills the llvm-symbolizer process.
    symbolizer.stop();
    // Reduce duplication of UI StackFrame elements, by doing a one time conversion between StackFrameInfo objects and StackFrame protos
    val it = frameIdToFrame.iterator()
    while(it.hasNext()) {
      val next = it.next()
      frames[next.key] = Memory.AllocationStack.StackFrame.newBuilder()
        .setModuleName(next.value.moduleName)
        .setMethodName(next.value.name)
        .setFileName(next.value.fileName)
        .setLineNumber(next.value.lineNumber)
        .build()
      it.remove() //Remove to reduce temp space required.
    }
    val pointerMap = context.pointersMap
    context.allocationsList.forEach { allocation ->
      // Some callstacks are recursive. Instead of having a fixed callstack length we track what site ids we have visited.
      val visitedCallSiteIds = TLongHashSet()
      // Build allocation stack proto
      val fullStack = Memory.AllocationStack.StackFrameWrapper.newBuilder()
      var callSiteId = pointerMap[allocation.stackId]?.parentId ?: -1
      while (callSiteId > 0L && !visitedCallSiteIds.contains(callSiteId)) {
        visitedCallSiteIds.add(callSiteId)
        val frame = pointerMap[callSiteId]?.let { frames[it.frameId] } ?: UNKNOWN_FRAME
        fullStack.addFrames(frame)
        callSiteId = pointerMap[callSiteId]?.parentId ?: -1
      }
      // Found a recursive callstack
      if (callSiteId > 0L) {
        val frameName = pointerMap[callSiteId]?.let { frames[it.frameId]?.methodName } ?: UNKNOWN_FRAME.methodName
        fullStack.addFrames(0, Memory.AllocationStack.StackFrame.newBuilder()
          .setMethodName("[Recursive] " + frameName))
      }

      val stack = Memory.AllocationStack.newBuilder()
        .setFullStack(fullStack)
        .build()

      // Build allocation event proto
      val event = Memory.AllocationEvent.Allocation.newBuilder()
        .setSize(Math.abs(allocation.size))
        .build()

      // Build allocation instance object
      val allocationMethod = pointerMap[allocation.stackId]?.let { frames[it.frameId] } ?: UNKNOWN_FRAME
      val instanceObject = NativeAllocationInstanceObject(
        event, classDb.registerClass(0, 0, allocationMethod.methodName), stack, allocation.count)

      // Add it to the heapset book keeping.
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