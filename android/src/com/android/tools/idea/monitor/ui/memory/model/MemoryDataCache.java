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

import com.android.tools.datastore.DataAdapter;
import com.android.tools.idea.monitor.ui.memory.view.MemoryProfilerUiManager;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.android.tools.profiler.proto.MemoryProfiler.*;

public class MemoryDataCache {
  static final int UNFINISHED_TIMESTAMP = -1;

  private List<MemoryData.MemorySample> myMemorySamples = Collections.synchronizedList(new ArrayList<>());
  private List<MemoryData.VmStatsSample> myVmStatsSamples = Collections.synchronizedList(new ArrayList<>());
  private List<MemoryData.HeapDumpSample> myHeapDumpSamples = Collections.synchronizedList(new ArrayList<>());
  private Map<MemoryData.HeapDumpSample, ByteString> myHeapDumps = new HashMap<>();
  private List<AllocationTrackingSample> myAllocationTrackingSamples = Collections.synchronizedList(new ArrayList<>());

  private EventDispatcher<MemoryProfilerUiManager.MemoryEventListener> myMemoryEventDispatcher;

  public MemoryDataCache(@NotNull EventDispatcher<MemoryProfilerUiManager.MemoryEventListener> dispatcher) {
    myMemoryEventDispatcher = dispatcher;
  }

  public void appendMemorySamples(@NotNull List<MemoryData.MemorySample> memorySamples) {
    myMemorySamples.addAll(memorySamples);
  }

  public void appendVmStatsSamples(@NotNull List<MemoryData.VmStatsSample> vmStatsSamples) {
    myVmStatsSamples.addAll(vmStatsSamples);
  }

  public void appendHeapDumpSample(@NotNull MemoryData.HeapDumpSample heapDumpSample) {
    myHeapDumpSamples.add(heapDumpSample);
  }

  public void addPulledHeapDumpData(@NotNull MemoryData.HeapDumpSample heapDumpSample, @NotNull ByteString heapDumpFile) {
    myHeapDumps.put(heapDumpSample, heapDumpFile);
    myMemoryEventDispatcher.getMulticaster().newHeapDumpSamplesRetrieved(heapDumpSample);
  }

  public void executeOnHeapDumpData(@NotNull BiConsumer<MemoryData.HeapDumpSample, ByteString> biConsumer) {
    myHeapDumps.forEach(biConsumer);
  }

  public void addAllocationTrackingData(@NotNull AllocationTrackingSample allocationTrackingSample) {
    myAllocationTrackingSamples.add(allocationTrackingSample);
    myMemoryEventDispatcher.getMulticaster().newAllocationTrackingSampleRetrieved(allocationTrackingSample);
  }

  public void executeOnAllocationTrackingSamples(@NotNull Consumer<AllocationTrackingSample> consumer) {
    myAllocationTrackingSamples.forEach(consumer);
  }

  @NotNull
  public MemoryData.MemorySample getMemorySample(int index) {
    return myMemorySamples.get(index);
  }

  @NotNull
  public MemoryData.VmStatsSample getVmStatsSample(int index) {
    return myVmStatsSamples.get(index);
  }

  @NotNull
  public MemoryData.HeapDumpSample getHeapDumpSample(int index) {
    return myHeapDumpSamples.get(index);
  }

  @NotNull
  public AllocationTrackingSample getAllocationTrackingSample(int index) {
    return myAllocationTrackingSamples.get(index);
  }

  @NotNull
  public ByteString getHeapDumpData(@NotNull MemoryData.HeapDumpSample sample) {
    assert myHeapDumps.containsKey(sample);
    return myHeapDumps.get(sample);
  }

  public MemoryData.HeapDumpSample swapLastHeapDumpSample(@NotNull MemoryData.HeapDumpSample sample) {
    int lastIndex = getLastHeapDumpIndex();
    MemoryData.HeapDumpSample result = myHeapDumpSamples.get(lastIndex);
    myHeapDumpSamples.set(lastIndex, sample);
    return result;
  }

  public int getLastHeapDumpIndex() {
    return myHeapDumpSamples.size() - 1;
  }

  public int getLatestPriorMemorySampleIndex(long time, boolean leftClosest) {
    int index =
      Collections.binarySearch(myMemorySamples, MemoryData.MemorySample.newBuilder().setTimestamp(time).build(),
                               (left, right) -> {
                                 long diff = left.getTimestamp() - right.getTimestamp();
                                 return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                               });
    return DataAdapter.convertBinarySearchIndex(index, myMemorySamples.size(), leftClosest);
  }

  public int getLatestPriorVmStatsSampleIndex(long time, boolean leftClosest) {
    int index = Collections
      .binarySearch(myVmStatsSamples, MemoryData.VmStatsSample.newBuilder().setTimestamp(time).build(),
                    (left, right) -> {
                      long diff = left.getTimestamp() - right.getTimestamp();
                      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                    });
    return DataAdapter.convertBinarySearchIndex(index, myVmStatsSamples.size(), leftClosest);
  }

  public int getLatestPriorHeapDumpSampleIndex(long time, boolean leftClosest) {
    int index = Collections
      .binarySearch(myHeapDumpSamples, MemoryData.HeapDumpSample.newBuilder().setStartTime(time).build(),
                    (left, right) -> {
                      long diff = left.getStartTime() - right.getStartTime();
                      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                    });
    return DataAdapter.convertBinarySearchIndex(index, myHeapDumpSamples.size(), leftClosest);
  }

  public int getLatestPriorAllocationTrackingSampleIndex(long time, boolean leftClosest) {
    int index = Collections.binarySearch(myAllocationTrackingSamples, new AllocationTrackingSample(time, time, new byte[]{}), (left, right) -> {
      long diff = left.getStartTime() - right.getStartTime();
      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
    });
    return DataAdapter.convertBinarySearchIndex(index, myAllocationTrackingSamples.size(), leftClosest);
  }

  public void reset() {
    myMemorySamples.clear();
    myVmStatsSamples.clear();
    myHeapDumpSamples.clear();
    myHeapDumps.clear();
    myAllocationTrackingSamples.clear();
  }
}
