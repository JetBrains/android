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

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.ProguardMap;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpDataRequest;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// TODO finish this class for the memory detail view
public class HeapDumpObjects implements MemoryObjects {
  @NotNull
  private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;

  private final MemoryNode myHeapDumpRootModelable;

  private final int myAppId;

  @NotNull
  private final HeapDumpInfo myHeapDumpInfo;

  @Nullable
  private final ProguardMap myProguardMap;

  @Nullable
  private Snapshot mySnapshot;

  public HeapDumpObjects(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                         int appId,
                         @NotNull HeapDumpInfo heapDumpInfo,
                         @Nullable ProguardMap proguardMap) {
    myClient = client;
    myAppId = appId;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
    myHeapDumpRootModelable = this.new RootHeapDumpAdapter();
  }

  @NotNull
  @Override
  public MemoryNode getRootAdapter() {
    return myHeapDumpRootModelable;
  }

  @Override
  public void dispose() {
    if (mySnapshot != null) {
      mySnapshot.dispose();
      mySnapshot = null;
    }
  }

  private class RootHeapDumpAdapter implements MemoryNode {
    @Override
    public String toString() {
      return "Heap Dump " + myHeapDumpInfo.getDumpId() + " @" + myHeapDumpInfo.getStartTime();
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTime, long endTime) {
      DumpDataResponse response;
      while (true) {
        // TODO move this to another thread and complete before we notify
        response = myClient.getHeapDump(HeapDumpDataRequest.newBuilder().setAppId(myAppId).setDumpId(myHeapDumpInfo.getDumpId()).build());
        if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
          break;
        }
        else if (response.getStatus() == DumpDataResponse.Status.NOT_READY) {
          try {
            Thread.sleep(50L);
          }
          catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
          }
          continue;
        }
        return new ArrayList<>();
      }

      InMemoryBuffer buffer = new InMemoryBuffer(response.getData().asReadOnlyByteBuffer());
      if (myProguardMap != null) {
        mySnapshot = Snapshot.createSnapshot(buffer, myProguardMap);
      }
      else {
        mySnapshot = Snapshot.createSnapshot(buffer);
      }
      mySnapshot.computeDominators();

      return mySnapshot.getHeaps().stream().map(HeapNode::new).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Collections.singletonList(Capability.LABEL);
    }
  }

  private static class HeapNode implements MemoryNode {
    @NotNull
    private final Heap myHeap;

    public HeapNode(@NotNull Heap heap) {
      myHeap = heap;
    }

    @NotNull
    public Heap getHeap() {
      return myHeap;
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public String getName() {
      return myHeap.getName();
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTime, long endTime) {
      return myHeap.getClasses().stream().map(ClassNode::new).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays
        .asList(Capability.LABEL, Capability.CHILDREN_COUNT, Capability.ELEMENT_SIZE, Capability.SHALLOW_SIZE, Capability.RETAINED_SIZE);
    }
  }

  private static class ClassNode implements MemoryNode {
    private final ClassObj myClassObj;

    public ClassNode(@NotNull ClassObj classObj) {
      myClassObj = classObj;
    }

    @NotNull
    @Override
    public String getName() {
      return myClassObj.getClassName();
    }

    @Override
    public int getChildrenCount() {
      return myClassObj.getInstanceCount();
    }

    @Override
    public int getElementSize() {
      return myClassObj.getSize();
    }

    @Override
    public int getShallowSize() {
      return myClassObj.getShallowSize();
    }

    @Override
    public long getRetainedSize() {
      return myClassObj.getTotalRetainedSize();
    }

    @NotNull
    @Override
    public List<MemoryNode> getSubList(long startTime, long endTime) {
      // TODO implement instance expansion
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<Capability> getCapabilities() {
      return Arrays.asList(Capability.LABEL, Capability.ELEMENT_SIZE);
    }
  }
}
