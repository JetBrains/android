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
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class HeapDumpCaptureObject implements CaptureObject {
  @NotNull
  private final MemoryServiceBlockingStub myClient;

  private final int myAppId;

  @NotNull
  private final HeapDumpInfo myHeapDumpInfo;

  @Nullable
  private final ProguardMap myProguardMap;

  @Nullable
  private volatile Snapshot mySnapshot;

  private volatile boolean myIsLoadingError = false;

  public HeapDumpCaptureObject(@NotNull MemoryServiceBlockingStub client,
                               int appId,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap) {
    myClient = client;
    myAppId = appId;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpCaptureObject)) {
      return false;
    }

    HeapDumpCaptureObject other = (HeapDumpCaptureObject)obj;
    return other.myAppId == myAppId && other.myIsLoadingError != myIsLoadingError && other.myHeapDumpInfo == myHeapDumpInfo;
  }

  @Override
  public void dispose() {
    Snapshot snapshot = mySnapshot;
    if (snapshot != null) {
      snapshot.dispose();
      mySnapshot = null;
    }
  }

  @NotNull
  @Override
  public String getLabel() {
    return "Heap Dump " + myHeapDumpInfo.getDumpId() + " @" + myHeapDumpInfo.getStartTime();
  }

  @NotNull
  @Override
  public List<HeapObject> getHeaps() {
    Snapshot snapshot = mySnapshot;
    if (snapshot == null) {
      return Collections.emptyList();
    }
    return snapshot.getHeaps().stream().map(HeapDumpHeapObject::new).collect(Collectors.toList());
  }

  @Override
  public long getStartTimeNs() {
    return myHeapDumpInfo.getStartTime();
  }

  @Override
  public long getEndTimeNs() {
    return myHeapDumpInfo.getEndTime();
  }

  @Override
  public boolean load() {
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
          myIsLoadingError = true;
          return false;
        }
        continue;
      }
      myIsLoadingError = true;
      return false;
    }

    InMemoryBuffer buffer = new InMemoryBuffer(response.getData().asReadOnlyByteBuffer());
    Snapshot snapshot;
    if (myProguardMap != null) {
      snapshot = Snapshot.createSnapshot(buffer, myProguardMap);
    }
    else {
      snapshot = Snapshot.createSnapshot(buffer);
    }
    snapshot.computeDominators();
    mySnapshot = snapshot;

    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return mySnapshot != null || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }
}
