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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.datastore.*;
import com.android.tools.profiler.proto.*;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.Consumer;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class MemoryDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final int TEST_DUMP_ID = 4320;
  private static final int ERROR_TEST_DUMP_ID = 4321;
  private static final int NOT_READY_TEST_DUMP_ID = 4322;
  private static final long BASE_TIME_NS = System.nanoTime();
  private static final long DELAY_TIME_NS = BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1);
  private static final ByteString DUMP_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());

  private static final MemoryProfiler.MemoryData.MemorySample DEFAULT_MEMORY_SAMPLE = MemoryProfiler.MemoryData.MemorySample.newBuilder()
    .setJavaMem(1024)
    .setTimestamp(BASE_TIME_NS)
    .build();

  private static final MemoryProfiler.MemoryData.VmStatsSample DEFAULT_VM_STATS = MemoryProfiler.MemoryData.VmStatsSample.newBuilder()
    .setTimestamp(BASE_TIME_NS)
    .setGcCount(1)
    .build();

  private static final MemoryProfiler.MemoryData.AllocationEvent DEFAULT_ALLOCATION = MemoryProfiler.MemoryData.AllocationEvent.newBuilder()
    .setTimestamp(BASE_TIME_NS)
    .setAllocationStackId(DUMP_DATA)
    .build();

  private static final MemoryProfiler.MemoryData.AllocationsInfo DEFAULT_ALLOCATION_INFO = MemoryProfiler.MemoryData.AllocationsInfo.newBuilder()
    .setStartTime(BASE_TIME_NS)
    .setEndTime(DELAY_TIME_NS)
    .build();
  private static final MemoryProfiler.MemoryData.AllocationsInfo FOLLOW_ALLOCATION_INFO = MemoryProfiler.MemoryData.AllocationsInfo.newBuilder()
    .setStartTime(DELAY_TIME_NS)
    .setEndTime(DurationData.UNSPECIFIED_DURATION)
    .build();


  private MemoryDataPoller myMemoryDataPoller;
  private DataStoreService myDataStore;
  @Rule
  public TestGrpcService<FakeMemoryService> myService = new TestGrpcService<>(new FakeMemoryService());

  @Before
  public void setUp() throws Exception {
    myDataStore = new DataStoreService("fake_service_name");
    myDataStore.setLegacyAllocationTracker(new FakeLegacyAllocationTracker());
    // TODO: Abstract to TestGrpcService
    myMemoryDataPoller = new MemoryDataPoller(myDataStore);
    myMemoryDataPoller.connectService(myService.getChannel());
    myMemoryDataPoller
      .startMonitoringApp(MemoryProfiler.MemoryStartRequest.newBuilder().setAppId(TEST_APP_ID).build(), mock(StreamObserver.class));
    myMemoryDataPoller.poll();
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
  }

  @Test
  public void testGetHeapDumpNotFound() throws Exception {
    getHeapDumpError(0, MemoryProfiler.DumpDataResponse.Status.NOT_FOUND);
  }

  @Test
  public void testGetHeapDumpNotReady() throws Exception {
    getHeapDumpError(NOT_READY_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.NOT_READY);
  }

  @Test
  public void testGetHeapDumpUnknown() throws Exception {
    getHeapDumpError(ERROR_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
  }

  @Test
  public void testGetDataInRange() throws Exception {
    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.newBuilder()
      .addMemSamples(DEFAULT_MEMORY_SAMPLE)
      .addVmStatsSamples(DEFAULT_VM_STATS)
      .addAllocationEvents(DEFAULT_ALLOCATION)
      .addAllocationsInfo(DEFAULT_ALLOCATION_INFO)
      .addAllocationsInfo(FOLLOW_ALLOCATION_INFO)
      .addHeapDumpInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                          .setDumpId(TEST_DUMP_ID)
                          .setEndTime(DELAY_TIME_NS)
                          .build())
      .addHeapDumpInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                          .setDumpId(ERROR_TEST_DUMP_ID)
                          .setEndTime(DELAY_TIME_NS)
                          .build())
      .build();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDataOutOfRange() throws Exception {
    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(0)
      .setStartTime(DELAY_TIME_NS)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.getDefaultInstance();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  public void disabledTestGetDataInvalidAppId() throws Exception {
    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setAppId(0)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.getDefaultInstance();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testTrackAllocations() throws Exception {
    MemoryProfiler.TrackAllocationsRequest request = MemoryProfiler.TrackAllocationsRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setEnabled(true)
      .build();
    MemoryProfiler.TrackAllocationsResponse expected = MemoryProfiler.TrackAllocationsResponse.newBuilder()
      .setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS)
      .build();
    StreamObserver<MemoryProfiler.TrackAllocationsResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.trackAllocations(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListAllocationContexts() throws Exception {
    MemoryProfiler.AllocationContextsRequest request = MemoryProfiler.AllocationContextsRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.AllocationContextsResponse expected = MemoryProfiler.AllocationContextsResponse.getDefaultInstance();
    StreamObserver<MemoryProfiler.AllocationContextsResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.listAllocationContexts(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListHeapDumpInfos() throws Exception {
    MemoryProfiler.ListDumpInfosRequest request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.ListHeapDumpInfosResponse expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                          .setDumpId(TEST_DUMP_ID)
                          .setEndTime(DELAY_TIME_NS)
                          .build())
      .addInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                          .setDumpId(ERROR_TEST_DUMP_ID)
                          .setEndTime(DELAY_TIME_NS)
                          .build())
      .addInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                          .setDumpId(NOT_READY_TEST_DUMP_ID)
                          .setEndTime(DurationData.UNSPECIFIED_DURATION)
                          .build())
      .build();
    StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  public void disabledTestListHeapDumpInfosOutOfRange() throws Exception {
    MemoryProfiler.ListDumpInfosRequest request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setStartTime(DELAY_TIME_NS)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.ListHeapDumpInfosResponse expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                  .setDumpId(NOT_READY_TEST_DUMP_ID)
                  .setEndTime(DurationData.UNSPECIFIED_DURATION)
                  .build())
      .build();
    StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  public void disabledTestListHeapDumpInfosInvlaidAppId() throws Exception {
    MemoryProfiler.ListDumpInfosRequest request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setAppId(0)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.ListHeapDumpInfosResponse expected = MemoryProfiler.ListHeapDumpInfosResponse.getDefaultInstance();
    StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetHeap() throws Exception {
    MemoryProfiler.HeapDumpDataRequest request = MemoryProfiler.HeapDumpDataRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setDumpId(TEST_DUMP_ID)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS)
      .setData(DUMP_DATA)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  private void getHeapDumpError(int id, MemoryProfiler.DumpDataResponse.Status error) {
    MemoryProfiler.HeapDumpDataRequest request = MemoryProfiler.HeapDumpDataRequest.newBuilder()
      .setAppId(TEST_APP_ID)
      .setDumpId(id)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(error)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  private static class FakeLegacyAllocationTracker implements LegacyAllocationTracker {

    @Override
    public boolean setAllocationTrackingEnabled(int processId, boolean enabled) {
      return enabled;
    }

    @Override
    public void getAllocationTrackingDump(int processId, @NotNull ExecutorService executorService, @NotNull Consumer<byte[]> consumer) {

    }

    @NotNull
    @Override
    public LegacyAllocationConverter parseDump(@NotNull byte[] dumpData) {
      return new LegacyAllocationConverter();
    }
  }

  private static class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {

    @Override
    public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request,
                                   StreamObserver<MemoryProfiler.MemoryStartResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.MemoryStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request,
                                  StreamObserver<MemoryProfiler.MemoryStopResponse> responseObserver) {
      super.stopMonitoringApp(request, responseObserver);
    }

    @Override
    public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> responseObserver) {
      MemoryProfiler.MemoryData response = MemoryProfiler.MemoryData.newBuilder()
        .addMemSamples(DEFAULT_MEMORY_SAMPLE)
        .addVmStatsSamples(DEFAULT_VM_STATS)
        .addAllocationEvents(DEFAULT_ALLOCATION)
        .addAllocationsInfo(DEFAULT_ALLOCATION_INFO)
        .addAllocationsInfo(FOLLOW_ALLOCATION_INFO)
        .addHeapDumpInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                            .setDumpId(TEST_DUMP_ID)
                            .setEndTime(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
                            .build())
        .addHeapDumpInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                            .setDumpId(ERROR_TEST_DUMP_ID)
                            .setEndTime(BASE_TIME_NS + TimeUnit.SECONDS.toNanos(1))
                            .build())
        .addHeapDumpInfos(MemoryProfiler.HeapDumpInfo.newBuilder()
                            .setDumpId(NOT_READY_TEST_DUMP_ID)
                            .setEndTime(DurationData.UNSPECIFIED_DURATION)
                            .build())
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }

    @Override
    public void triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest request,
                                StreamObserver<MemoryProfiler.TriggerHeapDumpResponse> responseObserver) {
      super.triggerHeapDump(request, responseObserver);
    }

    @Override
    public void getHeapDump(MemoryProfiler.HeapDumpDataRequest request, StreamObserver<MemoryProfiler.DumpDataResponse> responseObserver) {
      MemoryProfiler.DumpDataResponse.Builder response = MemoryProfiler.DumpDataResponse.newBuilder();
      switch (request.getDumpId()) {
        case TEST_DUMP_ID:
          response.setData(DUMP_DATA);
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
          break;
        case NOT_READY_TEST_DUMP_ID:
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
          break;
        case ERROR_TEST_DUMP_ID:
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
          break;
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void listHeapDumpInfos(MemoryProfiler.ListDumpInfosRequest request,
                                  StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> responseObserver) {
      super.listHeapDumpInfos(request, responseObserver);
    }

    @Override
    public void trackAllocations(MemoryProfiler.TrackAllocationsRequest request,
                                 StreamObserver<MemoryProfiler.TrackAllocationsResponse> responseObserver) {
      MemoryProfiler.TrackAllocationsResponse.Builder response = MemoryProfiler.TrackAllocationsResponse.newBuilder();
      if(request.getEnabled()) {
          response.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS);
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void listAllocationContexts(MemoryProfiler.AllocationContextsRequest request,
                                       StreamObserver<MemoryProfiler.AllocationContextsResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.AllocationContextsResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}