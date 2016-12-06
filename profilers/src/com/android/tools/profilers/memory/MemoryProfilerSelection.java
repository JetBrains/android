/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryProfilerSelection {
  @NotNull private final MemoryProfilerStage myStage;

  @Nullable private CaptureObject myCaptureObject;
  @Nullable private HeapObject myHeapObject;
  @Nullable private ClassObject myClassObject;
  @Nullable private InstanceObject myInstanceObject;

  public MemoryProfilerSelection(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
  }

  @Nullable
  public CaptureObject getCaptureObject() {
    return myCaptureObject;
  }

  @Nullable
  public HeapObject getHeapObject() {
    return myHeapObject;
  }

  @Nullable
  public ClassObject getClassObject() {
    return myClassObject;
  }

  @Nullable
  public InstanceObject getInstanceObject() {
    return myInstanceObject;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean setCaptureObject(@Nullable CaptureObject captureObject) {
    if (myCaptureObject == captureObject || (myCaptureObject != null && myCaptureObject.equals(captureObject))) {
      return false;
    }

    if (myCaptureObject != null) {
      Disposer.dispose(myCaptureObject);
    }
    setHeapObject(null);
    myCaptureObject = captureObject;
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE);
    return true;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean setHeapObject(@Nullable HeapObject heapObject) {
    if (myHeapObject == heapObject) {
      return false;
    }
    setClassObject(null);
    myHeapObject = heapObject;
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP);
    return true;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean setClassObject(@Nullable ClassObject classObject) {
    if (myClassObject == classObject) {
      return false;
    }
    setInstanceObject(null);
    myClassObject = classObject;
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_CLASS);
    return true;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean setInstanceObject(@Nullable InstanceObject instanceObject) {
    if (myInstanceObject == instanceObject) {
      return false;
    }
    myInstanceObject = instanceObject;
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_INSTANCE);
    return true;
  }
}
