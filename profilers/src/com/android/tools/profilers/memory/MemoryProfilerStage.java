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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.android.tools.profiler.proto.MemoryProfiler.MemoryData;

public class MemoryProfilerStage extends Stage {
  private final int myProcessId;

  @NotNull
  private final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;

  @NotNull
  private List<RangedContinuousSeries> myRangedSeries;

  public MemoryProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProcessId = profilers.getProcessId();
    myClient = profilers.getClient().getMemoryClient();
  }

  @Override
  public void enter() {
    Range xRange = getStudioProfilers().getViewRange();
    myRangedSeries = Arrays.asList(new RangedContinuousSeries("Java", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @Override
                                                                @NotNull
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getJavaMem();
                                                                }
                                                              }),
                                   new RangedContinuousSeries("Native", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @NotNull
                                                                @Override
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getNativeMem();
                                                                }
                                                              }),
                                   new RangedContinuousSeries("Graphics", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @NotNull
                                                                @Override
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getGraphicsMem();
                                                                }
                                                              }),
                                   new RangedContinuousSeries("Stack", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @NotNull
                                                                @Override
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getStackMem();
                                                                }
                                                              }),
                                   new RangedContinuousSeries("Code", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @NotNull
                                                                @Override
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getCodeMem();
                                                                }
                                                              }),
                                   new RangedContinuousSeries("Other", xRange, new Range(0, 1024*1024),
                                                              new MemoryDataSeries(myClient, myProcessId) {
                                                                @NotNull
                                                                @Override
                                                                public Long filterData(@NotNull MemoryData.MemorySample sample) {
                                                                  return sample.getOthersMem();
                                                                }
                                                              })
                                   );
  }

  @Override
  public void exit() {

  }

  @NotNull
  public List<RangedContinuousSeries> getRangedSeries() {
    return myRangedSeries;
  }
}
