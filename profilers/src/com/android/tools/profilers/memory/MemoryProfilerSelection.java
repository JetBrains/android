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

import com.android.tools.profilers.memory.adapters.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MemoryProfilerSelection {
  @NotNull private final MemoryProfilerStage myStage;

  @Nullable private CaptureEntry myCaptureEntry;
  @Nullable private CaptureObject myCaptureObject;
  @Nullable private HeapSet myHeapSet;
  @Nullable private ClassSet myClassSet;
  @Nullable private InstanceObject myInstanceObject;
  @NotNull private List<FieldObject> myFieldObjectPath = Collections.emptyList();

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

  @NotNull
  public List<FieldObject> getFieldObjectPath() {
    return myFieldObjectPath;
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  public boolean selectCaptureEntry(@Nullable CaptureEntry<? extends CaptureObject> captureEntry) {
    if (Objects.equals(myCaptureEntry, captureEntry)) {
      return false;
    }

    setFieldObjectPath(Collections.emptyList());
    setInstanceObject(null);
    setClassSet(null);
    setHeapSet(null);
    if (myCaptureObject != null) {
      myCaptureObject.unload();
    }
    myCaptureEntry = captureEntry;
    myCaptureObject = myCaptureEntry == null ? null : captureEntry.getCaptureObject();
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

    setFieldObjectPath(Collections.emptyList());
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

    setFieldObjectPath(Collections.emptyList());
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

    setFieldObjectPath(Collections.emptyList());
    setInstanceObject(instanceObject);
    return true;
  }

  public boolean selectFieldObjectPath(@NotNull List<FieldObject> fieldObjectPath) {
    assert fieldObjectPath.isEmpty() || (myCaptureObject != null && myInstanceObject != null);
    if (Objects.equals(myFieldObjectPath, fieldObjectPath)) {
      return false;
    }

    setFieldObjectPath(fieldObjectPath);
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

  public void setFieldObjectPath(@NotNull List<FieldObject> fieldObjectPath) {
    if (myFieldObjectPath != fieldObjectPath) {
      myFieldObjectPath = fieldObjectPath;
      myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_FIELD_PATH);
    }
  }
}
