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
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.classifiers.NativeMemoryHeapSet
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.google.common.collect.Maps.newHashMap
import com.google.common.truth.Truth.assertThat
import com.intellij.util.Base64
import org.junit.Before
import org.junit.Test
import kotlin.streams.toList

class HeapProfdConverterTest {

  private lateinit var context: Memory.NativeAllocationContext.Builder

  @Before
  fun buildBasicContext() {
    context = Memory.NativeAllocationContext.newBuilder()
      .addFrames(Memory.StackFrame.newBuilder()
                   .setId(1)
                   .setName(Base64.encode("Frame 1".toByteArray()))
                   .setModule(Base64.encode("/data/local/fakeModule%%".toByteArray())))
      .addFrames(Memory.StackFrame.newBuilder()
                   .setId(2)
                   .setName(Base64.encode("Frame 1A".toByteArray()))
                   .setModule(Base64.encode("TestModule".toByteArray()))
                   .setRelPc(1234))
      .addFrames(Memory.StackFrame.newBuilder()
                   .setId(3)
                   .setName(Base64.encode("Frame 2".toByteArray()))
                   .setModule(Base64.encode("TestModule".toByteArray()))
                   .setRelPc(1234))
      .putPointers(1, Memory.StackPointer.newBuilder()
        .setFrameId(1)
        .build())
      .putPointers(2, Memory.StackPointer.newBuilder()
        .setFrameId(2)
        .setParentId(1)
        .build())
      .putPointers(3, Memory.StackPointer.newBuilder()
        .setFrameId(4)
        .build())
      .putPointers(4, Memory.StackPointer.newBuilder()
        .setFrameId(3)
        .setParentId(4)
        .build())
      .addAllocations(Memory.Allocation.newBuilder()
                        .setTimestamp(1)
                        .setCount(5)
                        .setSize(20)
                        .setStackId(2))
      .addAllocations(Memory.Allocation.newBuilder()
                        .setTimestamp(1)
                        .setCount(-4)
                        .setSize(-16)
                        .setStackId(1))
      .addAllocations(Memory.Allocation.newBuilder()
                        .setTimestamp(1)
                        .setCount(2)
                        .setSize(16)
                        .setStackId(3))
  }

  @Test
  fun recursiveCallstacksHandled() {
    context.addAllocations(Memory.Allocation.newBuilder()
                             .setTimestamp(1)
                             .setCount(2)
                             .setSize(16)
                             .setStackId(4))
    val nativeHeapSet = NativeMemoryHeapSet(FakeCaptureObject.Builder().build())
    val symbolizer = FakeFrameSymbolizer()
    symbolizer.addValidSymbol("TestModule", "ValidSymbol")
    val heapProfdConverter = HeapProfdConverter("", symbolizer, nativeHeapSet, FakeNameDemangler())
    heapProfdConverter.populateHeapSet(context.build())
    val instances = nativeHeapSet.instancesStream.toList()
    // Frame 1 -> Frame1A ( Frame1 has a valid symbol as such formats the name
    assertThat(instances[3].name).isEqualTo("ValidSymbol (ValidSymbol:123)")
    assertThat(instances[3].callStackDepth).isEqualTo(2)
    assertThat(instances[3].allocationCallStack).isNotNull()
    assertThat(instances[3].allocationCallStack!!.fullStack.getFrames(0).methodName).isEqualTo("[Recursive] ValidSymbol (ValidSymbol:123)")
  }

  @Test
  fun heapSetConversion() {
    val nativeHeapSet = NativeMemoryHeapSet(FakeCaptureObject.Builder().build())
    val symbolizer = FakeFrameSymbolizer()
    symbolizer.addValidSymbol("TestModule", "ValidSymbol")
    val heapProfdConverter = HeapProfdConverter("", symbolizer, nativeHeapSet, FakeNameDemangler())
    heapProfdConverter.populateHeapSet(context.build())
    assertThat(nativeHeapSet.deltaAllocationCount).isEqualTo(7)
    assertThat(nativeHeapSet.deltaDeallocationCount).isEqualTo(4)
    assertThat(nativeHeapSet.allocationSize).isEqualTo(36)
    assertThat(nativeHeapSet.deallocationSize).isEqualTo(16)
    assertThat(nativeHeapSet.totalRemainingSize).isEqualTo(20)
    assertThat(nativeHeapSet.instancesCount).isEqualTo(3)
    val instances = nativeHeapSet.instancesStream.toList()
    // Frame 1 -> Frame1A ( Frame1 has a valid symbol as such formats the name
    assertThat(instances[0].name).isEqualTo("ValidSymbol (ValidSymbol:123)")
    assertThat(instances[0].callStackDepth).isEqualTo(1)
    assertThat(instances[0].allocationCallStack).isNotNull()
    assertThat(instances[0].allocationCallStack!!.fullStack.getFrames(0).methodName).isEqualTo("Frame 1")
    assertThat(instances[0].allocationCallStack!!.fullStack.getFrames(0).moduleName).isEqualTo("/data/local/fakeModule%%")
    // Frame 1
    assertThat(instances[1].name).isEqualTo("Frame 1")
    // Invalid link returns unknown
    assertThat(instances[2].name).isEqualTo("unknown")
    assertThat(symbolizer.stopCalled).isTrue()
  }

  class FakeFrameSymbolizer : NativeFrameSymbolizer {
    private val symbols = newHashMap<String, String>()
    var stopCalled = false
    fun addValidSymbol(symbol: String, path: String) {
      symbols[symbol] = path
    }

    override fun symbolize(abi: String?,
                           unsymbolizedFrame: com.android.tools.profiler.proto.Memory.NativeCallStack.NativeFrame?):
      com.android.tools.profiler.proto.Memory.NativeCallStack.NativeFrame {
      // Lookup symbol else return as if the symbolizer failed.
      val symbolName = symbols[unsymbolizedFrame!!.moduleName] ?: "0x00"
      return unsymbolizedFrame.toBuilder().setSymbolName(symbolName).setFileName(symbolName).setLineNumber(123).build()
    }

    override fun stop() {
      stopCalled = true
    }
  }

  class FakeNameDemangler : NameDemangler {
    override fun demangleInplace(stackFrames: Collection<NameHolder>) {
    }
  }
}