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

import com.android.tools.profilers.memory.MemoryProfilerConfiguration
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeMemoryHeapSetTest {

  @Test
  fun defaults() {
    val heapSet = NativeMemoryHeapSet(
      FakeCaptureObject.Builder().build())
    assertThat(heapSet.name).isEqualTo("Native")
    assertThat(heapSet.id).isEqualTo(0)
  }
  @Test
  fun heapSetClassifiersLimitedToNativeSets() {
    val heapSet = NativeMemoryHeapSet(
      FakeCaptureObject.Builder().build())
    val defaultClassifier = heapSet.createSubClassifier()
    assertThat(defaultClassifier).isInstanceOf(NativeAllocationMethodSet.createDefaultClassifier().javaClass)
    heapSet.setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.NATIVE_ARRANGE_BY_CALLSTACK)
    val callstackClassifier = heapSet.createSubClassifier()
    assertThat(callstackClassifier).isInstanceOf(NativeCallStackSet.createDefaultClassifier().javaClass)
  }
}