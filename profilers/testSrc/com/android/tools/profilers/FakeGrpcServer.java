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

public class FakeGrpcServer extends FakeGrpcChannel {
  public FakeGrpcServer(String name, BindableService service) {
    super(name, service, new EventService(),
          new MemoryService(),
          new NetworkService(),
          new CpuService());
  }

  private static class EventService extends EventServiceGrpc.EventServiceImplBase {
    @Override
    public void startMonitoringApp(EventStartRequest request, StreamObserver<EventStartResponse> response) {
      response.onNext(EventStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> response) {
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
    @Override
    public void startMonitoringApp(MemoryStartRequest request, StreamObserver<MemoryStartResponse> response) {
      response.onNext(MemoryStartResponse.getDefaultInstance());
      response.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request, StreamObserver<MemoryStopResponse> response) {
      response.onNext(MemoryStopResponse.getDefaultInstance());
      response.onCompleted();
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
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request, StreamObserver<NetworkStopResponse> response) {
      response.onNext(NetworkStopResponse.getDefaultInstance());
      response.onCompleted();
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
    }

    @Override
    public void stopMonitoringApp(CpuStopRequest request, StreamObserver<CpuStopResponse> response) {
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
    public void checkAppProfilingState(ProfilingStateRequest request,
                                       StreamObserver<ProfilingStateResponse> response) {
      response.onNext(ProfilingStateResponse.getDefaultInstance());
      response.onCompleted();
    }
  }
}
