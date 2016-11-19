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

import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profiler.proto.EventServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.RunnableFuture;

/**
 * This class host an EventService that will provide callers access to all cached EventData. The data is populated from polling the service
 * passed into the connectService function.
 */
public class EventDataPoller extends EventServiceGrpc.EventServiceImplBase implements ServicePassThrough, PollRunner.PollingCallback {

  private long myDataRequestStartTimestampNs = Long.MIN_VALUE;
  private EventServiceGrpc.EventServiceBlockingStub myEventPollingService;
  private int myProcessId = -1;

  //TODO: Pull into a storage class that can manage caching data to disk.
  protected PriorityQueue<EventProfiler.EventProfilerData> myData = new PriorityQueue<>(1000, new Comparator<EventProfiler.EventProfilerData>() {
    @Override
    public int compare(EventProfiler.EventProfilerData o1, EventProfiler.EventProfilerData o2) {
      return (int)(o1.getBasicInfo().getEndTimestamp() - o2.getBasicInfo().getEndTimestamp());
    }
  });

  public EventDataPoller() {

  }

  @Override
  public void poll() throws StatusRuntimeException {
    EventProfiler.EventDataRequest.Builder dataRequestBuilder = EventProfiler.EventDataRequest.newBuilder()
      .setAppId(myProcessId)
      .setStartTimestamp(myDataRequestStartTimestampNs)
      .setEndTimestamp(Long.MAX_VALUE);
    EventProfiler.EventDataResponse response = myEventPollingService.getData(dataRequestBuilder.build());

    for (EventProfiler.EventProfilerData data : response.getDataList()) {
      myDataRequestStartTimestampNs = data.getBasicInfo().getEndTimestamp();
      myData.add(data);
    }
  }

  @Override
  public void getData(EventProfiler.EventDataRequest request, StreamObserver<EventProfiler.EventDataResponse> observer) {
    EventProfiler.EventDataResponse.Builder response = EventProfiler.EventDataResponse.newBuilder();
    if(myData.size() == 0) {
      observer.onNext(response.build());
      observer.onCompleted();
      return;
    }

    long startTime = request.getStartTimestamp();
    long endTime = request.getEndTimestamp();

    //TODO: Optimize so we do not need to loop all the data every request, ideally binary search to start time and loop till end.
    Iterator<EventProfiler.EventProfilerData> itr = myData.iterator();
    while(itr.hasNext()) {
      EventProfiler.EventProfilerData obj = itr.next();
      long current = obj.getBasicInfo().getEndTimestamp();
      if (current > startTime && current <= endTime) {
        response.addData(obj);
      }
    }
    observer.onNext(response.build());
    observer.onCompleted();
  }

  @Override
  public void startMonitoringApp(EventProfiler.EventStartRequest request, StreamObserver<EventProfiler.EventStartResponse> observer) {
    myData.clear();
    myProcessId = request.getAppId();
    observer.onNext(myEventPollingService.startMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public void stopMonitoringApp(EventProfiler.EventStopRequest request, StreamObserver<EventProfiler.EventStopResponse> observer) {
    myProcessId = -1;
    observer.onNext(myEventPollingService.stopMonitoringApp(request));
    observer.onCompleted();
  }

  @Override
  public ServerServiceDefinition getService() {
    return bindService();
  }

  @Override
  public void connectService(ManagedChannel channel) {
    myEventPollingService = EventServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public RunnableFuture<Void> getRunner() {
    return new PollRunner(this, PollRunner.POLLING_DELAY_NS);
  }
}
