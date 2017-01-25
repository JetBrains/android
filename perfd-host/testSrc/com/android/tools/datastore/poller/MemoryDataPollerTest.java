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
import com.android.tools.datastore.service.MemoryService;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.util.Consumer;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MemoryDataPollerTest extends DataStorePollerTest {

  private static final int TEST_APP_ID = 1234;
  private static final int TEST_DUMP_ID = 4320;
  private static final int ERROR_TEST_DUMP_ID = 4321;
  private static final int NOT_READY_TEST_DUMP_ID = 4322;
  private static final int FINISHED_ALLOC_INFO_ID = 1;
  private static final int POST_PROCESS_ALLOC_INFO_ID = 2;
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

  private static final MemoryProfiler.MemoryData.AllocationEvent DEFAULT_ALLOCATION = MemoryProfiler.MemoryData.AllocationEvent.newBuilder()
    .setTimestamp(BASE_TIME_NS)
    .setAllocationStackId(DUMP_DATA)
    .build();

  private static final MemoryProfiler.AllocationsInfo FINISHED_ALLOCATION_INFO = MemoryProfiler.AllocationsInfo.newBuilder()
    .setInfoId(FINISHED_ALLOC_INFO_ID)
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .build();

  private static final MemoryProfiler.AllocationsInfo POST_PROCESS_ALLOCATION_INFO = MemoryProfiler.AllocationsInfo.newBuilder()
    .setInfoId(POST_PROCESS_ALLOC_INFO_ID)
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
  private MemoryService myMemoryDataPoller = new MemoryService(myDataStore, getPollTicker()::run);
  private FakeMemoryService myMemoryService = new FakeMemoryService();

  @Rule
  public TestGrpcService<FakeMemoryService> myService = new TestGrpcService<>(myMemoryDataPoller, myMemoryService);

  @Before
  public void setUp() throws Exception {
    when(myDataStore.getLegacyAllocationTracker()).thenReturn(new FakeLegacyAllocationTracker());
    // TODO: Abstract to TestGrpcService
    myMemoryDataPoller.connectService(myService.getChannel());
    startMonitoringApp();
    getPollTicker().run();
  }

  private void startMonitoringApp() {
    myMemoryDataPoller
      .startMonitoringApp(MemoryProfiler.MemoryStartRequest.newBuilder().setProcessId(TEST_APP_ID).build(), mock(StreamObserver.class));

    // First trigger heap dumps on the MemoryDataPoller to create the latching mechanism which both GetData and GetHeapDump depend on.
    myMemoryService.setTriggerHeapDumpInfo(DEFAULT_DUMP_INFO);
    myMemoryDataPoller.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
    myMemoryService.setTriggerHeapDumpInfo(ERROR_DUMP_INFO);
    myMemoryDataPoller.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
    myMemoryService.setTriggerHeapDumpInfo(NOT_READY_DUMP_INFO);
    myMemoryDataPoller.triggerHeapDump(MemoryProfiler.TriggerHeapDumpRequest.getDefaultInstance(), mock(StreamObserver.class));
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
    myMemoryDataPoller
      .stopMonitoringApp(MemoryProfiler.MemoryStopRequest.newBuilder().setProcessId(TEST_APP_ID).build(), mock(StreamObserver.class));
  }

  @Test
  public void testGetDataInRange() throws Exception {
    myMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myMemoryService.addVmStatsSample(DEFAULT_VM_STATS);
    myMemoryService.addAllocationEvents(DEFAULT_ALLOCATION);
    myMemoryService.addAllocationInfo(FINISHED_ALLOCATION_INFO);
    myMemoryService.addAllocationInfo(POST_PROCESS_ALLOCATION_INFO);
    myMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.newBuilder()
      .addMemSamples(DEFAULT_MEMORY_SAMPLE)
      .addVmStatsSamples(DEFAULT_VM_STATS)
      .addAllocationEvents(DEFAULT_ALLOCATION)
      .addAllocationsInfo(FINISHED_ALLOCATION_INFO)
      .addAllocationsInfo(POST_PROCESS_ALLOCATION_INFO)
      .addHeapDumpInfos(DEFAULT_DUMP_INFO)
      .addHeapDumpInfos(ERROR_DUMP_INFO)
      .addHeapDumpInfos(NOT_READY_DUMP_INFO)
      .build();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDataOutOfRange() throws Exception {
    myMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myMemoryService.addVmStatsSample(DEFAULT_VM_STATS);
    myMemoryService.addAllocationEvents(DEFAULT_ALLOCATION);
    myMemoryService.addAllocationInfo(FINISHED_ALLOCATION_INFO);
    myMemoryService.addAllocationInfo(POST_PROCESS_ALLOCATION_INFO);
    myMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.MemoryRequest request = MemoryProfiler.MemoryRequest.newBuilder()
      .setProcessId(0)
      .setStartTime(BASE_TIME_NS)
      .setEndTime(delayTimeFromBase(-2))
      .build();
    MemoryProfiler.MemoryData expected = MemoryProfiler.MemoryData.getDefaultInstance();
    StreamObserver<MemoryProfiler.MemoryData> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getData(request, observer);
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
    myMemoryDataPoller.getData(request, observer);
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
    myMemoryDataPoller.trackAllocations(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListAllocationContexts() throws Exception {
    MemoryProfiler.AllocationContextsRequest request = MemoryProfiler.AllocationContextsRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryProfiler.AllocationContextsResponse expected = MemoryProfiler.AllocationContextsResponse.getDefaultInstance();
    StreamObserver<MemoryProfiler.AllocationContextsResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.listAllocationContexts(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetAllocationsInfoStatusNotFound() throws Exception {
    // Ensures that a default instance is returned if there is no valid AllocationsInfoStatus
    MemoryProfiler.GetAllocationsInfoStatusRequest getRequest = MemoryProfiler.GetAllocationsInfoStatusRequest.newBuilder()
      .setInfoId(1).build();
    StreamObserver<MemoryProfiler.GetAllocationsInfoStatusResponse> getObserver = mock(StreamObserver.class);
    myMemoryDataPoller.getAllocationsInfoStatus(getRequest, getObserver);
    validateResponse(getObserver, MemoryProfiler.GetAllocationsInfoStatusResponse.getDefaultInstance());
  }

  /**
   * Tests the legacy allocation tracking workflow where the poller asynchronously updates the
   * AllocationsInfo status via the LegacyAllocationTrackingService
   */
  @Test
  public void testGetAllocationsInfoStatus() throws Exception {
    MemoryProfiler.AllocationsInfo info = MemoryProfiler.AllocationsInfo.newBuilder()
      .setInfoId(1).setStartTime(0).setEndTime(DurationData.UNSPECIFIED_DURATION).setLegacyTracking(true)
      .setStatus(MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS).build();
    myMemoryService.setTrackAllocationsInfo(info);

    // First enable allocation tracking.
    MemoryProfiler.TrackAllocationsRequest request = MemoryProfiler.TrackAllocationsRequest.newBuilder()
      .setProcessId(TEST_APP_ID).setEnabled(true).setLegacyTracking(true).build();
    StreamObserver<MemoryProfiler.TrackAllocationsResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.trackAllocations(request, observer);
    myMemoryService.addAllocationInfo(info);
    getPollTicker().run();

    // Ensures that status returns IN_PROGRESS
    MemoryProfiler.GetAllocationsInfoStatusRequest getRequest = MemoryProfiler.GetAllocationsInfoStatusRequest.newBuilder()
      .setInfoId(1).build();
    StreamObserver<MemoryProfiler.GetAllocationsInfoStatusResponse> getObserver = mock(StreamObserver.class);
    myMemoryDataPoller.getAllocationsInfoStatus(getRequest, getObserver);
    validateResponse(getObserver, MemoryProfiler.GetAllocationsInfoStatusResponse.newBuilder().setInfoId(1).setStatus(
      MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS).build());

    // Disable allocation tracking on a separate thread, which will block until the AllocationsInfo sample is received
    // via poll() before changing the status to COMPLETED.
    info = info.toBuilder().setEndTime(1).setStatus(MemoryProfiler.AllocationsInfo.Status.POST_PROCESS).build();
    myMemoryService.setTrackAllocationsInfo(info);
    final CountDownLatch threadWaitLatch = new CountDownLatch(1);
    final CountDownLatch threadDoneLatch = new CountDownLatch(1);
    new Thread(() -> {
      threadWaitLatch.countDown();
      StreamObserver<MemoryProfiler.TrackAllocationsResponse> threadObserver = mock(StreamObserver.class);
      MemoryProfiler.TrackAllocationsRequest threadRequest = MemoryProfiler.TrackAllocationsRequest.newBuilder()
        .setProcessId(TEST_APP_ID).setEnabled(false).setLegacyTracking(true).build();
      myMemoryDataPoller.trackAllocations(threadRequest, threadObserver);
      threadDoneLatch.countDown();
    }).start();

    try {
      threadWaitLatch.await();
    }
    catch (InterruptedException ignored) {
    }
    myMemoryService.resetGetDataResponse();
    myMemoryService.addAllocationInfo(info);
    getPollTicker().run();

    try {
      threadDoneLatch.await();
    }
    catch (InterruptedException ignored) {
    }
    getObserver = mock(StreamObserver.class);
    myMemoryDataPoller.getAllocationsInfoStatus(getRequest, getObserver);
    validateResponse(getObserver, MemoryProfiler.GetAllocationsInfoStatusResponse.newBuilder().setInfoId(1).setStatus(
      MemoryProfiler.AllocationsInfo.Status.COMPLETED).build());
  }

  @Test
  public void testListHeapDumpInfos() throws Exception {
    myMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
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
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
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
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
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
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testListHeapDumpInfosOutOfRange() throws Exception {
    myMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
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
    myMemoryDataPoller.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetHeapDumpNotFound() throws Exception {
    getHeapDumpError(0, MemoryProfiler.DumpDataResponse.Status.NOT_FOUND);
  }

  @Test
  public void testGetHeapDumpNotReady() throws Exception {
    myMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(NOT_READY_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.NOT_READY);
  }

  @Test
  public void testGetHeapDumpUnknown() throws Exception {
    myMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(ERROR_TEST_DUMP_ID, MemoryProfiler.DumpDataResponse.Status.FAILURE_UNKNOWN);
  }

  @Test
  public void testGetHeapDump() throws Exception {
    // First adds and polls the HeapDumpInfo sample we are testing, which counts down the latch
    myMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    getPollTicker().run();

    MemoryProfiler.HeapDumpDataRequest request = MemoryProfiler.HeapDumpDataRequest.newBuilder()
      .setProcessId(TEST_APP_ID)
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
      .setProcessId(TEST_APP_ID)
      .setDumpId(id)
      .build();
    MemoryProfiler.DumpDataResponse expected = MemoryProfiler.DumpDataResponse.newBuilder()
      .setStatus(error)
      .build();
    StreamObserver<MemoryProfiler.DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryDataPoller.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  private static long delayTimeFromBase(int numSec) {
    return BASE_TIME_NS + TimeUnit.SECONDS.toNanos(numSec);
  }

  private static class FakeLegacyAllocationTracker implements LegacyAllocationTracker {
    @Override
    public boolean setAllocationTrackingEnabled(int processId, boolean enabled) {
      return enabled;
    }

    @Override
    public void getAllocationTrackingDump(int processId, @NotNull ExecutorService executorService, @NotNull Consumer<byte[]> consumer) {
      consumer.consume(new byte[]{(byte)0});
    }

    @NotNull
    @Override
    public LegacyAllocationConverter parseDump(@NotNull byte[] dumpData) {
      return new LegacyAllocationConverter();
    }
  }

  // TODO look into merging this with MockMemoryService from profilers module.
  private static class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
    private MemoryProfiler.MemoryData.Builder myResponseBuilder = MemoryProfiler.MemoryData.newBuilder();
    private MemoryProfiler.AllocationsInfo myAllocationsInfo;
    private MemoryProfiler.HeapDumpInfo myTriggerHeapDumpInfo;

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
    public void getHeapDump(MemoryProfiler.HeapDumpDataRequest request, StreamObserver<MemoryProfiler.DumpDataResponse> responseObserver) {
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

    public void addAllocationEvents(@NotNull MemoryProfiler.MemoryData.AllocationEvent sample) {
      myResponseBuilder.addAllocationEvents(sample);
    }

    public void addAllocationInfo(@NotNull MemoryProfiler.AllocationsInfo info) {
      myResponseBuilder.addAllocationsInfo(info);
    }

    public void addHeapDumpInfos(@NotNull MemoryProfiler.HeapDumpInfo info) {
      myResponseBuilder.addHeapDumpInfos(info);
    }

    public void setTrackAllocationsInfo(@Nullable MemoryProfiler.AllocationsInfo info) {
      myAllocationsInfo = info;
    }

    public void setTriggerHeapDumpInfo(@Nullable MemoryProfiler.HeapDumpInfo info) {
      myTriggerHeapDumpInfo = info;
    }

    public void resetGetDataResponse() {
      myResponseBuilder = MemoryProfiler.MemoryData.newBuilder();
    }
  }
}