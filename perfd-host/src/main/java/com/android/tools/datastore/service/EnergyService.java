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
import com.android.tools.datastore.DeviceId;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.EnergyTable;
import com.android.tools.datastore.poller.EnergyDataPoller;
import com.android.tools.datastore.poller.PollRunner;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyProfiler.*;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase implements ServicePassThrough {

  private final EnergyTable myEnergyTable;

  @NotNull private final DataStoreService myService;
  private final Map<Long, PollRunner> myRunners = new HashMap<>();
  private final Consumer<Runnable> myFetchExecutor;

  public EnergyService(@NotNull DataStoreService service, Consumer<Runnable> fetchExecutor) {
    myService = service;
    myFetchExecutor = fetchExecutor;
    myEnergyTable = new EnergyTable();
  }

  @Override
  public void startMonitoringApp(EnergyProfiler.EnergyStartRequest request,
                                 StreamObserver<EnergyProfiler.EnergyStartResponse> responseObserver) {
    DeviceId deviceId = DeviceId.fromSession(request.getSession());
    EnergyServiceGrpc.EnergyServiceBlockingStub energyClient = myService.getEnergyClient(deviceId);
    ProfilerServiceGrpc.ProfilerServiceBlockingStub profilerClient = myService.getProfilerClient(deviceId);

    if (energyClient != null && profilerClient != null) {
      responseObserver.onNext(energyClient.startMonitoringApp(request));
      responseObserver.onCompleted();
      long sessionId = request.getSession().getSessionId();
      myRunners.put(sessionId, new EnergyDataPoller(request.getSession(), myEnergyTable, profilerClient, energyClient));
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
    EnergyServiceGrpc.EnergyServiceBlockingStub client =
      myService.getEnergyClient(DeviceId.fromSession(request.getSession()));
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
    EnergyProfiler.EnergySamplesResponse.Builder response = EnergyProfiler.EnergySamplesResponse.newBuilder();
    List<EnergySample> samples = myEnergyTable.findSamples(request);
    response.addAllSamples(samples);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getEvents(EnergyRequest request, StreamObserver<EnergyEventsResponse> responseObserver) {
    EnergyProfiler.EnergyEventsResponse.Builder response = EnergyProfiler.EnergyEventsResponse.newBuilder();
    List<EnergyEvent> events = myEnergyTable.findEvents(request);
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
