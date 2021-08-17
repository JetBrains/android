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
package com.android.tools.profilers.memory

import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.google.common.truth.Truth
import java.util.function.Supplier

class MemoryCaptureObjectTestUtils {
  companion object {
    private const val CLASS_NAME_0 = "com.android.studio.Foo"
    private const val CLASS_NAME_1 = "com.google.Bar"
    private const val CLASS_NAME_2 = "com.android.studio.Baz"

    fun createAndSelectHeapSet(stage: MainMemoryProfilerStage): HeapSet {
      val captureObject = FakeCaptureObject.Builder().build()
      val instanceObjects: Set<InstanceObject> = setOf(
        FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo0").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(300).build(),
        FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setDepth(
        2).setShallowSize(200)
        .setRetainedSize(300).build(),
      FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setDepth(
        3).setShallowSize(200)
        .setRetainedSize(300).build(),
        FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar0").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(400).build(),
        FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz0").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(500).build(),
        FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz1").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(500).build(),
        FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz2").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(500).build(),
        FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz3").setDepth(
        1).setShallowSize(200)
        .setRetainedSize(500).build())
      captureObject.addInstanceObjects(instanceObjects)
      stage
        .selectCaptureDuration(CaptureDurationData(1, false, false, CaptureEntry(Any(),
                                                                                 Supplier<CaptureObject> { captureObject })),
                               null)
      Truth.assertThat(captureObject.containsClass(0)).isTrue()
      Truth.assertThat(captureObject.containsClass(1)).isTrue()
      Truth.assertThat(captureObject.containsClass(2)).isTrue()
      return captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID)!!
    }
  }
}