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
package com.android.tools.idea.profilers.perfd;

import com.android.ddmlib.*;
import com.android.tools.idea.profilers.LegacyCpuTraceProfiler;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A proxy CpuService on host that intercepts grpc requests from perfd-host to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 *
 * In this class, 'profiling' means method-level tracing using either instrumentation or profiling.
 * Within this class 'profiling' is not a general concept as in 'CPU profiler'.
 */
public class CpuServiceProxy extends PerfdProxyService {

  @NotNull private CpuServiceGrpc.CpuServiceBlockingStub myServiceStub;
  @NotNull LegacyCpuTraceProfiler myLegacyProfiler;
  /**
   * True means we use DDMS for method-level tracing (capturing), namely, StartProfilingApp() and
   * StopProfilingApp() APIs in CPU Service.
   */
  private boolean myUseLegacyTracing;

  public CpuServiceProxy(@NotNull IDevice device,
                         @NotNull ManagedChannel channel,
                         @NotNull LegacyCpuTraceProfiler legacyProfiler) {
    super(CpuServiceGrpc.getServiceDescriptor());

    myServiceStub = CpuServiceGrpc.newBlockingStub(channel);
    myLegacyProfiler = legacyProfiler;

    // Use legacy tracing (via JDWP) for devices older than O (API level = 26).
    myUseLegacyTracing = device.getVersion().getFeatureLevel() < 26;
  }

  private void startProfilingApp(CpuProfilingAppStartRequest request,
                                 StreamObserver<CpuProfilingAppStartResponse> responseObserver) {
    CpuProfilingAppStartResponse response;
    if (myUseLegacyTracing) {
      response = myLegacyProfiler.startProfilingApp(request);
    }
    else {
      // Post-O tracing - goes straight to perfd.
      response = myServiceStub.startProfilingApp(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }


  private void stopProfilingApp(CpuProfilingAppStopRequest request,
                                StreamObserver<CpuProfilingAppStopResponse> responseObserver) {
    CpuProfilingAppStopResponse response;
    if (myUseLegacyTracing) {
      response = myLegacyProfiler.stopProfilingApp(request);
    }
    else {
      // Post-O tracing - goes straight to perfd.
      response = myServiceStub.stopProfilingApp(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private void checkAppProfilingState(ProfilingStateRequest request,
                                      StreamObserver<ProfilingStateResponse> responseObserver) {
    ProfilingStateResponse response;
    if (myUseLegacyTracing) {
      response = myLegacyProfiler.checkAppProfilingState(request);
    }
    else {
      // Post-O tracing - goes straight to perfd.
      response = myServiceStub.checkAppProfilingState(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public ServerServiceDefinition getServiceDefinition() {
    Map<MethodDescriptor, ServerCallHandler> overrides = new HashMap<>();
    overrides.put(CpuServiceGrpc.METHOD_START_PROFILING_APP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    startProfilingApp((CpuProfilingAppStartRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(CpuServiceGrpc.METHOD_STOP_PROFILING_APP,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    stopProfilingApp((CpuProfilingAppStopRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(CpuServiceGrpc.METHOD_CHECK_APP_PROFILING_STATE,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    checkAppProfilingState((ProfilingStateRequest)request, (StreamObserver)observer);
                  }));

    return generatePassThroughDefinitions(overrides, myServiceStub);
  }
}
