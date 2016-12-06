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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.google.protobuf3jarjar.ByteString;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

final class AllocationsHeapObject implements HeapObject {
  @NotNull private final AllocationsCaptureObject myRoot;

  public AllocationsHeapObject(@NotNull AllocationsCaptureObject root) {
    myRoot = root;
  }

  @Override
  public String toString() {
    return getHeapName();
  }

  @NotNull
  @Override
  public String getHeapName() {
    return "default";
  }

  @NotNull
  @Override
  public List<ClassObject> getClasses() {
    // TODO add caching
    MemoryProfiler.AllocationContextsResponse contextsResponse = myRoot.getClient().listAllocationContexts(
      MemoryProfiler.AllocationContextsRequest
        .newBuilder().setAppId(myRoot.getAppId()).setStartTime(myRoot.getStartTimeNs()).setEndTime(myRoot.getEndTimeNs()).build());

    TIntObjectHashMap<AllocationsClassObject> classNodes = new TIntObjectHashMap<>();
    Map<ByteString, MemoryProfiler.AllocationStack> callStacks = new HashMap<>();
    contextsResponse.getAllocatedClassesList().forEach(className -> {
      AllocationsClassObject
        dupe = classNodes.put(className.getClassId(), new AllocationsClassObject(className));
      assert dupe == null;
    });
    contextsResponse.getAllocationStacksList().forEach(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

    MemoryProfiler.MemoryData response = myRoot.getClient()
      .getData(MemoryProfiler.MemoryRequest.newBuilder().setAppId(myRoot.getAppId()).setStartTime(myRoot.getStartTimeNs()).setEndTime(
        myRoot.getEndTimeNs()).build());
    TIntHashSet allocatedClasses = new TIntHashSet();
    // TODO make sure class IDs fall into a global pool
    for (MemoryProfiler.MemoryData.AllocationEvent event : response.getAllocationEventsList()) {
      assert classNodes.containsKey(event.getAllocatedClassId());
      assert callStacks.containsKey(event.getAllocationStackId());
      classNodes.get(event.getAllocatedClassId())
        .addInstance(new AllocationsInstanceObject(event, classNodes.get(event.getAllocatedClassId()), callStacks.get(event.getAllocationStackId())));
      allocatedClasses.add(event.getAllocatedClassId());
    }

    List<ClassObject> classes = new ArrayList<>(classNodes.size());
    allocatedClasses.forEach(value -> {
      classes.add(classNodes.get(value));
      return true;
    });
    return classes;
  }

  @NotNull
  @Override
  public List<ClassAttribute> getClassAttributes() {
    return Arrays.asList(ClassAttribute.LABEL, ClassAttribute.CHILDREN_COUNT);
  }
}
