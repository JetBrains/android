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

import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.perflib.heap.ProguardMap;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataRequest;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.RelativeTimeConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class HeapDumpCaptureObject implements CaptureObject {

  @NotNull
  private final MemoryServiceBlockingStub myClient;

  private final int myProcessId;

  private final Common.Session mySession;

  @NotNull
  private final String myLabel;

  @NotNull
  private final HeapDumpInfo myHeapDumpInfo;

  @Nullable
  private final ProguardMap myProguardMap;

  @Nullable
  private volatile Snapshot mySnapshot;

  private volatile boolean myIsLoadingError = false;

  public HeapDumpCaptureObject(@NotNull MemoryServiceBlockingStub client,
                               Common.Session session,
                               int appId,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap,
                               @NotNull RelativeTimeConverter converter) {
    myClient = client;
    myProcessId = appId;
    mySession = session;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
    myLabel =
      "Heap Dump @ " +
      TimeAxisFormatter.DEFAULT
        .getFixedPointFormattedString(TimeUnit.MILLISECONDS.toMicros(1),
                                      TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myHeapDumpInfo.getStartTime())));
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof HeapDumpCaptureObject)) {
      return false;
    }

    HeapDumpCaptureObject other = (HeapDumpCaptureObject)obj;
    return other.myProcessId == myProcessId && other.myIsLoadingError == myIsLoadingError && other.myHeapDumpInfo == myHeapDumpInfo;
  }

  @NotNull
  @Override
  public String getName() {
    return myLabel;
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    return "hprof";
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) throws IOException {
    DumpDataResponse response = myClient.getHeapDump(
      DumpDataRequest.newBuilder().setProcessId(myProcessId).setSession(mySession).setDumpTime(myHeapDumpInfo.getStartTime()).build());
    if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
      response.getData().writeTo(outputStream);
    }
    else {
      throw new IOException("Could not retrieve hprof dump.");
    }
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
      response = myClient.getHeapDump(DumpDataRequest.newBuilder()
                                        .setProcessId(myProcessId)
                                        .setSession(mySession)
                                        .setDumpTime(myHeapDumpInfo.getStartTime()).build());
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
