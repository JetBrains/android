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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.*;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.EventProfiler.*;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.NetworkProfiler.*;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

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
  public static FakeGrpcServer createFakeGrpcServer(String name, BindableService service) {
    EventService eventService = new EventService();
    MemoryService memoryService = new MemoryService();
    NetworkService networkService = new NetworkService();
    CpuService cpuService = new CpuService();
    EnergyService energyService = new EnergyService();
    FakeGrpcServer server = new FakeGrpcServer(name, service, eventService, memoryService, networkService, cpuService, energyService);
    // Set the links between the services and the server.
    eventService.myServer = server;
    memoryService.myServer = server;
    networkService.myServer = server;
    cpuService.myServer = server;
    energyService.myServer = server;
    server.myCpuService = cpuService;
    return server;
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
      response.onNext(EventStartResponse.getDefaultInstance());
      response.onCompleted();
      myServer.addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> response) {
      response.onNext(EventStopResponse.getDefaultInstance());
      response.onCompleted();
      myServer.removeProfiledProcess(request.getSession());
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
      response.onNext(MemoryStartResponse.getDefaultInstance());
      response.onCompleted();
      myServer.addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> response) {
      response.onNext(MemoryStopResponse.getDefaultInstance());
      response.onCompleted();
      myServer.removeProfiledProcess(request.getSession());
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
      response.onNext(NetworkStartResponse.getDefaultInstance());
      response.onCompleted();
      myServer.addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request, StreamObserver<NetworkStopResponse> response) {
      response.onNext(NetworkStopResponse.getDefaultInstance());
      response.onCompleted();
      myServer.removeProfiledProcess(request.getSession());
    }

    @Override
    public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> response) {
      response.onNext(NetworkDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  public static class CpuService extends CpuServiceGrpc.CpuServiceImplBase {
    private boolean myIsBeingProfiled = false;
    private boolean myIsStartupProfiling = false;
    private long myProfilingStartTimestamp = 0;

    private FakeGrpcServer myServer;

    public void setStartupProfiling(boolean isStartupProfiling) {
      myIsStartupProfiling = isStartupProfiling;
      if (isStartupProfiling) {
        // if startup profiling is true, it means that an app is being profiled
        myIsBeingProfiled = true;
      }
    }

    public void setProfilingStartTimestamp(long timestamp) {
      myProfilingStartTimestamp = timestamp;
    }

    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> response) {
      response.onNext(CpuStartResponse.getDefaultInstance());
      response.onCompleted();
      myServer.addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> response) {
      response.onNext(CpuStopResponse.getDefaultInstance());
      response.onCompleted();
      myServer.removeProfiledProcess(request.getSession());
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
      response.onNext(GetTraceInfoResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void checkAppProfilingState(ProfilingStateRequest request,
                                       StreamObserver<ProfilingStateResponse> response) {
      response.onNext(
        ProfilingStateResponse.newBuilder()
          .setBeingProfiled(myIsBeingProfiled)
          .setIsStartupProfiling(myIsStartupProfiling)
          .setStartTimestamp(myProfilingStartTimestamp).build());
      response.onCompleted();
    }
  }

  private static class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase {
    private FakeGrpcServer myServer;

    @Override
    public void startMonitoringApp(EnergyProfiler.EnergyStartRequest request,
                                   StreamObserver<EnergyProfiler.EnergyStartResponse> response) {
      response.onNext(EnergyProfiler.EnergyStartResponse.getDefaultInstance());
      response.onCompleted();
      myServer.addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(EnergyProfiler.EnergyStopRequest request,
                                  StreamObserver<EnergyProfiler.EnergyStopResponse> response) {
      response.onNext(EnergyProfiler.EnergyStopResponse.getDefaultInstance());
      response.onCompleted();
      myServer.removeProfiledProcess(request.getSession());
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
    public void getEventGroup(EnergyProfiler.EnergyEventGroupRequest request, StreamObserver<EnergyProfiler.EnergyEventsResponse> response) {
      response.onNext(EnergyProfiler.EnergyEventsResponse.getDefaultInstance());
      response.onCompleted();
    }
  }
}
