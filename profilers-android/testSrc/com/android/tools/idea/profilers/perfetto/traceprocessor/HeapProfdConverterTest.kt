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
import com.google.common.truth.Truth.assertThat
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
                   .setName("Frame 1"))
      .addFrames(Memory.StackFrame.newBuilder()
                   .setId(2)
                   .setName("Frame 1A"))
      .addPointers(Memory.StackPointer.newBuilder()
                     .setId(1)
                     .setFrameId(1))
      .addPointers(Memory.StackPointer.newBuilder()
                     .setId(2)
                     .setFrameId(2)
                     .setParentId(1))
      .addPointers(Memory.StackPointer.newBuilder()
                     .setId(3)
                     .setFrameId(3))
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
  fun heapSetConversion() {
    val nativeHeapSet = NativeMemoryHeapSet(FakeCaptureObject.Builder().build())
    val heapProfdConverter = HeapProfdConverter(nativeHeapSet)
    heapProfdConverter.populateHeapSet(context.build())
    assertThat(nativeHeapSet.deltaAllocationCount).isEqualTo(7)
    assertThat(nativeHeapSet.deltaDeallocationCount).isEqualTo(4)
    assertThat(nativeHeapSet.allocationSize).isEqualTo(36)
    assertThat(nativeHeapSet.deallocationSize).isEqualTo(16)
    assertThat(nativeHeapSet.totalRemainingSize).isEqualTo(20)
    assertThat(nativeHeapSet.instancesCount).isEqualTo(3)
    val instances = nativeHeapSet.instancesStream.toList()
    // Frame 1 -> Frame1A
    assertThat(instances[0].name).isEqualTo("Frame 1A")
    assertThat(instances[0].callStackDepth).isEqualTo(2)
    assertThat(instances[0].allocationCallStack).isNotNull()
    assertThat(instances[0].allocationCallStack!!.fullStack.getFrames(0).methodName).isEqualTo("Frame 1")
    // Frame 1
    assertThat(instances[1].name).isEqualTo("Frame 1")
    // Invalid link returns unknown
    assertThat(instances[2].name).isEqualTo("unknown")
  }
}