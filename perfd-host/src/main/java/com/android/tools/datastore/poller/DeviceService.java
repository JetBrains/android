/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.profiler.proto.DeviceServiceGrpc;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.profiler.proto.ProfilerService;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class DeviceService extends DeviceServiceGrpc.DeviceServiceImplBase implements ServicePassThrough  {

  DeviceServiceGrpc.DeviceServiceBlockingStub myPollingService;

  @Override
  public void getTimes(ProfilerService.TimesRequest request, StreamObserver<ProfilerService.TimesResponse> observer) {
    observer.onNext(myPollingService.getTimes(request));
    observer.onCompleted();
  }

  @Override
  public void getVersion(ProfilerService.VersionRequest request, StreamObserver<ProfilerService.VersionResponse> observer) {
    observer.onNext(myPollingService.getVersion(request));
    observer.onCompleted();
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = DeviceServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public RunnableFuture<Void> getRunner() { return null; }
}
