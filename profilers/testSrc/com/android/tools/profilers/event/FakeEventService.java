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
package com.android.tools.profilers.event;

import com.android.tools.profiler.proto.EventProfiler.*;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.android.tools.idea.io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;

public final class FakeEventService extends EventServiceGrpc.EventServiceImplBase {
  Map<Long, SystemData> myEventData = new HashMap<>();
  Map<Long, ActivityData> myActivityData = new HashMap<>();

  public FakeEventService() {
  }

  public void addSystemEvent(SystemData data) {
    myEventData.put(data.getEventId(), data);
  }

  public void clearSystemEvents() {
    myEventData.clear();
  }

  public void addActivityEvent(ActivityData data) {
    myActivityData.put(data.getHash(), data);
  }

  @Override
  public void getActivityData(EventDataRequest request,
                              StreamObserver<ActivityDataResponse> responseObserver) {
    ActivityDataResponse response = ActivityDataResponse.newBuilder()
      .addAllData(myActivityData.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getSystemData(EventDataRequest request, StreamObserver<SystemDataResponse> responseObserver) {
    SystemDataResponse response = SystemDataResponse.newBuilder()
      .addAllData(myEventData.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventStartRequest request,
                                 StreamObserver<EventStartResponse> responseObserver) {
    responseObserver.onNext(EventStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(EventStopRequest request, StreamObserver<EventStopResponse> responseObserver) {
    responseObserver.onNext(EventStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}