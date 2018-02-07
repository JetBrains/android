/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.database.EnergyTable;
import com.android.tools.profiler.proto.*;
import com.intellij.openapi.util.Pair;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public final class EnergyDataPoller extends PollRunner {

  // TODO: Only used for prototyping. Delete these once we start getting real values from the device.
  private static final long FAKE_TIME_PERIOD_MS = 200;
  private static final RandomData FAKE_CPU_DATA = new RandomData(20);
  private static final RandomData FAKE_NETWORK_DATA = new RandomData(30);
  private static final int FAKE_WAKE_LOCK_DURATION_MS = (int)TimeUnit.SECONDS.toMillis(8);
  public static final int FAKE_WAKE_LOCK_PERIOD_MS = (int)TimeUnit.SECONDS.toMillis(5);

  private long myDataRequestStartTimestampNs;
  private int myNextId = 1;

  @NotNull private final Common.Session mySession;
  @NotNull private final EnergyTable myEnergyTable;
  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerService;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyService;

  // TODO: Once we move away from fake data, don't rely on the profilerService anymore
  public EnergyDataPoller(@NotNull Common.Session session,
                          @NotNull EnergyTable eventTable,
                          @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub profilerService,
                          @NotNull EnergyServiceGrpc.EnergyServiceBlockingStub energyService) {
    super(POLLING_DELAY_NS);
    myEnergyTable = eventTable;
    myProfilerService = profilerService;
    myEnergyService = energyService;
    mySession = session;

    myDataRequestStartTimestampNs = queryCurrentTime();
  }

  // TODO: Remove this temporary function once we're not creating fake data anymore
  private long queryCurrentTime() {
    Profiler.TimeRequest timeRequest = Profiler.TimeRequest.newBuilder().setDeviceId(mySession.getDeviceId()).build();
    return myProfilerService.getCurrentTime(timeRequest).getTimestampNs();
  }

  @Override
  public void poll() throws StatusRuntimeException {
    // TODO: Set endTimestamp with last timestamp from queried data (right now we're creating fake data)
    long endTimestampNs = queryCurrentTime();

    EnergyProfiler.EnergyRequest request = EnergyProfiler.EnergyRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(endTimestampNs) // TODO: Replace with Long.MAX_VALUE when grabbing data from device (see other pollers)
      .build();

    addLatestSamples(request);
    addLatestEvents(request);

    myDataRequestStartTimestampNs = endTimestampNs;
  }

  private void addLatestSamples(EnergyProfiler.EnergyRequest request) {
    Pair<Long, Long> timeRangeMs = getRangeMs(request);
    for (long timeMs = timeRangeMs.getFirst(); timeMs <= timeRangeMs.getSecond(); timeMs += FAKE_TIME_PERIOD_MS) {
      myEnergyTable.insertOrReplace(mySession,
        EnergyProfiler.EnergySample.newBuilder().
          setTimestamp(TimeUnit.MILLISECONDS.toNanos(timeMs)).
          setCpuUsage(FAKE_CPU_DATA.getValue(timeMs)).
          setNetworkUsage(FAKE_NETWORK_DATA.getValue(timeMs)).
          build());
    }
  }

  private void addLatestEvents(EnergyProfiler.EnergyRequest request) {
    Pair<Long, Long> timeRangeMs = getRangeMs(request);

    long deltaMs = timeRangeMs.getSecond() - timeRangeMs.getFirst();
    long groundedFirstMs = timeRangeMs.getFirst() % FAKE_WAKE_LOCK_PERIOD_MS;
    if (groundedFirstMs == 0 || groundedFirstMs + deltaMs > FAKE_WAKE_LOCK_PERIOD_MS) {
      long timeStartNs = TimeUnit.MILLISECONDS.toNanos(timeRangeMs.getFirst());

      Random random = new Random();
      long timeEndNs = timeStartNs + TimeUnit.MILLISECONDS.toNanos(random.nextInt(FAKE_WAKE_LOCK_DURATION_MS) + 1);

      EnergyProfiler.WakeLockAcquired wakeLockAcquired = EnergyProfiler.WakeLockAcquired.newBuilder()
        .setLevel(EnergyProfiler.WakeLockAcquired.Level.SCREEN_DIM_WAKE_LOCK)
        .addFlags(EnergyProfiler.WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
        .setTag(String.valueOf(timeRangeMs.getFirst()))
        .build();

      myEnergyTable.insertOrReplace(mySession,
                                    EnergyProfiler.EnergyEvent.newBuilder()
                                      .setTimestamp(timeStartNs)
                                      .setEventId(myNextId)
                                      .setWakeLockAcquired(wakeLockAcquired)
                                      .build());

      myEnergyTable.insertOrReplace(mySession, EnergyProfiler.EnergyEvent.newBuilder()
        .setTimestamp(timeEndNs)
        .setEventId(myNextId)
        .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance()).build());

      ++myNextId;
    }
  }

  /**
   * Helper function to get fake data time range.
   *
   * TODO: delete when get real energy data from device.
   */
  private static Pair<Long, Long> getRangeMs(EnergyProfiler.EnergyRequest request) {
    // TODO: Get real energy data from device and delete check session range, this adds fake sample within request range.
    long sessionStartTime = request.getSession().getStartTimestamp();
    long sessionEndTime = request.getSession().getEndTimestamp();
    long startTime = Math.max(request.getStartTimestamp(), sessionStartTime > 0 ? sessionStartTime : Long.MAX_VALUE);
    long endTime = Math.min(request.getEndTimestamp(), sessionEndTime > 0 ? sessionEndTime : Long.MAX_VALUE);

    long startTimeMs = TimeUnit.NANOSECONDS.toMillis(startTime);
    long endTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime);

    // Ground the first sample in a consistent way, so the samples returned given a query range
    // will always be the same. For example, with a heartbeat of 20ms...
    // (87 to 123) -> [100, 120]
    // (98 to 135) -> [100, 120]
    long firstSampleMs = (startTimeMs + (FAKE_TIME_PERIOD_MS - startTimeMs % FAKE_TIME_PERIOD_MS));
    return Pair.create(firstSampleMs, endTimeMs);
  }

  // TODO: Delete after we get real data from the target device
  private static final class RandomData {
    private static final int NUM_VALUES = 1000;
    private final int[] values = new int[NUM_VALUES];

    /**
     * Generates a bunch of random data samples, between 0 and {@code bound}.
     */
    public RandomData(int bound) {
      Random random = new Random();
      for (int i = 0; i < NUM_VALUES; i++) {
        values[i] = random.nextInt(bound);
      }
    }

    public int getValue(long timeMs) {
      // One data sample per tenth of a second. This allows 1000 unique values to spread across 100
      // seconds before the pattern repeats.
      return values[(int)((timeMs / 100) % NUM_VALUES)];
    }
  }

}
