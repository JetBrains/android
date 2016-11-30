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
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.AllocationEvent;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.memory.adapters.InstanceObject.ValueType;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.memory.adapters.ClassObject.InstanceAttribute.*;

public class AllocationsCaptureObject extends CaptureObject {
  @NotNull private static final Map<String, ValueType> ourTypeMap = ImmutableMap.<String, ValueType>builder()
    .put("boolean", ValueType.BOOLEAN)
    .put("byte", ValueType.BYTE)
    .put("short", ValueType.SHORT)
    .put("int", ValueType.INT)
    .put("long", ValueType.LONG)
    .put("float", ValueType.FLOAT)
    .put("double", ValueType.DOUBLE)
    .build();

  @NotNull private final MemoryServiceBlockingStub myClient;
  private final int myAppId;
  private final long myStartTimeNs;
  private final long myEndTimeNs;

  public AllocationsCaptureObject(@NotNull MemoryServiceBlockingStub client, int appId, long startTimeNs, long endTimeNs) {
    myClient = client;
    myAppId = appId;
    myStartTimeNs = startTimeNs;
    myEndTimeNs = endTimeNs;
  }

  @Override
  public void dispose() {
  }

  @Override
  public String toString() {
    // TODO refactor this once MemoryObject has been refactored into concrete classes
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
    return Collections.singletonList(new AllocationHeapObject());
  }

  private class AllocationHeapObject extends HeapObject {
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
      AllocationContextsResponse contextsResponse = myClient.listAllocationContexts(
        AllocationContextsRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimeNs).setEndTime(myEndTimeNs).build());

      TIntObjectHashMap<AllocationClassObject> classNodes = new TIntObjectHashMap<>();
      Map<ByteString, AllocationStack> callStacks = new HashMap<>();
      contextsResponse.getAllocatedClassesList().forEach(className -> {
        AllocationClassObject dupe = classNodes.put(className.getClassId(), new AllocationClassObject(className));
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
          .addInstance(new AllocationInstanceObject(event, classNodes.get(event.getAllocatedClassId()), callStacks.get(event.getAllocationStackId())));
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

  private static class AllocationClassObject extends ClassObject {
    @NotNull private final AllocatedClass myAllocatedClass;
    @NotNull private final List<InstanceObject> myInstanceNodes = new ArrayList<>();

    public AllocationClassObject(@NotNull AllocatedClass allocatedClass) {
      myAllocatedClass = allocatedClass;
    }

    @NotNull
    @Override
    public String getName() {
      return myAllocatedClass.getClassName();
    }

    public void addInstance(@NotNull AllocationInstanceObject node) {
      myInstanceNodes.add(node);
    }

    @Override
    public int getChildrenCount() {
      return myInstanceNodes.size();
    }

    @NotNull
    @Override
    public List<InstanceObject> getInstances() {
      return myInstanceNodes;
    }

    @NotNull
    @Override
    public List<InstanceAttribute> getInstanceAttributes() {
      return Arrays.asList(LABEL, SHALLOW_SIZE);
    }
  }

  private static class AllocationInstanceObject extends InstanceObject {
    @NotNull private final AllocationEvent myEvent;
    @NotNull private final AllocationClassObject myAllocationClassObject;
    @NotNull private final AllocationStack myCallStack;

    public AllocationInstanceObject(@NotNull AllocationEvent event, @NotNull AllocationClassObject allocationClassObject, @NotNull AllocationStack callStack) {
      myEvent = event;
      myAllocationClassObject = allocationClassObject;
      myCallStack = callStack;
    }

    @NotNull
    @Override
    public String getName() {
      return myAllocationClassObject.getName();
    }

    @Override
    public int getShallowSize() {
      return myEvent.getSize();
    }

    @NotNull
    @Override
    public AllocationStack getCallStack() {
      return myCallStack;
    }

    @Override
    public ValueType getValueType() {
      String className = myAllocationClassObject.getName();
      if (className.contains(".")) {
        if (className.equals("java.lang.String")) {
          return ValueType.STRING;
        }
        else {
          return ValueType.OBJECT;
        }
      }

      String trimmedClassName = className;
      if (getIsArray()) {
        trimmedClassName = className.substring(0, className.length() - "[]".length());
      }
      return ourTypeMap.getOrDefault(trimmedClassName, ValueType.OBJECT);
    }

    @Override
    public boolean getIsArray() {
      return myAllocationClassObject.getName().endsWith("[]");
    }
  }
}
