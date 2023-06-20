/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.faketransport;

import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuDataResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartResponse;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetThreadsResponse;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoResponse;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.EnergyProfiler;
import com.android.tools.profiler.proto.EnergyServiceGrpc;
import com.android.tools.profiler.proto.EventProfiler.ActivityDataResponse;
import com.android.tools.profiler.proto.EventProfiler.EventDataRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStartRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStartResponse;
import com.android.tools.profiler.proto.EventProfiler.EventStopRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStopResponse;
import com.android.tools.profiler.proto.EventProfiler.SystemDataResponse;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartResponse;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkDataRequest;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkDataResponse;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStartRequest;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStartResponse;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStopRequest;
import com.android.tools.profiler.proto.NetworkProfiler.NetworkStopResponse;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.idea.io.grpc.BindableService;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Trace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class FakeGrpcServer extends FakeGrpcChannel {
  /**
   * Mapping from sessions being profiled to the number of profilers that are monitoring it.
   */
  private Map<Long, Integer> myProfiledProcesses;

  private CpuService myCpuService;

  /**
   * A test should use createFakeGrpcServer() to obtain an instance of FakeGrpcServer, not calling the constructor directly.
   */
  private FakeGrpcServer(String name, BindableService... services) {
    super(name, services);
    myProfiledProcesses = new HashMap<>(7);
  }

  /**
   * @return a new instance of FakeGrpcServer ready for a test to use.
   */
  @NotNull
  public static FakeGrpcServer createFakeGrpcServer(String name, BindableService transportService, BindableService profilerService) {
    EventService eventService = new EventService();
    MemoryService memoryService = new MemoryService();
    NetworkService networkService = new NetworkService();
    CpuService cpuService = new CpuService();
    EnergyService energyService = new EnergyService();
    FakeGrpcServer server =
      new FakeGrpcServer(name, transportService, profilerService, eventService, memoryService, networkService, cpuService, energyService);
    // Set the links between the services and the server.
    eventService.myServer = server;
    memoryService.myServer = server;
    networkService.myServer = server;
    cpuService.myServer = server;
    energyService.myServer = server;
    server.myCpuService = cpuService;
    TransportService.setTestChannelName(server.getName());
    return server;
  }

  /**
   * A convenience method for creating a fake GRPC server when you don't care about
   * profiler-specific services.
   *
   * Note: The transport service was refactored out of the profiler service, which is why many
   * original tests use them, but they are not required.
   */
  @NotNull
  public static FakeGrpcServer createFakeGrpcServer(String name, BindableService transportService) {
    return createFakeGrpcServer(name, transportService, transportService);
  }

  /**
   * @return the number of processes currently being profiled.
   */
  public int getProfiledProcessCount() {
    return myProfiledProcesses.keySet().size();
  }

  /**
   * @return the reference to the CPU service.
   */
  public CpuService getCpuService() {
    return myCpuService;
  }

  private synchronized void addProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    int profilerCount = myProfiledProcesses.getOrDefault(sessionId, 0);
    myProfiledProcesses.put(sessionId, profilerCount + 1);
  }

  private synchronized void removeProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    Integer profilerCount = myProfiledProcesses.get(sessionId);
    if (profilerCount != null) {
      if (profilerCount.intValue() > 1) {
        myProfiledProcesses.replace(sessionId, profilerCount.intValue() - 1);
      }
      else {
        myProfiledProcesses.remove(sessionId);
      }
    }
  }

  private static class EventService extends EventServiceGrpc.EventServiceImplBase {
    private FakeGrpcServer myServer;

    @Override
    public void startMonitoringApp(EventStartRequest request, StreamObserver<EventStartResponse> response) {
      myServer.addProfiledProcess(request.getSession());
      response.onNext(EventStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> response) {
      myServer.removeProfiledProcess(request.getSession());
      response.onNext(EventStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getActivityData(EventDataRequest request, StreamObserver<ActivityDataResponse> response) {
      response.onNext(ActivityDataResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getSystemData(EventDataRequest request, StreamObserver<SystemDataResponse> response) {
      response.onNext(SystemDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class MemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
    private FakeGrpcServer myServer;

    @Override
    public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> response) {
      myServer.addProfiledProcess(request.getSession());
      response.onNext(MemoryStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> response) {
      myServer.removeProfiledProcess(request.getSession());
      response.onNext(MemoryStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
      response.onNext(MemoryData.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getJvmtiData(MemoryRequest request, StreamObserver<MemoryData> response) {
      response.onNext(MemoryData.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void listHeapDumpInfos(ListDumpInfosRequest request,
                                  StreamObserver<ListHeapDumpInfosResponse> response) {
      response.onNext(ListHeapDumpInfosResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class NetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {
    private FakeGrpcServer myServer;

    @Override
    public void startMonitoringApp(NetworkStartRequest request, StreamObserver<NetworkStartResponse> response) {
      myServer.addProfiledProcess(request.getSession());
      response.onNext(NetworkStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request, StreamObserver<NetworkStopResponse> response) {
      myServer.removeProfiledProcess(request.getSession());
      response.onNext(NetworkStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> response) {
      response.onNext(NetworkDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  public static class CpuService extends CpuServiceGrpc.CpuServiceImplBase {
    private Trace.TraceConfiguration myTraceConfiguration = Trace.TraceConfiguration.getDefaultInstance();
    private List<Trace.TraceInfo> myTraceInfos = new ArrayList<>();
    private FakeGrpcServer myServer;

    public void addTraceInfo(@NotNull Trace.TraceInfo info) {
      myTraceInfos.add(info);
    }

    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> response) {
      myServer.addProfiledProcess(request.getSession());
      response.onNext(CpuStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> response) {
      myServer.removeProfiledProcess(request.getSession());
      response.onNext(CpuStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getData(CpuDataRequest request, StreamObserver<CpuDataResponse> response) {
      response.onNext(CpuDataResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getThreads(GetThreadsRequest request, StreamObserver<GetThreadsResponse> response) {
      response.onNext(GetThreadsResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getTraceInfo(GetTraceInfoRequest request, StreamObserver<GetTraceInfoResponse> response) {
      response.onNext(GetTraceInfoResponse.newBuilder().addAllTraceInfo(myTraceInfos).build());
      response.onCompleted();
    }
  }

  private static class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase {
    private FakeGrpcServer myServer;

    @Override
    public void startMonitoringApp(EnergyProfiler.EnergyStartRequest request,
                                   StreamObserver<EnergyProfiler.EnergyStartResponse> response) {
      myServer.addProfiledProcess(request.getSession());
      response.onNext(EnergyProfiler.EnergyStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(EnergyProfiler.EnergyStopRequest request,
                                  StreamObserver<EnergyProfiler.EnergyStopResponse> response) {
      myServer.removeProfiledProcess(request.getSession());
      response.onNext(EnergyProfiler.EnergyStopResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getSamples(EnergyProfiler.EnergyRequest request, StreamObserver<EnergyProfiler.EnergySamplesResponse> response) {
      response.onNext(EnergyProfiler.EnergySamplesResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getEvents(EnergyProfiler.EnergyRequest request, StreamObserver<EnergyProfiler.EnergyEventsResponse> response) {
      response.onNext(EnergyProfiler.EnergyEventsResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getEventGroup(EnergyProfiler.EnergyEventGroupRequest request,
                              StreamObserver<EnergyProfiler.EnergyEventsResponse> response) {
      response.onNext(EnergyProfiler.EnergyEventsResponse.getDefaultInstance());
      response.onCompleted();
    }
  }
}
