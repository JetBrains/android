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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LogService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.EnergyTable;
import com.android.tools.datastore.energy.BatteryModel;
import com.android.tools.datastore.poller.EnergyDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventGroupRequest;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEventsResponse;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyRequest;
import com.android.tools.profiler.proto.EnergyProfiler.EnergySample;
import com.android.tools.profiler.proto.EnergyProfiler.EnergySamplesResponse;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase implements ServicePassThrough {

  private final BatteryModel myBatteryModel;
  private final EnergyTable myEnergyTable;

  @NotNull private final DataStoreService myService;
  private final Map<Long, PollRunner> myRunners = new HashMap<>();
  private final Consumer<Runnable> myFetchExecutor;
  @NotNull private final LogService myLogService;

  @SuppressWarnings("unchecked")
  private ResponseData<EnergySamplesResponse> myLastSamplesResponse = ResponseData.createEmpty();
  @SuppressWarnings("unchecked")
  private ResponseData<EnergyEventsResponse> myLastEventsResponse = ResponseData.createEmpty();

  public EnergyService(@NotNull DataStoreService service, @NotNull Consumer<Runnable> fetchExecutor, @NotNull LogService logService) {
    this(new BatteryModel(), service, fetchExecutor, logService);
  }

  @VisibleForTesting
  public EnergyService(@NotNull BatteryModel batteryModel, @NotNull DataStoreService service, Consumer<Runnable> fetchExecutor,
                       @NotNull LogService logService) {
    myBatteryModel = batteryModel;
    myService = service;
    myFetchExecutor = fetchExecutor;
    myLogService = logService;
    myEnergyTable = new EnergyTable();
  }

  @Override
  public void startMonitoringApp(EnergyProfiler.EnergyStartRequest request,
                                 StreamObserver<EnergyProfiler.EnergyStartResponse> responseObserver) {
    long streamId = request.getSession().getStreamId();
    EnergyServiceGrpc.EnergyServiceBlockingStub energyClient = myService.getEnergyClient(streamId);
    CpuServiceGrpc.CpuServiceBlockingStub cpuClient = myService.getCpuClient(streamId);
    NetworkServiceGrpc.NetworkServiceBlockingStub networkClient = myService.getNetworkClient(streamId);
    TransportServiceGrpc.TransportServiceBlockingStub transportClient = myService.getTransportClient(streamId);

    if (energyClient != null && transportClient != null) {
      responseObserver.onNext(energyClient.startMonitoringApp(request));
      responseObserver.onCompleted();
      long sessionId = request.getSession().getSessionId();
      myRunners
        .put(sessionId, new EnergyDataPoller(request.getSession(), myBatteryModel, myEnergyTable, transportClient, cpuClient, networkClient,
                                             energyClient, myLogService));
      myFetchExecutor.accept(myRunners.get(sessionId));
    }
    else {
      responseObserver.onNext(EnergyProfiler.EnergyStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }

  @Override
  public void stopMonitoringApp(EnergyProfiler.EnergyStopRequest request,
                                StreamObserver<EnergyProfiler.EnergyStopResponse> responseObserver) {
    long sessionId = request.getSession().getSessionId();
    PollRunner runner = myRunners.remove(sessionId);
    if (runner != null) {
      runner.stop();
    }
    // Our polling service can get shutdown if we unplug the device.
    // This should be the only function that gets called as StudioProfilers attempts
    // to stop monitoring the last app it was monitoring.
    EnergyServiceGrpc.EnergyServiceBlockingStub client = myService.getEnergyClient(request.getSession().getStreamId());
    if (client == null) {
      responseObserver.onNext(EnergyProfiler.EnergyStopResponse.getDefaultInstance());
    }
    else {
      responseObserver.onNext(client.stopMonitoringApp(request));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getSamples(EnergyRequest request, StreamObserver<EnergySamplesResponse> responseObserver) {
    if (!myLastSamplesResponse.matches(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      EnergyProfiler.EnergySamplesResponse.Builder response = EnergyProfiler.EnergySamplesResponse.newBuilder();
      List<EnergySample> samples = myEnergyTable.getSamples(request);
      response.addAllSamples(samples);
      myLastSamplesResponse =
        new ResponseData<>(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp(), response.build());
    }
    responseObserver.onNext(myLastSamplesResponse.getResponse());
    responseObserver.onCompleted();
  }

  @Override
  public void getEvents(EnergyRequest request, StreamObserver<EnergyEventsResponse> responseObserver) {
    if (!myLastEventsResponse.matches(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp())) {
      EnergyProfiler.EnergyEventsResponse.Builder response = EnergyProfiler.EnergyEventsResponse.newBuilder();
      List<EnergyEvent> events = myEnergyTable.getEvents(request);
      response.addAllEvents(events);
      myLastEventsResponse =
        new ResponseData<>(request.getSession(), request.getStartTimestamp(), request.getEndTimestamp(), response.build());
    }
    responseObserver.onNext(myLastEventsResponse.getResponse());
    responseObserver.onCompleted();
  }

  @Override
  public void getEventGroup(EnergyEventGroupRequest request, StreamObserver<EnergyEventsResponse> responseObserver) {
    EnergyProfiler.EnergyEventsResponse.Builder response = EnergyProfiler.EnergyEventsResponse.newBuilder();
    List<EnergyEvent> events = myEnergyTable.getEventGroup(request);
    response.addAllEvents(events);
    responseObserver.onNext(response.build());
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
    myEnergyTable.initialize(connection);
  }
}
