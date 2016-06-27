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
  private List<MemoryProfilerService.MemoryData.VmStatsSample> myVmStatsSamples =
    Collections.synchronizedList(new ArrayList<>());

  public void appendData(MemoryProfilerService.MemoryData entry) {
    myMemorySamples.addAll(entry.getMemSamplesList());
    myVmStatsSamples.addAll(entry.getVmStatsSamplesList());
  }

  @NotNull
  public MemoryProfilerService.MemoryData.MemorySample getMemorySample(int index) {
    return myMemorySamples.get(index);
  }

  @NotNull
  public MemoryProfilerService.MemoryData.VmStatsSample getVmStatsSample(int index) {
    return myVmStatsSamples.get(index);
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

  public int getLatestPriorVmStatsSampleIndex(long time) {
    int index = Collections
      .binarySearch(myVmStatsSamples, MemoryProfilerService.MemoryData.VmStatsSample.newBuilder().setTimestamp(time).build(),
                    (left, right) -> {
                      long diff = left.getTimestamp() - right.getTimestamp();
                      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                    });
    if (index < 0) {
      index = -index - 2;
    }
    return Math.max(0, Math.min(myVmStatsSamples.size() - 1, index));
  }

  public void reset() {
    myMemorySamples.clear();
    myVmStatsSamples.clear();
  }
}
