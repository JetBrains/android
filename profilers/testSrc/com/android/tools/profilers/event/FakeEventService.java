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

import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import com.intellij.util.containers.HashMap;
import io.grpc.stub.StreamObserver;

import java.util.Map;

public final class FakeEventService extends EventServiceGrpc.EventServiceImplBase {
  public static final int FAKE_APP_ID = 1111;
  Map<Long, EventProfiler.SystemData> myEventData = new HashMap<>();
  Map<Long, EventProfiler.ActivityData> myActivityData = new HashMap<>();

  public FakeEventService() {
  }

  public void addSystemEvent(EventProfiler.SystemData data) {
    myEventData.put(data.getEventId(), data);
  }

  public void addActivityEvent(EventProfiler.ActivityData data) {
    myActivityData.put(data.getHash(), data);
  }

  @Override
  public void getActivityData(EventProfiler.EventDataRequest request,
                              StreamObserver<EventProfiler.ActivityDataResponse> responseObserver) {
    EventProfiler.ActivityDataResponse response = EventProfiler.ActivityDataResponse.newBuilder()
      .addAllData(myActivityData.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void getSystemData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.SystemDataResponse> responseObserver) {
    EventProfiler.SystemDataResponse response = EventProfiler.SystemDataResponse.newBuilder()
      .addAllData(myEventData.values())
      .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventProfiler.EventStartRequest request,
                                 StreamObserver<EventProfiler.EventStartResponse> responseObserver) {
    responseObserver.onNext(EventProfiler.EventStartResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void stopMonitoringApp(EventProfiler.EventStopRequest request, StreamObserver<EventProfiler.EventStopResponse> responseObserver) {
    responseObserver.onNext(EventProfiler.EventStopResponse.newBuilder().build());
    responseObserver.onCompleted();
  }
}