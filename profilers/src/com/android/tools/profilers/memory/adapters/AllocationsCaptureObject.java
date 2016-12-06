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

import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AllocationsCaptureObject implements CaptureObject {
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
    return Collections.singletonList(new AllocationsHeapObject(this));
  }

  @NotNull
  public MemoryServiceBlockingStub getClient() {
    return myClient;
  }

  public int getAppId() {
    return myAppId;
  }

  public long getStartTimeNs() {
    return myStartTimeNs;
  }

  public long getEndTimeNs() {
    return myEndTimeNs;
  }
}
