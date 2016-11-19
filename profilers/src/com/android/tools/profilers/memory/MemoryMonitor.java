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

import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.MemorySample;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class MemoryMonitor extends ProfilerMonitor {

  private final int myProcessId;

  @NotNull
  private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;

  public MemoryMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
  }

  @NotNull
  public MemoryDataSeries getTotalMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getTotalMem);
  }

  @NotNull
  public MemoryDataSeries getJavaMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getJavaMem);
  }

  @NotNull
  public MemoryDataSeries getNativeMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getNativeMem);
  }

  @NotNull
  public MemoryDataSeries getGraphicsMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getGraphicsMem);
  }

  @NotNull
  public MemoryDataSeries getStackMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getStackMem);
  }

  @NotNull
  public MemoryDataSeries getCodeMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getCodeMem);
  }

  @NotNull
  public MemoryDataSeries getOthersMemory() {
    return new MemoryDataSeries(myClient, myProcessId, MemorySample::getOthersMem);
  }

  @NotNull
  public VmStatsDataSeries getObjectCount() {
    return new VmStatsDataSeries(myClient, myProcessId, sample -> (long)(sample.getJavaAllocationCount() - sample.getJavaFreeCount()));
  }

  @Override
  public String getName() {
    return "Memory";
  }

  public void expand() {
    myProfilers.setStage(new MemoryProfilerStage(myProfilers));
  }
}