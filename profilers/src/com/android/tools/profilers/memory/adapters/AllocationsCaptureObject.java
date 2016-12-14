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
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.google.protobuf3jarjar.ByteString;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class AllocationsCaptureObject implements CaptureObject {
  @NotNull private final MemoryServiceBlockingStub myClient;
  private final int myAppId;
  private final long myStartTimeNs;
  private final long myEndTimeNs;
  private volatile List<ClassObject> myClassObjs = null;

  public AllocationsCaptureObject(@NotNull MemoryServiceBlockingStub client, int appId, long startTimeNs, long endTimeNs) {
    myClient = client;
    myAppId = appId;
    myStartTimeNs = startTimeNs;
    myEndTimeNs = endTimeNs;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof AllocationsCaptureObject)) {
      return false;
    }

    AllocationsCaptureObject other = (AllocationsCaptureObject)obj;

    return other.myAppId == myAppId && other.myStartTimeNs == myStartTimeNs && other.myEndTimeNs == myEndTimeNs;
  }

  @Override
  public void dispose() {
  }

  @Override
  public String toString() {
    // TODO refactor this
    return "Allocations" +
           (myStartTimeNs != Long.MAX_VALUE ? " from " + TimeUnit.NANOSECONDS.toMillis(myStartTimeNs) + "ms" : "") +
           (myEndTimeNs != Long.MIN_VALUE ? " to " + TimeUnit.NANOSECONDS.toMillis(myEndTimeNs) + "ms" : "");
  }

  @NotNull
  @Override
  public String getLabel() {
    return "default";
  }

  @NotNull
  @Override
  public List<HeapObject> getHeaps() {
    //noinspection ConstantConditions
    assert isDoneLoading() && !isError();
    return Collections.singletonList(new AllocationsHeapObject());
  }

  @Override
  public long getStartTimeNs() {
    return myStartTimeNs;
  }

  @Override
  public long getEndTimeNs() {
    return myEndTimeNs;
  }

  @Override
  public boolean load() {
    // TODO add caching
    MemoryProfiler.AllocationContextsResponse contextsResponse = myClient.listAllocationContexts(
      MemoryProfiler.AllocationContextsRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimeNs).setEndTime(myEndTimeNs).build());

    TIntObjectHashMap<AllocationsClassObject> classNodes = new TIntObjectHashMap<>();
    Map<ByteString, MemoryProfiler.AllocationStack> callStacks = new HashMap<>();
    contextsResponse.getAllocatedClassesList().forEach(className -> {
      AllocationsClassObject
        dupe = classNodes.put(className.getClassId(), new AllocationsClassObject(className));
      assert dupe == null;
    });
    contextsResponse.getAllocationStacksList().forEach(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

    MemoryProfiler.MemoryData response = myClient
      .getData(MemoryProfiler.MemoryRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimeNs).setEndTime(myEndTimeNs).build());
    TIntHashSet allocatedClasses = new TIntHashSet();
    // TODO make sure class IDs fall into a global pool
    for (MemoryProfiler.MemoryData.AllocationEvent event : response.getAllocationEventsList()) {
      assert classNodes.containsKey(event.getAllocatedClassId());
      assert callStacks.containsKey(event.getAllocationStackId());
      classNodes.get(event.getAllocatedClassId()).addInstance(
        new AllocationsInstanceObject(event, classNodes.get(event.getAllocatedClassId()), callStacks.get(event.getAllocationStackId())));
      allocatedClasses.add(event.getAllocatedClassId());
    }

    List<ClassObject> classes = new ArrayList<>(classNodes.size());
    allocatedClasses.forEach(value -> {
      classes.add(classNodes.get(value));
      return true;
    });
    myClassObjs = classes;
    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return myClassObjs != null;
  }

  @Override
  public boolean isError() {
    return false;
  }

  final class AllocationsHeapObject implements HeapObject {
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
      return myClassObjs;
    }

    @NotNull
    @Override
    public List<ClassAttribute> getClassAttributes() {
      return Arrays.asList(ClassAttribute.LABEL, ClassAttribute.CHILDREN_COUNT);
    }
  }
}
