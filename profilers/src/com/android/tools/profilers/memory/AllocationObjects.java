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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AllocationObjects implements MemoryObjects {
  @NotNull private final RootAllocationsNode myRoot;
  @NotNull private final MemoryServiceBlockingStub myClient;
  private final int myAppId;
  private final long myStartTimeNs;
  private final long myEndTimeNs;

  public AllocationObjects(@NotNull MemoryServiceBlockingStub client, int appId, long startTimeNs, long endTimeNs) {
    myClient = client;
    myAppId = appId;
    myRoot = new RootAllocationsNode();
    myStartTimeNs = startTimeNs;
    myEndTimeNs = endTimeNs;
  }

  @NotNull
  @Override
  public MemoryNode getRootNode() {
    return myRoot;
  }

  @Override
  public void dispose() {

  }

  private class RootAllocationsNode implements MemoryNode {
    @Override
    public String toString() {
      // TODO refactor this once MemoryNode has been refactored into concrete classes
      return "Allocations" +
             (myStartTimeNs != Long.MAX_VALUE ? " from " + TimeUnit.NANOSECONDS.toMillis(myStartTimeNs) + "ms" : "") +
             (myEndTimeNs != Long.MIN_VALUE ? " to " + TimeUnit.NANOSECONDS.toMillis(myEndTimeNs) + "ms" : "");
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList() {
      return Collections.singletonList(new HeapNode());
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Collections.singletonList(Capability.LABEL);
    }
  }

  private class HeapNode implements MemoryNode {
    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public String getName() {
      return "default";
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList() {
      // TODO add caching
      AllocationContextsResponse contextsResponse = myClient.listAllocationContexts(
        AllocationContextsRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimeNs).setEndTime(myEndTimeNs).build());

      TIntObjectHashMap<AllocationClassNode> classNodes = new TIntObjectHashMap<>();
      Map<ByteString, AllocationStack> callStacks = new HashMap<>();
      contextsResponse.getAllocatedClassesList().forEach(className -> {
        AllocationClassNode dupe = classNodes.put(className.getClassId(), new AllocationClassNode(className));
        assert dupe == null;
      });
      contextsResponse.getAllocationStacksList().forEach(callStack -> callStacks.putIfAbsent(callStack.getStackId(), callStack));

      MemoryData response = myClient
        .getData(MemoryProfiler.MemoryRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimeNs).setEndTime(myEndTimeNs).build());
      TIntHashSet allocatedClasses = new TIntHashSet();
      // TODO make sure class IDs fall into a global pool
      for (AllocationEvent event : response.getAllocationEventsList()) {
        assert classNodes.containsKey(event.getAllocatedClassId());
        assert callStacks.containsKey(event.getAllocationStackId());
        classNodes.get(event.getAllocatedClassId())
          .addInstance(new AllocationInstanceNode(event, classNodes.get(event.getAllocatedClassId()), callStacks.get(event.getAllocationStackId())));
        allocatedClasses.add(event.getAllocatedClassId());
      }

      List<MemoryNode> results = new ArrayList<>(classNodes.size());
      allocatedClasses.forEach(value -> {
        results.add(classNodes.get(value));
        return true;
      });
      return results;
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.CHILDREN_COUNT);
    }
  }

  private static class AllocationClassNode implements MemoryNode {
    @NotNull private final AllocatedClass myAllocatedClass;
    @NotNull private final List<MemoryNode> myInstanceNodes = new ArrayList<>();

    public AllocationClassNode(@NotNull AllocatedClass allocatedClass) {
      myAllocatedClass = allocatedClass;
    }

    @NotNull
    @Override
    public String getName() {
      return myAllocatedClass.getClassName();
    }

    public void addInstance(@NotNull AllocationInstanceNode node) {
      myInstanceNodes.add(node);
    }

    @Override
    public int getChildrenCount() {
      return myInstanceNodes.size();
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList() {
      return myInstanceNodes;
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.SHALLOW_SIZE);
    }
  }

  private static class AllocationInstanceNode implements MemoryNode {
    @NotNull private final AllocationEvent myEvent;
    @NotNull private final AllocationClassNode myAllocationClassNode;
    @NotNull private final AllocationStack myCallStack;

    public AllocationInstanceNode(@NotNull AllocationEvent event, @NotNull AllocationClassNode allocationClassNode, @NotNull AllocationStack callStack) {
      myEvent = event;
      myAllocationClassNode = allocationClassNode;
      myCallStack = callStack;
    }

    @NotNull
    @Override
    public String getName() {
      return myAllocationClassNode.getName();
    }

    @Override
    public int getShallowSize() {
      return myEvent.getSize();
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.ELEMENT_SIZE, Capability.SHALLOW_SIZE);
    }
  }
}
