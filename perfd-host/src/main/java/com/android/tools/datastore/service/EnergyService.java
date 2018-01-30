/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyRequest;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyDataResponse;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TODO: Set real data with Energy data table.
 */
public class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase implements ServicePassThrough {

  // TODO: Only used for prototyping. Delete these once we start getting real values from the device.
  private static final long FAKE_TIME_PERIOD_MS = 200;
  private static final RandomData FAKE_CPU_DATA = new RandomData(20);
  private static final RandomData FAKE_NETWORK_DATA = new RandomData(30);

  @NotNull private final DataStoreService myService;
  private final Consumer<Runnable> myExecutor;

  public EnergyService(@NotNull DataStoreService service, Consumer<Runnable> fetchExecutor) {
    myService = service;
    myExecutor = fetchExecutor;
  }

  @Override
  public void getData(EnergyRequest request, StreamObserver<EnergyDataResponse> responseObserver) {
    EnergyDataResponse.Builder responseBuilder = EnergyDataResponse.newBuilder();

    // TODO: Get real energy data from device and delete check session range, this adds fake sample within request range.
    long sessionStartTime = request.getSession().getStartTimestamp();
    long sessionEndTime = request.getSession().getEndTimestamp();
    long startTime = Math.max(request.getStartTimestamp() + 1, sessionStartTime > 0 ? sessionStartTime : Long.MAX_VALUE);
    long endTime = Math.min(request.getEndTimestamp(), sessionEndTime > 0 ? sessionEndTime : Long.MAX_VALUE);

    long startTimeMs = TimeUnit.NANOSECONDS.toMillis(startTime);
    long endTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime);

    // Ground the first sample in a consistent way, so the samples returned given a query range
    // will always be the same. For example, with a heartbeat of 20ms...
    // (87 to 123) -> [100, 120]
    // (98 to 135) -> [100, 120]
    long firstSampleMs = (startTimeMs + (FAKE_TIME_PERIOD_MS - startTimeMs % FAKE_TIME_PERIOD_MS));
    for (long timeMs = firstSampleMs; timeMs <= endTimeMs; timeMs += FAKE_TIME_PERIOD_MS) {
      responseBuilder.addSampleData(
        EnergyDataResponse.EnergySample.newBuilder().
          setTimestamp(TimeUnit.MILLISECONDS.toNanos(timeMs)).
          setCpuUsage(FAKE_CPU_DATA.getValue(timeMs)).
          setNetworkUsage(FAKE_NETWORK_DATA.getValue(timeMs)).
          build());
    }
    responseObserver.onNext(responseBuilder.build());
    responseObserver.onCompleted();
  }

  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
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
