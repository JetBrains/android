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
package com.android.tools.profilers.memory.adapters.classifiers;

import com.android.tools.profilers.memory.MemoryProfilerConfiguration;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;

public class NativeMemoryHeapSet extends HeapSet {
  private static final int DEFAULT_HEAP_ID = 0;

  public NativeMemoryHeapSet(@NotNull CaptureObject captureObject) {
    super(captureObject, CaptureObject.NATIVE_HEAP_NAME, DEFAULT_HEAP_ID);
    setClassGrouping(MemoryProfilerConfiguration.ClassGrouping.NATIVE_ARRANGE_BY_ALLOCATION_METHOD);
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    switch (myClassGrouping) {
      case NATIVE_ARRANGE_BY_ALLOCATION_METHOD:
        return NativeAllocationMethodSet.createDefaultClassifier();
      case NATIVE_ARRANGE_BY_CALLSTACK:
        return NativeCallStackSet.createDefaultClassifier();
      default:
        throw new RuntimeException("Classifier type not implemented: " + myClassGrouping);
    }
  }
}
