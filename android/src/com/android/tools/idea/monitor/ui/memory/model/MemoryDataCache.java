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
package com.android.tools.idea.monitor.ui.memory.model;

import com.android.tools.profiler.proto.MemoryProfilerService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemoryDataCache {
  private List<MemoryProfilerService.MemoryData.MemorySample> myMemorySamples = Collections.synchronizedList(new ArrayList<>());
  private List<MemoryProfilerService.MemoryData.InstanceCountSample> myInstanceCountSamples =
    Collections.synchronizedList(new ArrayList<>());
  private List<MemoryProfilerService.MemoryData.GcSample> myGcSamples = Collections.synchronizedList(new ArrayList<>());

  public void appendData(MemoryProfilerService.MemoryData entry) {
    myMemorySamples.addAll(entry.getMemSamplesList());
    myInstanceCountSamples.addAll(entry.getInstanceCountSamplesList());
    myGcSamples.addAll(entry.getGcSamplesList());
  }

  @NotNull
  public MemoryProfilerService.MemoryData.MemorySample getMemorySample(int index) {
    return myMemorySamples.get(index);
  }

  @NotNull
  public MemoryProfilerService.MemoryData.InstanceCountSample getInstanceCountSample(int index) {
    return myInstanceCountSamples.get(index);
  }

  @NotNull
  public MemoryProfilerService.MemoryData.GcSample getGcSample(int index) {
    return myGcSamples.get(index);
  }

  public int getLatestPriorMemorySampleIndex(long time) {
    int index =
      Collections.binarySearch(myMemorySamples, MemoryProfilerService.MemoryData.MemorySample.newBuilder().setTimestamp(time).build(),
                               (left, right) -> {
                                 long diff = left.getTimestamp() - right.getTimestamp();
                                 return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                               });
    if (index < 0) {
      index = -index - 2;
    }
    return Math.max(0, Math.min(myMemorySamples.size() - 1, index));
  }

  public int getLatestPriorInstanceCountSampleIndex(long time) {
    int index = Collections
      .binarySearch(myInstanceCountSamples, MemoryProfilerService.MemoryData.InstanceCountSample.newBuilder().setTimestamp(time).build(),
                    (left, right) -> {
                      long diff = left.getTimestamp() - right.getTimestamp();
                      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                    });
    if (index < 0) {
      index = -index - 2;
    }
    return Math.max(0, Math.min(myInstanceCountSamples.size() - 1, index));
  }

  public int getLatestPriorGcSampleIndex(long time) {
    int index = Collections.binarySearch(myGcSamples, MemoryProfilerService.MemoryData.GcSample.newBuilder().setTimestamp(time).build(),
                                          (left, right) -> {
                                            long diff = left.getTimestamp() - right.getTimestamp();
                                            return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                                          });
    if (index < 0) {
      index = -index - 2;
    }
    return Math.max(0, Math.min(myGcSamples.size() - 1, index));
  }

  public void reset() {
    myMemorySamples.clear();
    myInstanceCountSamples.clear();
    myGcSamples.clear();
  }
}
