/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.views;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public class SelectionModel implements Disposable {
  public interface SelectionListener extends EventListener {
    void onHeapChanged(@NotNull Heap heap);

    void onClassObjChanged(@Nullable ClassObj classObj);

    void onInstanceChanged(@Nullable Instance instance);
  }

  private final EventDispatcher<SelectionListener> myDispatcher = EventDispatcher.create(SelectionListener.class);
  private Heap myHeap;
  @Nullable private ClassObj myClassObj;
  @Nullable private Instance myInstance;
  private boolean mySelectionLocked;

  public SelectionModel(@NotNull Heap heap) {
    myHeap = heap;
  }

  @Override
  public void dispose() {
    myHeap = null;
    myClassObj = null;
    myInstance = null;
  }

  @NotNull
  public Heap getHeap() {
    return myHeap;
  }

  public void setHeap(@NotNull Heap heap) {
    if (myHeap != heap && !mySelectionLocked) {
      myHeap = heap;
      myDispatcher.getMulticaster().onHeapChanged(myHeap);
    }
  }

  @Nullable
  public ClassObj getClassObj() {
    return myClassObj;
  }

  public void setClassObj(@Nullable ClassObj classObj) {
    if (myClassObj != classObj && !mySelectionLocked) {
      myClassObj = classObj;
      myDispatcher.getMulticaster().onClassObjChanged(myClassObj);
    }
  }

  @Nullable
  public Instance getInstance() {
    return myInstance;
  }

  public void setInstance(@Nullable Instance instance) {
    if (myInstance != instance && !mySelectionLocked) {
      myInstance = instance;
      myDispatcher.getMulticaster().onInstanceChanged(myInstance);
    }
  }

  public void setSelectionLocked(boolean locked) {
    mySelectionLocked = locked;
  }

  public void addListener(@NotNull SelectionListener listener) {
    myDispatcher.addListener(listener);
    listener.onHeapChanged(myHeap);
    listener.onClassObjChanged(myClassObj);
    listener.onInstanceChanged(myInstance);
  }

  public void removeListener(@NotNull SelectionListener listener) {
    myDispatcher.removeListener(listener);
  }
}
