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
import com.android.tools.datastore.energy.BatteryModel;
import com.android.tools.datastore.energy.PowerProfile;
import com.android.tools.profiler.proto.*;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class hosts an EnergyService that will provide callers access to all cached energy data.
 *
 * NOTE: This poller depends on other services (e.g. CPU, Network). Be sure that those service's
 * pollers get called first.
 */
public final class EnergyDataPoller extends PollRunner {

  @NotNull private final Common.Session mySession;
  @NotNull private final BatteryModel myBatteryModel;
  @NotNull private final EnergyTable myEnergyTable;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyService;
  private long myDataRequestStartTimestampNs;
  @Nullable
  private CpuProfiler.CpuUsageData myLastData = null;

  @NotNull private ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerService;
  @NotNull private CpuServiceGrpc.CpuServiceBlockingStub myCpuService;
  @NotNull private NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkService;

  // TODO: Once we move away from fake data, don't rely on the profilerService anymore
  public EnergyDataPoller(@NotNull Common.Session session,
                          @NotNull BatteryModel batteryModel,
                          @NotNull EnergyTable eventTable,
                          @NotNull ProfilerServiceGrpc.ProfilerServiceBlockingStub profilerService,
                          @NotNull CpuServiceGrpc.CpuServiceBlockingStub cpuService,
                          @NotNull NetworkServiceGrpc.NetworkServiceBlockingStub networkService,
                          @NotNull EnergyServiceGrpc.EnergyServiceBlockingStub energyService) {
    super(POLLING_DELAY_NS);
    myBatteryModel = batteryModel;
    myEnergyTable = eventTable;
    myProfilerService = profilerService;
    myCpuService = cpuService;
    myNetworkService = networkService;
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
    // Network-related samples
    {
      NetworkProfiler.NetworkDataRequest networkDataRequest =
        NetworkProfiler.NetworkDataRequest.newBuilder()
          .setSession(request.getSession())
          .setStartTimestamp(request.getStartTimestamp())
          .setEndTimestamp(request.getEndTimestamp())
          .setType(NetworkProfiler.NetworkDataRequest.Type.ALL)
          .build();

      NetworkProfiler.NetworkDataResponse networkDataResponse = myNetworkService.getData(networkDataRequest);
      for (NetworkProfiler.NetworkProfilerData networkData : networkDataResponse.getDataList()) {
        if (networkData.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.CONNECTIVITY_DATA) {
          myBatteryModel.handleEvent(networkData.getEndTimestamp(),
                                     BatteryModel.Event.NETWORK_TYPE_CHANGED,
                                     PowerProfile.NetworkType.from(networkData.getConnectivityData().getDefaultNetworkType()));
        }
        else if (networkData.getDataCase() == NetworkProfiler.NetworkProfilerData.DataCase.SPEED_DATA) {
          // TODO(b/73487166): We can probably simplify this into one line by converting speedData
          // directly into a single event type ourselves.
          NetworkProfiler.SpeedData speedData = networkData.getSpeedData();
          myBatteryModel.handleEvent(networkData.getEndTimestamp(),
                                     BatteryModel.Event.NETWORK_DOWNLOAD,
                                     speedData.getReceived() > 0);
          myBatteryModel.handleEvent(networkData.getEndTimestamp(),
                                     BatteryModel.Event.NETWORK_UPLOAD,
                                     speedData.getSent() > 0);
        }
      }
    }

    // CPU-related samples
    {
      CpuProfiler.CpuDataRequest cpuDataRequest =
        CpuProfiler.CpuDataRequest.newBuilder()
          .setSession(request.getSession())
          .setStartTimestamp(request.getStartTimestamp())
          .setEndTimestamp(request.getEndTimestamp())
          .build();
      CpuProfiler.CpuDataResponse cpuDataResponse = myCpuService.getData(cpuDataRequest);
      CpuProfiler.CpuUsageData prevUsageData = myLastData;

      for (CpuProfiler.CpuUsageData currUsageData : cpuDataResponse.getDataList()) {
        if (prevUsageData == null) {
          prevUsageData = currUsageData;
          continue;
        }

        double elapsed = (currUsageData.getElapsedTimeInMillisec() - prevUsageData.getElapsedTimeInMillisec());
        double appPercent = (currUsageData.getAppCpuTimeInMillisec() - prevUsageData.getAppCpuTimeInMillisec()) / elapsed;
        myBatteryModel.handleEvent(currUsageData.getEndTimestamp(), BatteryModel.Event.CPU_USAGE, appPercent);
        prevUsageData = currUsageData;
      }
    }

    for (EnergyProfiler.EnergySample sample : myBatteryModel.getSamplesBetween(request.getStartTimestamp(), request.getEndTimestamp())) {
      myEnergyTable.insertOrReplace(mySession, sample);
    }
  }

  private void addLatestEvents(EnergyProfiler.EnergyRequest request) {
    for (EnergyProfiler.EnergyEvent event : myEnergyService.getEvents(request).getEventsList()) {
      myEnergyTable.insertOrReplace(mySession, event);
    }
  }
}
