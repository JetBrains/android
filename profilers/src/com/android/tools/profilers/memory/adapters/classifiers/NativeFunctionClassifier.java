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

import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NativeFunctionClassifier extends Classifier {
  @NotNull private final Map<Memory.AllocationStack.StackFrame, NativeCallStackSet> myFunctions = new LinkedHashMap<>();
  @NotNull private final Map<String, NativeAllocationMethodSet> myAllocations = new LinkedHashMap<>();
  private final int myDepth;

  NativeFunctionClassifier(int depth) {
    myDepth = depth;
  }

  @Nullable
  @Override
  public ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent) {
    // First check if the instance has a child node.
    Memory.AllocationStack.StackFrame function = getFrameAtCurrentDepth(instance);
    if (function != null) {
      NativeCallStackSet methodSet = myFunctions.get(function);
      if (methodSet == null && createIfAbsent) {
        methodSet = new NativeCallStackSet(function, myDepth + 1);
        myFunctions.put(function, methodSet);
      }
      return methodSet;
    }
    // No function exist at current depth means we are at the leaf.
    // Return the allocation function as an allocation.
    String name = instance.getClassEntry().getClassName();
    NativeAllocationMethodSet allocation = myAllocations.get(name);
    if (allocation == null && createIfAbsent) {
      allocation = new NativeAllocationMethodSet(name);
      myAllocations.put(name, allocation);
    }
    return allocation;
  }

  @Nullable
  private Memory.AllocationStack.StackFrame getFrameAtCurrentDepth(@NotNull InstanceObject instance) {
    int stackDepth = instance.getCallStackDepth();
    Memory.AllocationStack stack = instance.getAllocationCallStack();
    if (stackDepth <= 0 || myDepth >= stackDepth || stack == null) {
      return null;
    }

    int frameIndex = stackDepth - myDepth - 1;
    Memory.AllocationStack.StackFrameWrapper fullStack = stack.getFullStack();
    Memory.AllocationStack.StackFrame stackFrame = fullStack.getFrames(frameIndex);
    return stackFrame;
  }

  @NotNull
  @Override
  public List<ClassifierSet> getFilteredClassifierSets() {
    return Stream.concat(myFunctions.values().stream(), myAllocations.values().stream()).filter(child -> !child.getIsFiltered())
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  protected List<ClassifierSet> getAllClassifierSets() {
    return Stream.concat(myFunctions.values().stream(), myAllocations.values().stream()).collect(Collectors.toList());
  }
}