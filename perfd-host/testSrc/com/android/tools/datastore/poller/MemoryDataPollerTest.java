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

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.datastore.service.MemoryService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MemoryDataPollerTest extends DataStorePollerTest {

  private static final Common.Session TEST_SESSION = Common.Session.newBuilder().setSessionId(1234).setPid(4321).build();
  private static final long BASE_TIME_NS = TimeUnit.DAYS.toNanos(1);
  private static final ByteString DUMP_DATA = ByteString.copyFrom("Test Data", Charset.defaultCharset());

  private static final MemoryData.MemorySample DEFAULT_MEMORY_SAMPLE = MemoryData.MemorySample.newBuilder()
    .setJavaMem(1024)
    .setTimestamp(BASE_TIME_NS)
    .build();

  private static final MemoryData.AllocStatsSample DEFAULT_ALLOC_STATS = MemoryData.AllocStatsSample.newBuilder()
    .setTimestamp(BASE_TIME_NS)
    .build();

  private static final MemoryData.GcStatsSample DEFAULT_GC_STATS = MemoryData.GcStatsSample.newBuilder()
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .build();

  private static final AllocationsInfo FINISHED_LEGACY_ALLOCATION_INFO = AllocationsInfo.newBuilder()
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .setLegacy(true)
    .build();

  private static final AllocationsInfo IN_PROGRESS_LEGACY_ALLOCATION_INFO = AllocationsInfo.newBuilder()
    .setStartTime(delayTimeFromBase(1))
    .setEndTime(Long.MAX_VALUE)
    .setLegacy(true)
    .build();

  private static final AllocationsInfo IN_PROGRESS_LIVE_ALLOCATION_INFO = AllocationsInfo.newBuilder()
    .setStartTime(delayTimeFromBase(1))
    .setEndTime(Long.MAX_VALUE)
    .setLegacy(false)
    .build();

  private static final AllocationsInfo FINISHED_LIVE_ALLOCATION_INFO = AllocationsInfo.newBuilder()
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .setLegacy(false)
    .build();

  private static final HeapDumpInfo DEFAULT_DUMP_INFO = HeapDumpInfo.newBuilder()
    .setStartTime(BASE_TIME_NS)
    .setEndTime(delayTimeFromBase(1))
    .build();

  private static final HeapDumpInfo ERROR_DUMP_INFO = HeapDumpInfo.newBuilder()
    .setStartTime(delayTimeFromBase(1))
    .setEndTime(delayTimeFromBase(2))
    .build();

  private static final HeapDumpInfo NOT_READY_DUMP_INFO = HeapDumpInfo.newBuilder()
    .setStartTime(delayTimeFromBase(2))
    .setEndTime(Long.MAX_VALUE)
    .build();

  private DataStoreService myDataStore = mock(DataStoreService.class);
  private MemoryService myMemoryService = new MemoryService(myDataStore, getPollTicker()::run);
  private FakeMemoryService myFakeMemoryService = new FakeMemoryService();

  private TestName myTestName = new TestName();
  private TestGrpcService<FakeMemoryService> myService =
    new TestGrpcService<>(MemoryDataPollerTest.class, myTestName, myMemoryService, myFakeMemoryService);
  @Rule
  public RuleChain myChain = RuleChain.outerRule(myTestName).around(myService);

  @Before
  public void setUp() throws Exception {
    when(myDataStore.getMemoryClient(any())).thenReturn(MemoryServiceGrpc.newBlockingStub(myService.getChannel()));
    startMonitoringApp();
    getPollTicker().run();
  }

  private void startMonitoringApp() {
    myMemoryService
      .startMonitoringApp(MemoryStartRequest.newBuilder().setSession(TEST_SESSION).build(), mock(StreamObserver.class));
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
    myMemoryService
      .stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(TEST_SESSION).build(), mock(StreamObserver.class));
  }

  @Test
  public void testAppStoppedRequestHandled() {
    myMemoryService
      .stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(TEST_SESSION).build(), mock(StreamObserver.class));
    when(myDataStore.getMemoryClient(any())).thenReturn(null);
    StreamObserver<TriggerHeapDumpResponse> heapDump = mock(StreamObserver.class);
    myMemoryService.triggerHeapDump(TriggerHeapDumpRequest.getDefaultInstance(), heapDump);
    validateResponse(heapDump, TriggerHeapDumpResponse.getDefaultInstance());
    StreamObserver<TrackAllocationsResponse> allocations = mock(StreamObserver.class);
    myMemoryService.trackAllocations(TrackAllocationsRequest.getDefaultInstance(), allocations);
    validateResponse(allocations, TrackAllocationsResponse.getDefaultInstance());
    StreamObserver<ForceGarbageCollectionResponse> garbageCollection = mock(StreamObserver.class);
    myMemoryService.forceGarbageCollection(ForceGarbageCollectionRequest.getDefaultInstance(), garbageCollection);
    validateResponse(garbageCollection, ForceGarbageCollectionResponse.getDefaultInstance());
  }

  @Test
  public void testGetDataInRange() throws Exception {
    myFakeMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myFakeMemoryService.addAllocStatsSample(DEFAULT_ALLOC_STATS);
    myFakeMemoryService.addGcStatsSample(DEFAULT_GC_STATS);
    myFakeMemoryService.addAllocationInfo(FINISHED_LEGACY_ALLOCATION_INFO);
    myFakeMemoryService.addAllocationInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO);
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryRequest request = MemoryRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryData expected = MemoryData.newBuilder()
      .addMemSamples(DEFAULT_MEMORY_SAMPLE)
      .addAllocStatsSamples(DEFAULT_ALLOC_STATS)
      .addGcStatsSamples(DEFAULT_GC_STATS)
      .addAllocationsInfo(FINISHED_LEGACY_ALLOCATION_INFO)
      .addAllocationsInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO)
      .addHeapDumpInfos(DEFAULT_DUMP_INFO)
      .addHeapDumpInfos(ERROR_DUMP_INFO)
      .addHeapDumpInfos(NOT_READY_DUMP_INFO)
      .build();
    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetDataOutOfRange() throws Exception {
    myFakeMemoryService.addMemorySample(DEFAULT_MEMORY_SAMPLE);
    myFakeMemoryService.addAllocStatsSample(DEFAULT_ALLOC_STATS);
    myFakeMemoryService.addGcStatsSample(DEFAULT_GC_STATS);
    myFakeMemoryService.addAllocationInfo(FINISHED_LEGACY_ALLOCATION_INFO);
    myFakeMemoryService.addAllocationInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO);
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    MemoryRequest request = MemoryRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(BASE_TIME_NS)
      .setEndTime(delayTimeFromBase(-2))
      .build();
    MemoryData expected = MemoryData.getDefaultInstance();
    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  @Ignore
  public void testGetDataInvalidSession() throws Exception {
    MemoryRequest request = MemoryRequest.newBuilder()
      .setSession(Common.Session.getDefaultInstance())
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryData expected = MemoryData.getDefaultInstance();
    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testTrackAllocations() throws Exception {
    TrackAllocationsRequest request = TrackAllocationsRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setEnabled(true)
      .build();
    TrackAllocationsResponse expected = TrackAllocationsResponse.newBuilder()
      .setStatus(TrackAllocationsResponse.Status.SUCCESS)
      .build();
    StreamObserver<TrackAllocationsResponse> observer = mock(StreamObserver.class);
    myMemoryService.trackAllocations(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testTrackAllocationsImmediatelyCreatesInfoData() throws Exception {
    // Make sure there are no AllocationsInfos in the table:
    MemoryRequest memoryRequest = MemoryRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    MemoryData expectedData = MemoryData.getDefaultInstance();
    StreamObserver<MemoryData> memoryDataObserver = mock(StreamObserver.class);
    myMemoryService.getData(memoryRequest, memoryDataObserver);
    validateResponse(memoryDataObserver, expectedData);

    // Enable allocation tracking.
    myFakeMemoryService.setTrackAllocationsInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO);
    TrackAllocationsRequest request = TrackAllocationsRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setEnabled(true)
      .build();
    TrackAllocationsResponse trackAllocationsExpected = TrackAllocationsResponse.newBuilder()
      .setStatus(TrackAllocationsResponse.Status.SUCCESS)
      .setInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO)
      .build();
    StreamObserver<TrackAllocationsResponse> trackAllocationsObserver = mock(StreamObserver.class);
    myMemoryService.trackAllocations(request, trackAllocationsObserver);
    validateResponse(trackAllocationsObserver, trackAllocationsExpected);

    // Tests that the AllocationsInfo exists without having to poll.
    expectedData = MemoryData.newBuilder().addAllocationsInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO).build();
    memoryDataObserver = mock(StreamObserver.class);
    myMemoryService.getData(memoryRequest, memoryDataObserver);
    validateResponse(memoryDataObserver, expectedData);
  }

  @Test
  public void testTriggerHeapDumpImmediatelyCreatesInfoData() throws Exception {
    // Make sure there are no HeapDumpInfos in the table:
    ListDumpInfosRequest listHeapDumpRequest = ListDumpInfosRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    ListHeapDumpInfosResponse expectedList = ListHeapDumpInfosResponse.newBuilder()
      .build();
    StreamObserver<ListHeapDumpInfosResponse> listHeapDumpObserver = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(listHeapDumpRequest, listHeapDumpObserver);
    validateResponse(listHeapDumpObserver, expectedList);

    // Triggers heap dump
    myFakeMemoryService.setTriggerHeapDumpInfo(DEFAULT_DUMP_INFO);
    TriggerHeapDumpRequest triggerHeapDumpRequest = TriggerHeapDumpRequest.newBuilder().setSession(TEST_SESSION).build();
    TriggerHeapDumpResponse expectedTrigger = TriggerHeapDumpResponse.newBuilder().setStatus(TriggerHeapDumpResponse.Status.SUCCESS)
      .setInfo(DEFAULT_DUMP_INFO).build();
    StreamObserver<TriggerHeapDumpResponse> triggerHeapDumpObserver = mock(StreamObserver.class);
    myMemoryService.triggerHeapDump(triggerHeapDumpRequest, triggerHeapDumpObserver);
    validateResponse(triggerHeapDumpObserver, expectedTrigger);

    // Tests that the HeapDumpInfo exists without having to poll.
    expectedList = ListHeapDumpInfosResponse.newBuilder().addInfos(DEFAULT_DUMP_INFO).build();
    listHeapDumpObserver = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(listHeapDumpRequest, listHeapDumpObserver);
    validateResponse(listHeapDumpObserver, expectedList);
  }

  @Test
  public void testListHeapDumpInfos() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    ListDumpInfosRequest request = ListDumpInfosRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(0)
      .setEndTime(BASE_TIME_NS)
      .build();
    ListHeapDumpInfosResponse expected = ListHeapDumpInfosResponse.newBuilder()
      .addInfos(DEFAULT_DUMP_INFO)
      .build();
    StreamObserver<ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);

    request = ListDumpInfosRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(BASE_TIME_NS)
      .setEndTime(delayTimeFromBase(1))
      .build();
    expected = ListHeapDumpInfosResponse.newBuilder()
      .addInfos(DEFAULT_DUMP_INFO)
      .addInfos(ERROR_DUMP_INFO)
      .build();
    observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);

    request = ListDumpInfosRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(delayTimeFromBase(1))
      .setEndTime(delayTimeFromBase(2))
      .build();
    expected = ListHeapDumpInfosResponse.newBuilder()
      .addInfos(ERROR_DUMP_INFO)
      .addInfos(NOT_READY_DUMP_INFO)
      .build();
    observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testOngoingHeapDumpOnStop() throws Exception {
    StreamObserver<MemoryStopResponse> stopObserver = mock(StreamObserver.class);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();
    myMemoryService.stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(TEST_SESSION).build(), stopObserver);

    DumpDataRequest request =
      DumpDataRequest.newBuilder().setSession(TEST_SESSION).setDumpTime(NOT_READY_DUMP_INFO.getStartTime()).build();
    StreamObserver<DumpDataResponse> dumpObserver = mock(StreamObserver.class);
    myMemoryService.getHeapDump(request, dumpObserver);
    DumpDataResponse dumpExpectedResponse =
      DumpDataResponse.newBuilder().setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN).setData(ByteString.EMPTY).build();
    validateResponse(dumpObserver, dumpExpectedResponse);
  }

  @Test
  public void testListHeapDumpInfosOutOfRange() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();

    ListDumpInfosRequest request = ListDumpInfosRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(delayTimeFromBase(2))
      .setEndTime(Long.MAX_VALUE)
      .build();
    ListHeapDumpInfosResponse expected = ListHeapDumpInfosResponse.newBuilder()
      .addInfos(NOT_READY_DUMP_INFO)
      .build();
    StreamObserver<ListHeapDumpInfosResponse> observer = mock(StreamObserver.class);
    myMemoryService.listHeapDumpInfos(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetHeapDumpNotFound() throws Exception {
    getHeapDumpError(delayTimeFromBase(-1), DumpDataResponse.Status.NOT_FOUND);
  }

  @Test
  public void testGetHeapDumpNotReady() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(NOT_READY_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(NOT_READY_DUMP_INFO.getStartTime(), DumpDataResponse.Status.NOT_READY);
  }

  @Test
  public void testGetHeapDumpUnknown() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(ERROR_DUMP_INFO);
    getPollTicker().run();
    getHeapDumpError(ERROR_DUMP_INFO.getStartTime(), DumpDataResponse.Status.FAILURE_UNKNOWN);
  }

  @Test
  public void testGetHeapDump() throws Exception {
    myFakeMemoryService.addHeapDumpInfos(DEFAULT_DUMP_INFO);
    getPollTicker().run();

    DumpDataRequest request = DumpDataRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setDumpTime(DEFAULT_DUMP_INFO.getStartTime())
      .build();
    DumpDataResponse expected = DumpDataResponse.newBuilder()
      .setStatus(DumpDataResponse.Status.SUCCESS)
      .setData(DUMP_DATA)
      .build();
    StreamObserver<DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetLegacyAllocationDump() {
    myFakeMemoryService.addAllocationInfo(FINISHED_LEGACY_ALLOCATION_INFO);
    getPollTicker().run();

    DumpDataRequest request = DumpDataRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setDumpTime(FINISHED_LEGACY_ALLOCATION_INFO.getStartTime())
      .build();
    DumpDataResponse expected = DumpDataResponse.newBuilder()
      .setStatus(DumpDataResponse.Status.SUCCESS)
      .setData(DUMP_DATA)
      .build();
    StreamObserver<DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getLegacyAllocationDump(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testOnGoingAllocationTrackingStopped() throws Exception {
    myFakeMemoryService.addAllocationInfo(IN_PROGRESS_LEGACY_ALLOCATION_INFO);
    getPollTicker().run();
    StreamObserver<MemoryStopResponse> stopObserver = mock(StreamObserver.class);
    myMemoryService.stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(TEST_SESSION).build(), stopObserver);

    MemoryRequest request = MemoryRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(0)
      .setEndTime(Long.MAX_VALUE)
      .build();
    AllocationsInfo expectedFailedInfo = AllocationsInfo.newBuilder()
      .setStartTime(delayTimeFromBase(1))
      .setEndTime(delayTimeFromBase(1) + 1)
      .setStatus(AllocationsInfo.Status.FAILURE_UNKNOWN)
      .setLegacy(true)
      .build();
    MemoryData expected = MemoryData.newBuilder()
      .addAllocationsInfo(expectedFailedInfo)
      .build();
    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myMemoryService.getData(request, observer);
    validateResponse(observer, expected);

    LegacyAllocationEventsRequest eventsRequest = LegacyAllocationEventsRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(IN_PROGRESS_LEGACY_ALLOCATION_INFO.getStartTime())
      .build();
    LegacyAllocationEventsResponse eventsExpected = LegacyAllocationEventsResponse.newBuilder()
      .setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN)
      .build();
    StreamObserver<LegacyAllocationEventsResponse> eventsObserver = mock(StreamObserver.class);
    myMemoryService.getLegacyAllocationEvents(eventsRequest, eventsObserver);
    validateResponse(eventsObserver, eventsExpected);

    DumpDataRequest dumpRequest = DumpDataRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setDumpTime(IN_PROGRESS_LEGACY_ALLOCATION_INFO.getStartTime())
      .build();
    DumpDataResponse dumpExpected = DumpDataResponse.newBuilder()
      .setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN)
      .build();
    StreamObserver<DumpDataResponse> dumpObserver = mock(StreamObserver.class);
    myMemoryService.getLegacyAllocationDump(dumpRequest, dumpObserver);
    validateResponse(dumpObserver, dumpExpected);
  }

  @Test
  public void testGetLegacyAllocationDumpOnLiveAllocation() {
    myFakeMemoryService.addAllocationInfo(FINISHED_LIVE_ALLOCATION_INFO);
    getPollTicker().run();

    DumpDataRequest request = DumpDataRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setDumpTime(FINISHED_LIVE_ALLOCATION_INFO.getStartTime())
      .build();
    DumpDataResponse expected = DumpDataResponse.newBuilder()
      .setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN)
      .build();
    StreamObserver<DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getLegacyAllocationDump(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetLegacyAllocationEventsOnLiveAllocation() {
    myFakeMemoryService.addAllocationInfo(FINISHED_LIVE_ALLOCATION_INFO);
    getPollTicker().run();

    LegacyAllocationEventsRequest request = LegacyAllocationEventsRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setStartTime(FINISHED_LIVE_ALLOCATION_INFO.getStartTime())
      .build();
    LegacyAllocationEventsResponse expected = LegacyAllocationEventsResponse.newBuilder()
      .setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN)
      .build();
    StreamObserver<LegacyAllocationEventsResponse> observer = mock(StreamObserver.class);
    myMemoryService.getLegacyAllocationEvents(request, observer);
    validateResponse(observer, expected);
  }

  private void getHeapDumpError(long dumpTime, DumpDataResponse.Status error) {
    DumpDataRequest request = DumpDataRequest.newBuilder()
      .setSession(TEST_SESSION)
      .setDumpTime(dumpTime)
      .build();
    DumpDataResponse expected = DumpDataResponse.newBuilder()
      .setStatus(error)
      .build();
    StreamObserver<DumpDataResponse> observer = mock(StreamObserver.class);
    myMemoryService.getHeapDump(request, observer);
    validateResponse(observer, expected);
  }

  private static long delayTimeFromBase(int numSec) {
    return BASE_TIME_NS + TimeUnit.SECONDS.toNanos(numSec);
  }

  // TODO look into merging this with MockMemoryService from profilers module.
  private static class FakeMemoryService extends MemoryServiceGrpc.MemoryServiceImplBase {
    private MemoryData.Builder myResponseBuilder = MemoryData.newBuilder();
    private AllocationsInfo myAllocationsInfo;
    private HeapDumpInfo myTriggerHeapDumpInfo;

    @Override
    public void startMonitoringApp(MemoryStartRequest request,
                                   StreamObserver<MemoryStartResponse> responseObserver) {
      responseObserver.onNext(MemoryStartResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(MemoryStopRequest request,
                                  StreamObserver<MemoryStopResponse> responseObserver) {
      responseObserver.onNext(MemoryStopResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
      responseObserver.onNext(myResponseBuilder.build());
      responseObserver.onCompleted();
    }

    // Return empty data
    @Override
    public void getJvmtiData(MemoryRequest request, StreamObserver<MemoryData> responseObserver) {
      responseObserver.onNext(MemoryData.newBuilder().build());
      responseObserver.onCompleted();
    }

    @Override
    public void triggerHeapDump(TriggerHeapDumpRequest request,
                                StreamObserver<TriggerHeapDumpResponse> responseObserver) {
      TriggerHeapDumpResponse.Builder builder = TriggerHeapDumpResponse.newBuilder();
      builder.setStatus(TriggerHeapDumpResponse.Status.SUCCESS);
      if (myTriggerHeapDumpInfo != null) {
        builder.setInfo(myTriggerHeapDumpInfo);
      }
      responseObserver.onNext(builder.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getHeapDump(DumpDataRequest request, StreamObserver<DumpDataResponse> responseObserver) {
      DumpDataResponse.Builder response = DumpDataResponse.newBuilder();
      if (request.getDumpTime() == DEFAULT_DUMP_INFO.getStartTime()) {
        response.setData(DUMP_DATA);
        response.setStatus(DumpDataResponse.Status.SUCCESS);
      }
      else if (request.getDumpTime() == NOT_READY_DUMP_INFO.getStartTime()) {
        response.setStatus(DumpDataResponse.Status.NOT_READY);
      }
      else if (request.getDumpTime() == ERROR_DUMP_INFO.getStartTime()) {
        response.setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN);
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getLegacyAllocationDump(DumpDataRequest request,
                                        StreamObserver<DumpDataResponse> responseObserver) {
      DumpDataResponse.Builder response = DumpDataResponse.newBuilder();
      if (request.getDumpTime() == FINISHED_LEGACY_ALLOCATION_INFO.getStartTime()) {
        response.setData(DUMP_DATA);
        response.setStatus(DumpDataResponse.Status.SUCCESS);
      }
      else if (request.getDumpTime() == IN_PROGRESS_LEGACY_ALLOCATION_INFO.getStartTime()) {
        response.setStatus(DumpDataResponse.Status.NOT_READY);
      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void listHeapDumpInfos(ListDumpInfosRequest request,
                                  StreamObserver<ListHeapDumpInfosResponse> responseObserver) {
      super.listHeapDumpInfos(request, responseObserver);
    }

    @Override
    public void trackAllocations(TrackAllocationsRequest request,
                                 StreamObserver<TrackAllocationsResponse> responseObserver) {
      TrackAllocationsResponse.Builder response = TrackAllocationsResponse.newBuilder();
      response.setStatus(TrackAllocationsResponse.Status.SUCCESS);
      if (myAllocationsInfo != null) {
        response.setInfo(myAllocationsInfo);
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }

    @Override
    public void getLegacyAllocationEvents(LegacyAllocationEventsRequest request,
                                          StreamObserver<LegacyAllocationEventsResponse> responseObserver) {
      responseObserver.onNext(LegacyAllocationEventsResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getLegacyAllocationContexts(LegacyAllocationContextsRequest request,
                                            StreamObserver<AllocationContextsResponse> responseObserver) {
      responseObserver.onNext(AllocationContextsResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    public void addMemorySample(@NotNull MemoryData.MemorySample sample) {
      myResponseBuilder.addMemSamples(sample);
    }

    public void addAllocStatsSample(@NotNull MemoryData.AllocStatsSample sample) {
      myResponseBuilder.addAllocStatsSamples(sample);
    }

    public void addGcStatsSample(@NotNull MemoryData.GcStatsSample sample) {
      myResponseBuilder.addGcStatsSamples(sample);
    }

    public void addAllocationInfo(@NotNull AllocationsInfo info) {
      myResponseBuilder.addAllocationsInfo(info);
    }

    public void addHeapDumpInfos(@NotNull HeapDumpInfo info) {
      myResponseBuilder.addHeapDumpInfos(info);
    }

    public void setTriggerHeapDumpInfo(@Nullable HeapDumpInfo info) {
      myTriggerHeapDumpInfo = info;
    }

    public void setTrackAllocationsInfo(@Nullable AllocationsInfo info) {
      myAllocationsInfo = info;
    }
  }
}