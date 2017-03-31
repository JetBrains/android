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
import com.android.tools.profilers.memory.adapters.ClassSet;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemoryProfilerSelection {
  @NotNull private final MemoryProfilerStage myStage;

  @Nullable private CaptureObject myCaptureObject;
  @Nullable private HeapSet myHeapSet;
  @Nullable private ClassSet myClassSet;
  @Nullable private InstanceObject myInstanceObject;

  public MemoryProfilerSelection(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
  }

  @Nullable
  public CaptureObject getCaptureObject() {
    return myCaptureObject;
  }

  @Nullable
  public HeapSet getHeapSet() {
    return myHeapSet;
  }

  @Nullable
  public ClassSet getClassSet() {
    return myClassSet;
  }

  @Nullable
  public InstanceObject getInstanceObject() {
    return myInstanceObject;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean selectCaptureObject(@Nullable CaptureObject captureObject) {
    if (myCaptureObject == captureObject) {
      return false;
    }

    selectInstanceObject(null);
    selectClassSet(null);
    selectHeapSet(null);
    myCaptureObject = captureObject;
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE);
    return true;
  }

  /**
   * @return true if selection was committed successfully
   */
  public boolean finishSelectingCaptureObject(@Nullable CaptureObject captureObject) {
    if (captureObject != null && captureObject == myCaptureObject && !captureObject.isError() && captureObject.isDoneLoading()) {
      myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE);
      return true;
    }
    return false;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean selectHeapSet(@Nullable HeapSet heapSet) {
    assert heapSet == null || myCaptureObject != null;
    if (myHeapSet == heapSet) {
      return false;
    }

    setInstanceObject(null);
    setClassSet(null);
    setHeapSet(heapSet);
    return true;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean selectClassSet(@Nullable ClassSet classSet) {
    assert classSet == null || myCaptureObject != null;
    if (myClassSet == classSet) {
      return false;
    }

    setInstanceObject(null);
    setClassSet(classSet);
    return true;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean selectInstanceObject(@Nullable InstanceObject instanceObject) {
    assert instanceObject == null || myCaptureObject != null;
    if (myInstanceObject == instanceObject) {
      return false;
    }

    setInstanceObject(instanceObject);
    return true;
  }

  private void setHeapSet(@Nullable HeapSet heapSet) {
    if (myHeapSet != heapSet) {
      myHeapSet = heapSet;
      myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP);
    }
  }

  private void setClassSet(@Nullable ClassSet classSet) {
    if (myClassSet != classSet) {
      myClassSet = classSet;
      myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_CLASS);
    }
  }

  private void setInstanceObject(@Nullable InstanceObject instanceObject) {
    if (myInstanceObject != instanceObject) {
      myInstanceObject = instanceObject;
      myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_INSTANCE);
    }
  }
}
