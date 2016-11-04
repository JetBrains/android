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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.datastore.DataAdapter;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.HashMap;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;
import static com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache.UNFINISHED_TIMESTAMP;
import static com.android.tools.profiler.proto.MemoryProfiler.*;

public class MemoryPoller extends Poller {
  @NotNull private final MemoryDataCache myDataCache;

  private long myStartTimestampNs;

  private final int myAppId;

  private boolean myHasPendingHeapDumpSample;

  public MemoryPoller(@NotNull SeriesDataStore dataStore, @NotNull MemoryDataCache dataCache, int appId) {
    super(dataStore, POLLING_DELAY_NS);
    myDataCache = dataCache;
    myAppId = appId;
    myHasPendingHeapDumpSample = false;
  }

  public Map<SeriesDataType, DataAdapter> createAdapters() {
    Map<SeriesDataType, DataAdapter> adapters = new HashMap<>();

    adapters.put(SeriesDataType.MEMORY_TOTAL, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getTotalMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_JAVA, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getJavaMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_NATIVE, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getNativeMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_GRAPHICS, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getGraphicsMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_CODE, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getCodeMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_OTHERS, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.MemorySample sample) {
        return sample.getOthersMem();
      }
    });

    adapters.put(SeriesDataType.MEMORY_OBJECT_COUNT, new VmStatsSampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryData.VmStatsSample sample) {
        return new Long(sample.getJavaAllocationCount() - sample.getJavaFreeCount());
      }
    });

    adapters.put(SeriesDataType.MEMORY_HEAPDUMP_EVENT, new HeapDumpSampleAdapter());
    adapters.put(SeriesDataType.MEMORY_ALLOCATION_TRACKING_EVENT, new AllocationTrackingSampleAdapter());

    return adapters;
  }

  @Override
  protected void asyncInit() throws StatusRuntimeException {
    MemoryConfig config = MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryConfig.Option.newBuilder().setFeature(MemoryFeature.MEMORY_LEVELS).setEnabled(true)
        .build()
    ).build();
    MemoryStatus status = myService.getMemoryService().setMemoryConfig(config);
    myStartTimestampNs = status.getStatusTimestamp();
  }

  @Override
  protected void asyncShutdown() throws StatusRuntimeException {
    MemoryConfig config = MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryConfig.Option.newBuilder().setFeature(MemoryFeature.MEMORY_LEVELS).setEnabled(false)
        .build()
    ).build();
    myService.getMemoryService().setMemoryConfig(config);
  }

  @Override
  protected void poll() throws StatusRuntimeException {
    MemoryRequest request = MemoryRequest.newBuilder()
      .setAppId(myAppId)
      .setStartTime(myStartTimestampNs)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryData result = myService.getMemoryService().getData(request);

    myDataCache.appendMemorySamples(result.getMemSamplesList());
    myDataCache.appendVmStatsSamples(result.getVmStatsSamplesList());

    List<MemoryData.HeapDumpSample> pendingFetch = new ArrayList<>();
    for (int i = 0; i < result.getHeapDumpSamplesCount(); i++) {
      MemoryData.HeapDumpSample sample = result.getHeapDumpSamples(i);
      if (myHasPendingHeapDumpSample) {
        // Note - if there is an existing pending heap dump, the first sample from the response should represent the same sample
        assert i == 0 && sample.getEndTime() != UNFINISHED_TIMESTAMP;

        MemoryData.HeapDumpSample previousLastSample = myDataCache.swapLastHeapDumpSample(sample);
        assert previousLastSample.getFilePath().equals(sample.getFilePath());
        myHasPendingHeapDumpSample = false;
        pendingFetch.add(sample);
      }
      else {
        myDataCache.appendHeapDumpSample(sample);

        if (sample.getEndTime() == UNFINISHED_TIMESTAMP) {
          // Note - there should be at most one unfinished heap dump request at a time. e.g. the final sample from the response.
          assert i == result.getHeapDumpSamplesCount() - 1;
          myHasPendingHeapDumpSample = true;
        }
        else {
          pendingFetch.add(sample);
        }
      }
    }

    if (!pendingFetch.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        for (MemoryData.HeapDumpSample sample : pendingFetch) {
          ByteString heapDumpData = pullHeapDumpData(sample);
          if (heapDumpData != null) {
            myDataCache.addPulledHeapDumpData(sample, heapDumpData);
          }
        }
      });
    }

    if (result.getEndTimestamp() > myStartTimestampNs) {
      myStartTimestampNs = result.getEndTimestamp();
    }
  }

  /**
   * Triggers a heap dump grpc request
   */
  public boolean requestHeapDump() {
    HeapDumpRequest.Builder builder = HeapDumpRequest.newBuilder();
    builder.setAppId(myAppId);
    builder.setRequestTime(myStartTimestampNs);   // Currently not used on perfd.
    switch (myService.getMemoryService().triggerHeapDump(builder.build()).getStatus()) {
      case SUCCESS:
        return true;
      default:
        return false;
    }
  }

  @Nullable
  private ByteString pullHeapDumpData(@NotNull MemoryData.HeapDumpSample sample) {
    if (!sample.getSuccess()) {
      return null;
    }

    HeapDumpDataRequest dataRequest = HeapDumpDataRequest.newBuilder().setAppId(myAppId).setDumpId(sample.getDumpId()).build();
    HeapDumpDataResponse response = myService.getMemoryService().getHeapDump(dataRequest);
    if (response.getStatus() == HeapDumpDataResponse.Status.SUCCESS) {
      return response.getData();
    }
    return null;
  }

  private abstract class MemorySampleAdapter<T> implements DataAdapter<T> {
    @Override
    public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
      return myDataCache.getLatestPriorMemorySampleIndex(TimeUnit.MICROSECONDS.toNanos(timeUs), leftClosest);
    }

    @Override
    public void reset() {
      myDataCache.reset();
    }

    @Override
    public void stop() {
      MemoryPoller.this.stop();
    }

    @Override
    public SeriesData<T> get(int index) {
      MemoryData.MemorySample sample = myDataCache.getMemorySample(index);
      return new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(sample.getTimestamp()), getSampleValue(sample));
    }

    public abstract T getSampleValue(MemoryData.MemorySample sample);
  }

  private abstract class VmStatsSampleAdapter<T> implements DataAdapter<T> {
    @Override
    public int getClosestTimeIndex(long time, boolean leftClosest) {
      return myDataCache.getLatestPriorVmStatsSampleIndex(TimeUnit.MICROSECONDS.toNanos(time), leftClosest);
    }

    @Override
    public void reset() {
      myDataCache.reset();
    }

    @Override
    public void stop() {
      MemoryPoller.this.stop();
    }

    @Override
    public SeriesData<T> get(int index) {
      MemoryData.VmStatsSample sample = myDataCache.getVmStatsSample(index);
      return new SeriesData<>(TimeUnit.NANOSECONDS.toMicros(sample.getTimestamp()), getSampleValue(sample));
    }

    public abstract T getSampleValue(MemoryData.VmStatsSample sample);
  }

  private class HeapDumpSampleAdapter implements DataAdapter<DurationData> {
    @Override
    public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
      return myDataCache.getLatestPriorHeapDumpSampleIndex(TimeUnit.MICROSECONDS.toNanos(timeUs), leftClosest);
    }

    @Override
    public void reset() {
      myDataCache.reset();
    }

    @Override
    public void stop() {
      MemoryPoller.this.stop();
    }

    @Override
    public SeriesData<DurationData> get(int index) {
      MemoryData.HeapDumpSample sample = myDataCache.getHeapDumpSample(index);
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(sample.getStartTime());
      long durationUs = sample.getEndTime() == UNFINISHED_TIMESTAMP ? UNSPECIFIED_DURATION :
                        TimeUnit.NANOSECONDS.toMicros(sample.getEndTime() - sample.getStartTime());
      return new SeriesData<>(startTimeUs, new DurationData(durationUs));
    }
  }

  private class AllocationTrackingSampleAdapter implements DataAdapter<DurationData> {
    @Override
    public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
      return myDataCache.getLatestPriorAllocationTrackingSampleIndex(TimeUnit.MICROSECONDS.toNanos(timeUs), leftClosest);
    }

    @Override
    public SeriesData<DurationData> get(int index) {
      AllocationTrackingSample sample = myDataCache.getAllocationTrackingSample(index);
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(sample.getStartTime());
      long durationUs = sample.getEndTime() == UNFINISHED_TIMESTAMP ? UNSPECIFIED_DURATION :
                        TimeUnit.NANOSECONDS.toMicros(sample.getEndTime() - sample.getStartTime());
      return new SeriesData<>(startTimeUs, new DurationData(durationUs));
    }

    @Override
    public void reset() {
      myDataCache.reset();
    }

    @Override
    public void stop() {
      MemoryPoller.this.stop();
    }
  }
}
