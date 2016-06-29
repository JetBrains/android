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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.ui.memory.view.MemoryProfilerUiManager;
import com.android.tools.profiler.proto.MemoryProfilerService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MemoryDataCache {
  private static final Logger LOG = Logger.getInstance(MemoryDataCache.class);
  private static final long UNFINISHED_HEAP_DUMP_TIMESTAMP = -1;

  private final int myPid;
  private List<MemoryProfilerService.MemoryData.MemorySample> myMemorySamples = Collections.synchronizedList(new ArrayList<>());
  private List<MemoryProfilerService.MemoryData.VmStatsSample> myVmStatsSamples =
    Collections.synchronizedList(new ArrayList<>());
  private List<MemoryProfilerService.MemoryData.HeapDumpSample> myHeapDumpSamples = Collections.synchronizedList(new ArrayList<>());

  private boolean myHasPendingHeapDumpSample;

  private Map<String, File> myHeapDumpFiles = new HashMap<>();

  @NotNull
  private final IDevice myDevice;

  private EventDispatcher<MemoryProfilerUiManager.MemoryEventListener> myMemoryEventDispatcher;

  public MemoryDataCache(int pid, IDevice device, EventDispatcher<MemoryProfilerUiManager.MemoryEventListener> dispatcher) {
    myPid = pid;
    myDevice = device;
    myMemoryEventDispatcher = dispatcher;
  }

  public void appendData(MemoryProfilerService.MemoryData entry) {
    myMemorySamples.addAll(entry.getMemSamplesList());
    myVmStatsSamples.addAll(entry.getVmStatsSamplesList());

    List<MemoryProfilerService.MemoryData.HeapDumpSample> newSamples = new ArrayList<>();
    for (int i = 0; i < entry.getHeapDumpSamplesCount(); i++) {
      MemoryProfilerService.MemoryData.HeapDumpSample sample = entry.getHeapDumpSamples(i);
      if (myHasPendingHeapDumpSample) {
        // Note - if there is an existing pending heap dump, the first sample from the response should represent the same sample
        assert i == 0 && myHeapDumpSamples.get(myHeapDumpSamples.size() - 1).getFilePath().equals(sample.getFilePath());

        if (sample.getEndTime() != UNFINISHED_HEAP_DUMP_TIMESTAMP) {
          myHeapDumpSamples.set(myHeapDumpSamples.size() - 1, sample);
          myHasPendingHeapDumpSample = false;
          newSamples.add(sample);
        }
      }
      else {
        myHeapDumpSamples.add(sample);

        if (sample.getEndTime() == UNFINISHED_HEAP_DUMP_TIMESTAMP) {
          // Note - there should be at most one unfinished heap dump request at a time. e.g. the final sample from the response.
          assert i == entry.getHeapDumpSamplesCount() - 1;
          myHasPendingHeapDumpSample = true;
        }
        else {
          newSamples.add(sample);
        }
      }
    }

    if (!newSamples.isEmpty()) {
      myMemoryEventDispatcher.getMulticaster().newHeapDumpSamplesReceived(newSamples);
    }
  }

  @NotNull
  public MemoryProfilerService.MemoryData.MemorySample getMemorySample(int index) {
    return myMemorySamples.get(index);
  }

  @NotNull
  public MemoryProfilerService.MemoryData.VmStatsSample getVmStatsSample(int index) {
    return myVmStatsSamples.get(index);
  }

  @NotNull
  public List<MemoryProfilerService.MemoryData.HeapDumpSample> getHeapDumpSamples() {
    return myHeapDumpSamples;
  }

  @Nullable
  public File getHeapDumpFile(@Nullable MemoryProfilerService.MemoryData.HeapDumpSample sample) {
    if (sample == null || !sample.getSuccess()) {
      return null;
    }

    File tempFile = myHeapDumpFiles.get(sample.getFilePath());
    if (tempFile == null) {
      try {
        tempFile = File.createTempFile(Long.toString(sample.getEndTime()), ".hprof");
        if (tempFile != null) {
          tempFile.deleteOnExit();
          myDevice.pullFile(sample.getFilePath(), tempFile.getAbsolutePath());
        }
      }
      catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
        LOG.info("Error pulling '" + sample.getFilePath() + "' from device.", e);
      }
    }

    return tempFile;
  }

  public int getLatestPriorMemorySampleIndex(long time, boolean leftClosest) {
    int index =
      Collections.binarySearch(myMemorySamples, MemoryProfilerService.MemoryData.MemorySample.newBuilder().setTimestamp(time).build(),
                               (left, right) -> {
                                 long diff = left.getTimestamp() - right.getTimestamp();
                                 return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                               });
    index = DataAdapter.convertBinarySearchIndex(index, leftClosest);
    return Math.max(0, Math.min(myMemorySamples.size(), index));
  }

  public int getLatestPriorVmStatsSampleIndex(long time, boolean leftClosest) {
    int index = Collections
      .binarySearch(myVmStatsSamples, MemoryProfilerService.MemoryData.VmStatsSample.newBuilder().setTimestamp(time).build(),
                    (left, right) -> {
                      long diff = left.getTimestamp() - right.getTimestamp();
                      return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
                    });
    index = DataAdapter.convertBinarySearchIndex(index, leftClosest);
    return Math.max(0, Math.min(myVmStatsSamples.size(), index));
  }

  public void reset() {
    myMemorySamples.clear();
    myVmStatsSamples.clear();
    myHeapDumpSamples.clear();

    for (File file : myHeapDumpFiles.values()) {
      file.delete();
    }
    myHeapDumpFiles.clear();
  }
}
