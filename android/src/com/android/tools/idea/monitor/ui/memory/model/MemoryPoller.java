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
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.monitor.datastore.DataAdapter;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.model.DurationData.UNSPECIFIED_DURATION;
import static com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache.UNFINISHED_HEAP_DUMP_TIMESTAMP;
import static com.android.tools.profiler.proto.MemoryProfiler.*;

public class MemoryPoller extends Poller {
  private static final Logger LOG = Logger.getInstance(MemoryPoller.class.getCanonicalName());

  @NotNull private final MemoryDataCache myDataCache;

  @NotNull private final IDevice myDevice;

  @Nullable private volatile CountDownLatch myLogcatHeapDumpFinishedLatch = null;

  @NotNull private final AndroidLogcatService.LogcatListener myLogcatListener;

  private long myStartTimestampNs;

  private final int myAppId;

  private boolean myHasPendingHeapDumpSample;

  private volatile boolean myIsListeningForLogcat = false;

  public MemoryPoller(@NotNull SeriesDataStore dataStore, @NotNull MemoryDataCache dataCache, int appId) {
    super(dataStore, POLLING_DELAY_NS);
    myDataCache = dataCache;
    myAppId = appId;
    myHasPendingHeapDumpSample = false;
    myDevice = myService.getDevice();

    myLogcatListener = new AndroidLogcatService.LogcatListener() {
      @Override
      public void onLogLineReceived(@NotNull LogCatMessage line) {
        CountDownLatch capturedLatch = myLogcatHeapDumpFinishedLatch;
        if (myIsListeningForLogcat && line.getMessage().contains("hprof: heap dump completed") && line.getPid() == appId) {
          assert capturedLatch != null;
          myIsListeningForLogcat = false;
          capturedLatch.countDown();
        }
      }
    };

    AndroidLogcatService.getInstance().addListener(myDevice, myLogcatListener);
  }

  @Override
  public void stop() {
    myIsListeningForLogcat = false;
    CountDownLatch capturedLatch = myLogcatHeapDumpFinishedLatch;
    if (capturedLatch != null) {
      capturedLatch.countDown();
    }

    super.stop();

    AndroidLogcatService.getInstance().removeListener(myDevice, myLogcatListener);
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

    List<MemoryData.HeapDumpSample> pendingPulls = new ArrayList<>();
    for (int i = 0; i < result.getHeapDumpSamplesCount(); i++) {
      MemoryData.HeapDumpSample sample = result.getHeapDumpSamples(i);
      if (myHasPendingHeapDumpSample) {
        // Note - if there is an existing pending heap dump, the first sample from the response should represent the same sample
        assert i == 0 && sample.getEndTime() != UNFINISHED_HEAP_DUMP_TIMESTAMP;

        MemoryData.HeapDumpSample previousLastSample = myDataCache.swapLastHeapDumpSample(sample);
        assert previousLastSample.getFilePath().equals(sample.getFilePath());
        myHasPendingHeapDumpSample = false;
        pendingPulls.add(sample);
      }
      else {
        myDataCache.appendHeapDumpSample(sample);

        if (sample.getEndTime() == UNFINISHED_HEAP_DUMP_TIMESTAMP) {
          // Note - there should be at most one unfinished heap dump request at a time. e.g. the final sample from the response.
          assert i == result.getHeapDumpSamplesCount() - 1;
          myHasPendingHeapDumpSample = true;
        }
        else {
          pendingPulls.add(sample);
        }
      }
    }

    if (!pendingPulls.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          assert myLogcatHeapDumpFinishedLatch != null;
          //noinspection ConstantConditions
          myLogcatHeapDumpFinishedLatch.await(1, TimeUnit.MINUTES); // TODO Change this to heartbeat-based.
          for (MemoryData.HeapDumpSample sample : pendingPulls) {
            File heapDumpFile = pullHeapDumpFile(sample);
            if (heapDumpFile != null) {
              myDataCache.addPulledHeapDumpFile(sample, heapDumpFile);
            }
          }
        }
        catch (InterruptedException ignored) {}
        finally {
          myLogcatHeapDumpFinishedLatch = null;
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
    if (myLogcatHeapDumpFinishedLatch != null) {
      return false;
    }

    myLogcatHeapDumpFinishedLatch = new CountDownLatch(1);
    myIsListeningForLogcat = true;

    HeapDumpRequest.Builder builder = HeapDumpRequest.newBuilder();
    builder.setAppId(myAppId);
    builder.setRequestTime(myStartTimestampNs);   // Currently not used on perfd.
    myService.getMemoryService().triggerHeapDump(builder.build());
    return true;
  }

  @Nullable
  public File pullHeapDumpFile(@NotNull MemoryData.HeapDumpSample sample) {
    if (!sample.getSuccess()) {
      return null;
    }

    try {
      File tempFile = FileUtil.createTempFile(Long.toString(sample.getEndTime()), ".hprof");
      tempFile.deleteOnExit();
      myDevice.pullFile(sample.getFilePath(), tempFile.getAbsolutePath());
      return tempFile;
    }
    catch (IOException | AdbCommandRejectedException | TimeoutException | SyncException e) {
      LOG.warn("Error pulling '" + sample.getFilePath() + "' from device.", e);
      return null;
    }
  }

  private abstract class MemorySampleAdapter<T> implements DataAdapter<T> {
    @Override
    public int getClosestTimeIndex(long timeUs, boolean leftClosest) {
      return myDataCache.getLatestPriorMemorySampleIndex(TimeUnit.MICROSECONDS.toNanos(timeUs), leftClosest);
    }

    @Override
    public void reset(long deviceStartTimeUs, long studioStartTimeUs) {
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
    public void reset(long deviceStartTimeMs, long studioStartTimeMs) {
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
    public void reset(long deviceStartTimeMs, long studioStartTimeMs) {
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
      long durationUs = sample.getEndTime() == UNFINISHED_HEAP_DUMP_TIMESTAMP ? UNSPECIFIED_DURATION :
                        TimeUnit.NANOSECONDS.toMicros(sample.getEndTime() - sample.getStartTime());
      return new SeriesData<>(startTimeUs, new DurationData(durationUs));
    }
  }
}
