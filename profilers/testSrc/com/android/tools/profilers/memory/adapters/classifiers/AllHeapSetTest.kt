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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.MemoryObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AllHeapSetTest {
  @Test
  fun `all-heap sums up separate heaps`() {
    val capture = FakeCaptureObject.Builder().build()
    val heap1 = HeapSet(capture, "heap1", 1)
    val heap2 = HeapSet(capture, "heap2", 2)
    val allHeap = AllHeapSet(capture, arrayOf(heap1, heap2)).also { it.clearClassifierSets() }

    val insts1 = arrayOf(FakeInstanceObject.Builder(capture, 1, "obj").setHeapId(1).setShallowSize(4).build(),
                         FakeInstanceObject.Builder(capture, 2, "int").setHeapId(1).setShallowSize(8).build(),
                         FakeInstanceObject.Builder(capture, 3, "str").setHeapId(1).setShallowSize(14).build())
    val insts2 = arrayOf(FakeInstanceObject.Builder(capture, 4, "cat").setHeapId(2).setShallowSize(3).build(),
                         FakeInstanceObject.Builder(capture, 5, "dog").setHeapId(2).setShallowSize(5).build(),
                         FakeInstanceObject.Builder(capture, 6, "rat").setHeapId(2).setShallowSize(7).build())
    val insts = insts1 + insts2

    insts.forEach { allHeap.addDeltaInstanceObject(it) }

    fun assertHeapSumsUp(heapProp: (HeapSet) -> Long, instProp: (InstanceObject) -> Long) {
      fun <X> Array<X>.sumByL(f: (X) -> Long) = fold(0L){ s,x ->  s + f(x)}
      assertThat(heapProp(allHeap)).isEqualTo(heapProp(heap1) + heapProp(heap2))
      assertThat(heapProp(allHeap)).isEqualTo(insts.sumByL(instProp))
      assertThat(heapProp(heap1)).isEqualTo(insts1.sumByL(instProp))
      assertThat(heapProp(heap2)).isEqualTo(insts2.sumByL(instProp))
    }

    fun Long.validOrZero() = if (this == MemoryObject.INVALID_VALUE.toLong()) 0L else this

    assertHeapSumsUp({it.deltaAllocationCount.toLong()}, {1})
    assertHeapSumsUp({it.totalShallowSize}, {it.shallowSize.toLong().validOrZero()})
    assertHeapSumsUp({it.totalNativeSize}, {it.nativeSize.validOrZero()})
    assertHeapSumsUp({it.totalRetainedSize}, {it.retainedSize.validOrZero()})
  }
}