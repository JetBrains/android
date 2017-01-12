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

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.database.DatastoreTable;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * This class hosts an EventService that will provide callers access to all cached EventData.
 * The data is populated from polling the service passed into the connectService function.
 */
public class ProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase implements ServicePassThrough {

  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myPollingService;

  public ProfilerService() {
  }

  @Override
  public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    if (myPollingService != null) {
      observer.onNext(myPollingService.getTimes(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> observer) {
    if (myPollingService != null) {
      observer.onNext(myPollingService.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> observer) {
    if (myPollingService != null) {
      observer.onNext(myPollingService.getDevices(request));
    }

    observer.onCompleted();
  }

  @Override
  public void getBytes(Profiler.BytesRequest request, StreamObserver<Profiler.BytesResponse> responseObserver) {
    // TODO: This should check a local cache of files (either in a database or on disk) before
    // making the request against the device
    responseObserver.onNext(myPollingService.getBytes(request));
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(Profiler.GetProcessesRequest request, StreamObserver<Profiler.GetProcessesResponse> observer) {
    if (myPollingService != null) {
      observer.onNext(myPollingService.getProcesses(request));
    }

    observer.onCompleted();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myPollingService = ProfilerServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public DatastoreTable getDatastoreTable() {
    return null;
  }
}
