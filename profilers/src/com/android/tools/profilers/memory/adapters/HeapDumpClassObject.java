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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A UI representation of a {@link ClassObj}.
 */
final class HeapDumpClassObject extends ClassObject {
  @NotNull private final ClassObj myClassObj;
  @NotNull private final HeapDumpHeapObject myHeapObject;
  private long myRetainedSize;

  @Nullable
  private List<InstanceObject> myInstanceObjects = null;

  public HeapDumpClassObject(@NotNull HeapDumpHeapObject heapObject, @NotNull ClassObj classObj) {
    super(classObj.getClassName());
    myHeapObject = heapObject;
    myClassObj = classObj;
    for (Instance instance : myClassObj.getHeapInstances(myHeapObject.getHeap().getId())) {
      myRetainedSize += instance.getTotalRetainedSize();
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpClassObject)) {
      return false;
    }

    HeapDumpClassObject otherClass = (HeapDumpClassObject)obj;
    return myClassObj == otherClass.myClassObj;
  }

  @Override
  public int hashCode() {
    return myClassObj.hashCode();
  }

  @NotNull
  @Override
  public HeapObject getHeapObject() {
    return myHeapObject;
  }

  @Override
  public int getTotalCount() {
    return myClassObj.getInstanceCount();
  }

  @Override
  public int getHeapCount() {
    return myClassObj.getHeapInstancesCount(myHeapObject.getHeap().getId());
  }

  @Override
  public int getInstanceSize() {
    return myClassObj.getInstanceSize();
  }

  @Override
  public int getShallowSize() {
    return myClassObj.getShallowSize(myHeapObject.getHeap().getId());
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @NotNull
  @Override
  public List<InstanceObject> getInstances() {
    // One liner to prevent having to declare a final variable just so the closure can use it.
    ValueType type = JAVA_LANG_STRING.equals(getName()) ? ValueType.STRING : (JAVA_LANG_CLASS.equals(getName()) ? ValueType.CLASS : null);
    if (myInstanceObjects == null) {
      myInstanceObjects =
        myClassObj.getHeapInstances(myHeapObject.getHeap().getId()).stream()
          .map(instance -> new HeapDumpInstanceObject(this, instance, type))
          .collect(Collectors.toList());
    }
    return myInstanceObjects;
  }

  @NotNull
  @Override
  public List<InstanceObject.InstanceAttribute> getInstanceAttributes() {
    return Arrays
      .asList(InstanceObject.InstanceAttribute.LABEL, InstanceObject.InstanceAttribute.DEPTH, InstanceObject.InstanceAttribute.SHALLOW_SIZE,
              InstanceObject.InstanceAttribute.RETAINED_SIZE);
  }
}
