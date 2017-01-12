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
import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.MemoryService;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;

public class MemoryDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final int TEST_DUMP_ID = 4320;
  private static final int ERROR_TEST_DUMP_ID = 4321;
  private static final int NOT_READY_TEST_DUMP_ID = 4322;
  private static final int FINISHED_ALLOC_INFO_ID = 1;
  private static final int IN_PROGRESS_ALLOCATION_INFO_ID = 2;
  private static final long BASE_TIME_NS = System.nanoTime();
  private static final ByteString DUMP_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());

  private static final MemoryProfiler.MemoryData.MemorySample DEFAULT_MEMORY_SAMPLE = MemoryProfiler.MemoryData.MemorySample.newBuilder()
    .setJavaMem(1024)
    .setTimestamp(BASE_TIME_NS)
    .build();

  private static final MemoryProfiler.MemoryData.VmStatsSample DEFAULT_VM_STATS = MemoryProfiler.MemoryData.VmStatsSample.newBuilder()
    .setTimestamp(BASE_TIME_NS)
    .setGcCount(1)
    .build();

  private static final MemoryProfiler.AllocationsInfo FINISHED_ALLOCATION_INFO = MemoryProfiler.AllocationsInfo.newBuilder()
    .setInfoId(FINISHED_ALLOC_INFO_ID)
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .build();

  private static final MemoryProfiler.AllocationsInfo IN_PROGRESS_ALLOCATION_INFO = MemoryProfiler.AllocationsInfo.newBuilder()
    .setInfoId(IN_PROGRESS_ALLOCATION_INFO_ID)
    .setStartTime(delayTimeFromBase(1))
    .setEndTime(DurationData.UNSPECIFIED_DURATION)
    .build();

  private static final MemoryProfiler.HeapDumpInfo DEFAULT_DUMP_INFO = MemoryProfiler.HeapDumpInfo.newBuilder()
    .setDumpId(TEST_DUMP_ID)
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .build();

  private static final MemoryProfiler.HeapDumpInfo ERROR_DUMP_INFO = MemoryProfiler.HeapDumpInfo.newBuilder()
    .setDumpId(ERROR_TEST_DUMP_ID)
    .setStartTime(delayTimeFromBase(1))
    .setEndTime(delayTimeFromBase(2))
    .build();

  private static final MemoryProfiler.HeapDumpInfo NOT_READY_DUMP_INFO = MemoryProfiler.HeapDumpInfo.newBuilder()
    .setDumpId(NOT_READY_TEST_DUMP_ID)
    .setStartTime(delayTimeFromBase(2))
    .setEndTime(DurationData.UNSPECIFIED_DURATION)
    .build();

  private DataStoreService myDataStore = mock(DataStoreService.class);
  private MemoryService myMemoryService = new MemoryService(getPollTicker()::run);
  private FakeMemoryService myFakeMemoryService = new FakeMemoryService();

  @Rule
  public TestGrpcService<FakeMemoryService> myService = new TestGrpcService<>(myMemoryService, myFakeMemoryService);

  @Before
  public void setUp() throws Exception {
    // TODO: Abstract to TestGrpcService
    myMemoryService.connectService(myService.getChannel());
    startMonitoringApp();
    getPollTicker().run();
  }

  private void startMonitoringApp() {
    myMemoryService
      .startMonitoringApp(MemoryProfiler.MemoryStartRequest.newBuilder().setProcessId(TEST_APP_ID).build(), mock(StreamObserver.class));

    // First trigger heap dumps on the MemoryDataPoller to create the latching mechanism which both GetData and GetHeapDump depend on.
    myFakeMemoryService.setTriggerHeapDumpInfo(DEFAULT_DUMP_INFO);
    myMemoryService.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
    myFakeMemoryService.setTriggerHeapDumpInfo(ERROR_DUMP_INFO);
    myMemoryService.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
    myFakeMemoryService.setTriggerHeapDumpInfo(NOT_READY_DUMP_INFO);
    myMemoryService.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
    myMemoryService
      .stopMonitoringApp(MemoryProfiler.MemoryStopRequest.newBuilder().setProcessId(TEST_APP_ID).build(), mock(StreamObserver.class));
  }

  @Test
  public void testGetDataInRange() throws Exception {
    myFakeMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myFakeMemoryService.addVmStatsSample(DEFAULT_VM_STATS);
    myFakeMemoryService.addAllocationInfo(FINISHED_ALLOCATION_INFO);
    myFakeMemoryService.addAllocationInfo(IN_PROGRESS_ALLOCATION_INFO);
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.newBuilder()
      .addMemSamples(DEFAULT_MEMORY_SAMPLE)
      .addVmStatsSamples(DEFAULT_VM_STATS)
      .addAllocationsInfo(FINISHED_ALLOCATION_INFO)
      .addAllocationsInfo(IN_PROGRESS_ALLOCATION_INFO)
      .addHeapDumpInfos(DEFAULT_DUMP_INFO)
      .addHeapDumpInfos(ERROR_DUMP_INFO)
      .addHeapDumpInfos(NOT_READY_DUMP_INFO)
      .build();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDataOutOfRange() throws Exception {
    myFakeMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myFakeMemoryService.addVmStatsSample(DEFAULT_VM_STATS);
    myFakeMemoryService.addAllocationInfo(FINISHED_ALLOCATION_INFO);
    myFakeMemoryService.addAllocationInfo(IN_PROGRESS_ALLOCATION_INFO);
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(0)
      .setStartTime(BASE_TIME_NS)
      .setEndTime(delayTimeFromBase(-2))
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.getDefaultInstance();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  @Ignore
  public void testGetDataInvalidAppId() throws Exception {
    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(0)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.getDefaultInstance();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testTrackAllocations() throws Exception {
    MemoryProfiler.TrackAllocationsRequest request = MemoryProfiler.TrackAllocationsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setEnabled(true)
      .build();
    MemoryProfiler.TrackAllocationsResponse expected = MemoryProfiler.TrackAllocationsResponse.newBuilder()
      .setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS)
      .build();
    StreamObserver<MemoryProfiler.TrackAllocationsResponse> observer = mock(StreamObserver.class);
    myMemoryService.trackAllocations(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListHeapDumpInfos() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.ListDumpInfosRequest request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(BASE_TIME_NS)
      .build();
    MemoryProfiler.ListHeapDumpInfosResponse expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(DEFAULT_DUMP_INFO)
      .build();
    StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);

    request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(BASE_TIME_NS)
      .setEndTime(delayTimeFromBase(1))
      .build();
    expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(DEFAULT_DUMP_INFO)
      .addInfos(ERROR_DUMP_INFO)
      .build();
    observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);

    request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(delayTimeFromBase(1))
      .setEndTime(delayTimeFromBase(2))
      .build();
    expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(ERROR_DUMP_INFO)
      .addInfos(NOT_READY_DUMP_INFO)
      .build();
    observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListHeapDumpInfosOutOfRange() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.ListDumpInfosRequest request = MemoryProfiler.ListDumpInfosRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(delayTimeFromBase(2))
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.ListHeapDumpInfosResponse expected = MemoryProfiler.ListHeapDumpInfosResponse.newBuilder()
      .addInfos(NOT_READY_DUMP_INFO)
      .build();
    StreamObserver<MemoryProfiler.ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetHeapDumpNotFound() throws Exception {
    getHeapDumpError(0, MemoryProfiler.DumpDataResponse.Status.NOT_FOUND);
  }

  @Test
  public void testGetHeapDumpNotReady() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(NOT_READY_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.NOT_READY);
  }

  @Test
  public void testGetHeapDumpUnknown() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(ERROR_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
  }

  @Test
  public void testGetHeapDump() throws Exception {
    // First adds and polls the HeapDumpInfo sample we are testing, which counts down the latch
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.DumpDataRequest request = MemoryProfiler.DumpDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setDumpId(TEST_DUMP_ID)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS)
      .setData(DUMP_DATA)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetAllocationDump() {
    myFakeMemoryService.addAllocationInfo(FINISHED_ALLOCATION_INFO);
    getPollTicker().run();

    MemoryProfiler.DumpDataRequest request = MemoryProfiler.DumpDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setDumpId(FINISHED_ALLOC_INFO_ID)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS)
      .setData(DUMP_DATA)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getAllocationDump(request, observer);
    validateResponse(observer, expected);
  }

  private void getHeapDumpError(int id, MemoryProfiler.DumpDataResponse.Status error) {
    MemoryProfiler.DumpDataRequest request = MemoryProfiler.DumpDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setDumpId(id)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(error)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  private static long delayTimeFromBase(int numSec) {
    return BASE_TIME_NS + TimeUnit.SECONDS.toNanos(numSec);
  }

  // TODO look into merging this with MockMemoryService from profilers module.
  private static class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
    private MemoryProfiler.MemoryData.Builder myResponseBuilder = MemoryProfiler.MemoryData.newBuilder();
    private MemoryProfiler.AllocationsInfo myAllocationsInfo;
    private MemoryProfiler.HeapDumpInfo myTriggerHeapDumpInfo;
    private ByteString myAllocationDump;

    @Override
    public void startMonitoringApp(MemoryProfiler.MemoryStartRequest request,
                                   StreamObserver<MemoryProfiler.MemoryStartResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.MemoryStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryProfiler.MemoryStopRequest request,
                                  StreamObserver<MemoryProfiler.MemoryStopResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.MemoryStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getData(MemoryProfiler.MemoryRequest request, StreamObserver<MemoryProfiler.MemoryData> responseObserver) {
      responseObserver.onNext(myResponseBuilder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest request,
                                StreamObserver<MemoryProfiler.TriggerHeapDumpResponse> responseObserver) {
      MemoryProfiler.TriggerHeapDumpResponse.Builder builder = MemoryProfiler.TriggerHeapDumpResponse.newBuilder();
      builder.setStatus(MemoryProfiler.TriggerHeapDumpResponse.Status.SUCCESS);
      if (myTriggerHeapDumpInfo != null) {
        builder.setInfo(myTriggerHeapDumpInfo);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getHeapDump(MemoryProfiler.DumpDataRequest request, StreamObserver<MemoryProfiler.DumpDataResponse> responseObserver) {
      MemoryProfiler.DumpDataResponse.Builder response = MemoryProfiler.DumpDataResponse.newBuilder();
      switch (request.getDumpId()) {
        case TEST_DUMP_ID:
          response.setData(DUMP_DATA);
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
          break;
        case NOT_READY_TEST_DUMP_ID:
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.NOT_READY);
          break;
        case ERROR_TEST_DUMP_ID:
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
          break;
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getAllocationDump(MemoryProfiler.DumpDataRequest request,
                                  StreamObserver<MemoryProfiler.DumpDataResponse> responseObserver) {
      MemoryProfiler.DumpDataResponse.Builder response = MemoryProfiler.DumpDataResponse.newBuilder();
      switch (request.getDumpId()) {
        case FINISHED_ALLOC_INFO_ID:
          response.setData(DUMP_DATA);
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.SUCCESS);
          break;
        case IN_PROGRESS_ALLOCATION_INFO_ID:
          response.setStatus(MemoryProfiler.DumpDataResponse.Status.NOT_READY);
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
      response.setStatus(MemoryProfiler.TrackAllocationsResponse.Status.SUCCESS);
      if (myAllocationsInfo != null) {
        response.setInfo(myAllocationsInfo);
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getAllocationEvents(MemoryProfiler.AllocationEventsRequest request,
                                    StreamObserver<MemoryProfiler.AllocationEventsResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.AllocationEventsResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void listAllocationContexts(MemoryProfiler.AllocationContextsRequest request,
                                       StreamObserver<MemoryProfiler.AllocationContextsResponse> responseObserver) {
      responseObserver.onNext(MemoryProfiler.AllocationContextsResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    public void addMemorySample(@NotNull MemoryProfiler.MemoryData.MemorySample sample) {
      myResponseBuilder.addMemSamples(sample);
    }

    public void addVmStatsSample(@NotNull MemoryProfiler.MemoryData.VmStatsSample sample) {
      myResponseBuilder.addVmStatsSamples(sample);
    }

    public void addAllocationInfo(@NotNull MemoryProfiler.AllocationsInfo info) {
      myResponseBuilder.addAllocationsInfo(info);
    }

    public void addHeapDumpInfos(@NotNull MemoryProfiler.HeapDumpInfo info) {
      myResponseBuilder.addHeapDumpInfos(info);
    }

    public void setTriggerHeapDumpInfo(@Nullable MemoryProfiler.HeapDumpInfo info) {
      myTriggerHeapDumpInfo = info;
    }
  }
}