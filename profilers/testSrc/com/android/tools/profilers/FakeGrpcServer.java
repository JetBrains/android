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
   *
   * It is better to be a non-static variable. But that requires changing the nested service classes
   * defined in this class to be either non-static or to take a FakeGrpcServer parameter in constructors.
   * Neither approach is very clean; not worth it.
   */
  private static Map<Long, Integer> ourProfiledProcesses = new HashMap<>(7);

  public FakeGrpcServer(String name, BindableService service) {
    super(name, service,
          new EventService(),
          new MemoryService(),
          new NetworkService(),
          new CpuService(),
          new EnergyService());
    // TODO the current static map keeps around values from previous tests. We should refactor this properly so this doesn't have to be
    // a static.
    ourProfiledProcesses.clear();
  }

  /**
   * @return the number of processes currently being profiled.
   */
  public int getProfiledProcessCount() {
    return ourProfiledProcesses.keySet().size();
  }

  private synchronized static void addProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    int profilerCount = ourProfiledProcesses.getOrDefault(sessionId, 0);
    ourProfiledProcesses.put(sessionId, profilerCount + 1);
  }

  private synchronized static void removeProfiledProcess(Common.Session session) {
    long sessionId = session.getSessionId();
    Integer profilerCount = ourProfiledProcesses.get(sessionId);
    if (profilerCount != null) {
      if (profilerCount.intValue() > 1) {
        ourProfiledProcesses.replace(sessionId, profilerCount.intValue() - 1);
      }
      else {
        ourProfiledProcesses.remove(sessionId);
      }
    }
  }

  private static class EventService extends EventServiceGrpc.EventServiceImplBase {
    @Override
    public void startMonitoringApp(EventStartRequest request, StreamObserver<EventStartResponse> response) {
      response.onNext(EventStartResponse.getDefaultInstance());
      response.onCompleted();
      addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> response) {
      response.onNext(EventStopResponse.getDefaultInstance());
      response.onCompleted();
      removeProfiledProcess(request.getSession());
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
    @Override
    public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> response) {
      response.onNext(MemoryStartResponse.getDefaultInstance());
      response.onCompleted();
      addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> response) {
      response.onNext(MemoryStopResponse.getDefaultInstance());
      response.onCompleted();
      removeProfiledProcess(request.getSession());
    }

    @Override
    public void getData(MemoryRequest request, StreamObserver<MemoryData> response) {
      response.onNext(MemoryData.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class NetworkService extends NetworkServiceGrpc.NetworkServiceImplBase {
    @Override
    public void startMonitoringApp(NetworkStartRequest request, StreamObserver<NetworkStartResponse> response) {
      response.onNext(NetworkStartResponse.getDefaultInstance());
      response.onCompleted();
      addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request, StreamObserver<NetworkStopResponse> response) {
      response.onNext(NetworkStopResponse.getDefaultInstance());
      response.onCompleted();
      removeProfiledProcess(request.getSession());
    }

    @Override
    public void getData(NetworkDataRequest request, StreamObserver<NetworkDataResponse> response) {
      response.onNext(NetworkDataResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class CpuService extends CpuServiceGrpc.CpuServiceImplBase {
    @Override
    public void startMonitoringApp(CpuStartRequest request, StreamObserver<CpuStartResponse> response) {
      response.onNext(CpuStartResponse.getDefaultInstance());
      response.onCompleted();
      addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> response) {
      response.onNext(CpuStopResponse.getDefaultInstance());
      response.onCompleted();
      removeProfiledProcess(request.getSession());
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
      response.onNext(ProfilingStateResponse.getDefaultInstance());
      response.onCompleted();
    }
  }

  private static class EnergyService extends EnergyServiceGrpc.EnergyServiceImplBase {
    @Override
    public void startMonitoringApp(EnergyProfiler.EnergyStartRequest request,
                                   StreamObserver<EnergyProfiler.EnergyStartResponse> response) {
      response.onNext(EnergyProfiler.EnergyStartResponse.getDefaultInstance());
      response.onCompleted();
      addProfiledProcess(request.getSession());
    }

    @Override
    public void stopMonitoringApp(EnergyProfiler.EnergyStopRequest request,
                                  StreamObserver<EnergyProfiler.EnergyStopResponse> response) {
      response.onNext(EnergyProfiler.EnergyStopResponse.getDefaultInstance());
      response.onCompleted();
      removeProfiledProcess(request.getSession());
    }

    @Override
    public void getData(EnergyProfiler.EnergyRequest request, StreamObserver<EnergyProfiler.EnergyDataResponse> response) {
      response.onNext(EnergyProfiler.EnergyDataResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void getEvents(EnergyProfiler.EnergyRequest request, StreamObserver<EnergyProfiler.EnergyEventsResponse> response) {
      response.onNext(EnergyProfiler.EnergyEventsResponse.getDefaultInstance());
      response.onCompleted();
    }
  }
}
