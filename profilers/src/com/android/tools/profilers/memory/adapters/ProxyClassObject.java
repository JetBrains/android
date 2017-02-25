/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ProxyClassObject extends ClassObject {
  @NotNull private final ClassObject myClassObject;
  @NotNull private final List<InstanceObject> myInstanceObjects = new ArrayList<>();

  private int myTotalCount;
  private int myHeapCount;
  private long myRetainedSize;
  private boolean myHasStackInfo;

  public ProxyClassObject(@NotNull ClassObject classObject, @NotNull InstanceObject instanceObject) {
    super(classObject.getName());
    myClassObject = classObject;
    myInstanceObjects.add(instanceObject);
    myTotalCount = 1;
    myHeapCount = 1;
    myRetainedSize = instanceObject.getRetainedSize();
    myHasStackInfo = instanceObject.getCallStack() != null;
  }

  @NotNull
  public ClassObject getClassObject() {
    return myClassObject;
  }

  @Override
  public boolean isInNamespace(@NotNull NamespaceObject target) {
    return target instanceof ProxyClassObject && ((ProxyClassObject)target).myClassObject.equals(myClassObject);
  }

  @Override
  public int getTotalCount() {
    return myTotalCount;
  }

  @Override
  public int getHeapCount() {
    return myHeapCount;
  }

  @Override
  public long getRetainedSize() {
    return myRetainedSize;
  }

  @Override
  public int getInstanceSize() {
    return myClassObject.getInstanceSize();
  }

  @Override
  public int getShallowSize() {
    return myClassObject.getShallowSize();
  }

  @NotNull
  @Override
  public HeapObject getHeapObject() {
    return myClassObject.getHeapObject();
  }

  @NotNull
  @Override
  public List<InstanceObject.InstanceAttribute> getInstanceAttributes() {
    return myClassObject.getInstanceAttributes();
  }

  @NotNull
  @Override
  public List<InstanceObject> getInstances() {
    return myInstanceObjects;
  }

  @Override
  public void accumulateInstanceObject(@NotNull InstanceObject instanceObject) {
    myInstanceObjects.add(instanceObject);
    myHeapCount += 1;
    myTotalCount += 1;
    myRetainedSize += Math.max(instanceObject.getRetainedSize(), 0);
    myHasStackInfo |= instanceObject.getCallStack() != null;
  }
}
