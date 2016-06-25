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

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.profiler.proto.MemoryProfilerService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MemoryPoller extends Poller {
  private static final Logger LOG = Logger.getInstance(MemoryPoller.class.getCanonicalName());

  private static final long POLL_PERIOD_NS = TimeUnit.MILLISECONDS.toNanos(250);

  @NotNull private final MemoryDataCache myDataCache;
  private long myStartTimestamp;
  final private int myAppId;

  public MemoryPoller(@NotNull SeriesDataStore dataStore, @NotNull MemoryDataCache dataCache, int appId) {
    super(dataStore, POLL_PERIOD_NS);
    myDataCache = dataCache;
    myAppId = appId;
  }

  public Map<SeriesDataType, DataAdapter> createAdapters() {
    Map<SeriesDataType, DataAdapter> adapters = new HashMap<>();
    adapters.put(SeriesDataType.MEMORY_TOTAL, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getTotalMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_JAVA, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getJavaMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_NATIVE, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getNativeMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_GRAPHICS, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getGraphicsMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_CODE, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getCodeMem();
      }
    });
    adapters.put(SeriesDataType.MEMORY_OTHERS, new MemorySampleAdapter<Long>() {
      @Override
      public Long getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample) {
        return sample.getOthersMem();
      }
    });
    return adapters;
  }

  @Override
  protected void asyncInit() {
    MemoryProfilerService.MemoryConfig config = MemoryProfilerService.MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryProfilerService.MemoryConfig.Option.newBuilder().setFeature(MemoryProfilerService.MemoryFeature.MEMORY_LEVELS).setEnabled(true)
        .build()
    ).build();
    MemoryProfilerService.MemoryStatus status = myService.getMemoryService().setMemoryConfig(config);
    myStartTimestamp = status.getStatusTimestamp();
  }

  @Override
  protected void asyncShutdown() {
    MemoryProfilerService.MemoryConfig config = MemoryProfilerService.MemoryConfig.newBuilder().setAppId(myAppId).addOptions(
      MemoryProfilerService.MemoryConfig.Option.newBuilder().setFeature(MemoryProfilerService.MemoryFeature.MEMORY_LEVELS).setEnabled(false)
        .build()
    ).build();
    myService.getMemoryService().setMemoryConfig(config);
  }

  @Override
  protected void poll() {
    MemoryProfilerService.MemoryRequest request =
      MemoryProfilerService.MemoryRequest.newBuilder().setAppId(myAppId).setStartTime(myStartTimestamp).setEndTime(Long.MAX_VALUE).build();
    MemoryProfilerService.MemoryData result;
    try {
      result = myService.getMemoryService().getData(request);
      myDataCache.appendData(result);
    }
    catch (StatusRuntimeException e) {
      LOG.info("Server most likely unreachable.");
      cancel(true);
      return;
    }

    myStartTimestamp = result.getEndTimestamp();
  }

  public abstract class MemorySampleAdapter<T> implements DataAdapter<T> {

    @Override
    public int getClosestTimeIndex(long time) {
      return myDataCache.getLatestPriorMemorySampleIndex(TimeUnit.NANOSECONDS.convert(time, TimeUnit.MILLISECONDS));
    }

    @Override
    public void reset(long deviceStartTimeMs, long studioStartTimeMs) {
      myDataCache.reset();
    }

    @Override
    public void stop() {
      MemoryPoller.this.stop();
    }

    @Override
    public SeriesData<T> get(int index) {
      MemoryProfilerService.MemoryData.MemorySample sample = myDataCache.getMemorySample(index);
      return new SeriesData<>(TimeUnit.NANOSECONDS.toMillis(sample.getTimestamp()), getSampleValue(sample));
    }

    public abstract T getSampleValue(MemoryProfilerService.MemoryData.MemorySample sample);
  }
}
