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
import java.util.List;
import java.util.stream.Collectors;

// TODO finish this class for the memory detail view
public class HeapDumpCaptureObject extends CaptureObject {
  @NotNull
  private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;

  private final int myAppId;

  @NotNull
  private final HeapDumpInfo myHeapDumpInfo;

  @Nullable
  private final ProguardMap myProguardMap;

  @Nullable
  private Snapshot mySnapshot;

  public HeapDumpCaptureObject(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                               int appId,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap) {
    myClient = client;
    myAppId = appId;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
  }

  @Override
  public void dispose() {
    if (mySnapshot != null) {
      mySnapshot.dispose();
      mySnapshot = null;
    }
  }

  @Override
  public String toString() {
    return "Heap Dump " + myHeapDumpInfo.getDumpId() + " @" + myHeapDumpInfo.getStartTime();
  }

  @NotNull
  @Override
  public String getLabel() {
    return "";
  }

  @NotNull
  @Override
  public List<HeapObject> getHeaps() {
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

    return mySnapshot.getHeaps().stream().map(HeapDumpHeapObject::new).collect(Collectors.toList());
  }
}
