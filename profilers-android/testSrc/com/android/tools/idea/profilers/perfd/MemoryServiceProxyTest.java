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

import com.android.annotations.Nullable;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.idea.profilers.LegacyAllocationConverter;
import com.android.tools.idea.profilers.LegacyAllocationTracker;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.memory.FakeMemoryService;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.COMPLETED;
import static com.android.tools.profiler.proto.MemoryProfiler.AllocationsInfo.Status.IN_PROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class MemoryServiceProxyTest {

  private static final int MONITOR_PROCESS_1 = 1;
  private static final int MONITOR_PROCESS_2 = 2;

  /**
   * Auxiliary static test data used for verifying legacy allocation tracking workflow
   */
  private static final String CLASS_NAME = MemoryServiceProxyTest.class.getName();
  private static final String METHOD_NAME = "TestMethod";
  private static final String FILE_NAME = "MemoryServiceProxyTest.java";
  private static final int LINE_NUMBER = 100;
  private static final int CLASS_ID = 0;
  private static final int THREAD_ID = 10;
  private static final int SIZE = 101;
  private static final byte[] RAW_DATA = new byte[]{'a'};

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryServiceProxyTest", myService);
  @NotNull private MemoryServiceProxy myProxy;

  // Mockable tracker states
  private CountDownLatch myParsingWaitLatch;
  private CountDownLatch myParsingDoneLatch;
  private boolean myAllocationTrackingState;
  private LegacyAllocationConverter myAllocationConverter;
  private boolean myReturnNullTrackingData;
  private IDevice myDevice;

  @Before
  public void setUp() {
    myReturnNullTrackingData = false;
    myAllocationTrackingState = false;
    myParsingWaitLatch = new CountDownLatch(1);
    myParsingDoneLatch = new CountDownLatch(1);
    myAllocationConverter = new LegacyAllocationConverter();

    myDevice = mock(IDevice.class);
    when(myDevice.getSerialNumber()).thenReturn("Serial");
    when(myDevice.getName()).thenReturn("Device");
    // api version < 26  to enable legacy tracking features.
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(1, "Version"));
    when(myDevice.isOnline()).thenReturn(true);

    ManagedChannel mockChannel = InProcessChannelBuilder.forName("MemoryServiceProxyTest").build();
    myProxy = new MemoryServiceProxy(myDevice, mockChannel, Runnable::run, (device, process) -> getTracker(device, process));

    // Monitoring two processes simultaneously
    myProxy.startMonitoringApp(
      MemoryStartRequest.newBuilder().setSession(ProfilersTestData.SESSION_DATA).setProcessId(MONITOR_PROCESS_1).build(),
      mock(StreamObserver.class));
    myProxy.startMonitoringApp(
      MemoryStartRequest.newBuilder().setSession(ProfilersTestData.SESSION_DATA).setProcessId(MONITOR_PROCESS_2).build(),
      mock(StreamObserver.class));
  }

  @After
  public void tearDown() throws Exception {
    // Stop the two process monitoring
    myProxy
      .stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(ProfilersTestData.SESSION_DATA).setProcessId(MONITOR_PROCESS_1).build(),
                         mock(StreamObserver.class));
    myProxy
      .stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(ProfilersTestData.SESSION_DATA).setProcessId(MONITOR_PROCESS_2).build(),
                         mock(StreamObserver.class));
  }

  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    ServerServiceDefinition serverDefinition = myProxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = MemoryServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertEquals(allMethods.size(), definedMethods.size());
    definedMethods.containsAll(allMethods);
  }

  @Test
  public void testGetDataWithoutAllocationsInfo() throws Exception {
    MemoryData.MemorySample memSample = MemoryData.MemorySample.newBuilder().setJavaMem(1).setTimestamp(1).build();
    MemoryData memData = MemoryProfiler.MemoryData.newBuilder().addMemSamples(memSample).build();
    myService.setMemoryData(memData);

    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).build(), observer);
    verify(observer, times(1)).onNext(memData);
    verify(observer, times(1)).onCompleted();
  }

  @Test
  public void testGetDataWithAllocationsInfo() throws Exception {
    int startTime1 = 5;
    int startTime2 = 10;
    // Enable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(true).setRequestTime(startTime1).build(),
      mock(StreamObserver.class));

    // Enable a tracking sesion on Process 2
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_2).setEnabled(true).setRequestTime(startTime2).build(),
      mock(StreamObserver.class));

    MemoryData expected1 = MemoryData.newBuilder()
      .addAllocationsInfo(AllocationsInfo.newBuilder().setStartTime(startTime1).setEndTime(DurationData.UNSPECIFIED_DURATION)
                            .setStatus(IN_PROGRESS).setLegacy(true).build())
      .setEndTimestamp(startTime1)
      .build();
    StreamObserver<MemoryData> observer1 = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setStartTime(0).setEndTime(11).build(), observer1);
    verify(observer1, times(1)).onNext(expected1);
    verify(observer1, times(1)).onCompleted();

    MemoryData expected2 = MemoryData.newBuilder()
      .addAllocationsInfo(AllocationsInfo.newBuilder().setStartTime(startTime2).setEndTime(DurationData.UNSPECIFIED_DURATION)
                            .setStatus(IN_PROGRESS).setLegacy(true).build())
      .setEndTimestamp(startTime2)
      .build();
    StreamObserver<MemoryData> observer2 = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setProcessId(MONITOR_PROCESS_2).setStartTime(0).setEndTime(11).build(), observer2);
    verify(observer2, times(1)).onNext(expected2);
    verify(observer2, times(1)).onCompleted();
  }

  @Test
  public void testLegacyAllocationTrackingWorkflow() throws Exception {
    int time1 = 5;
    int time2 = 10;
    AllocationEventsRequest eventRequest =
      AllocationEventsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setStartTime(time1).setEndTime(time2).build();

    // Enable a tracking session on Process 1
    TrackAllocationsResponse expected1 = TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.SUCCESS)
      .setInfo(
        AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(DurationData.UNSPECIFIED_DURATION).setStatus(IN_PROGRESS)
          .setLegacy(true).build()).build();
    StreamObserver<TrackAllocationsResponse> observer1 = mock(StreamObserver.class);
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(true).setRequestTime(time1).build(),
      observer1);
    assertTrue(myAllocationTrackingState);
    verify(observer1, times(1)).onNext(expected1);
    verify(observer1, times(1)).onCompleted();

    // Verify that query for AllocationEvents on at ongoing session returns the NOT_FOUND response
    StreamObserver<AllocationEventsResponse> notFoundObserver = mock(StreamObserver.class);
    myProxy.getAllocationEvents(eventRequest, notFoundObserver);
    verify(notFoundObserver, times(1)).onNext(MemoryServiceProxy.NOT_FOUND_RESPONSE);
    verify(notFoundObserver, times(1)).onCompleted();

    // Disable a tracking session on Process 1
    TrackAllocationsResponse expected2 = TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.SUCCESS)
      .setInfo(AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(time2).setStatus(COMPLETED).setLegacy(true).build())
      .build();
    StreamObserver<TrackAllocationsResponse> observer2 = mock(StreamObserver.class);
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(false).setRequestTime(time2).build(),
      observer2);
    verify(observer2, times(1)).onNext(expected2);
    verify(observer2, times(1)).onCompleted();

    // Mock completion of the parsing process and the resulting data.
    myParsingWaitLatch.countDown();
    myParsingDoneLatch.await();
    List<AllocationEvent> expectedEvents = myAllocationConverter.getAllocationEvents(time1, time2);
    AllocationEventsResponse expected3 = AllocationEventsResponse.newBuilder().setStatus(AllocationEventsResponse.Status.SUCCESS)
      .addAllEvents(expectedEvents).build();
    StreamObserver<AllocationEventsResponse> observer3 = mock(StreamObserver.class);
    myProxy.getAllocationEvents(eventRequest, observer3);
    verify(observer3, times(1)).onNext(expected3);
    verify(observer3, times(1)).onCompleted();

    // Verify that ListAllocationContexts contains the stack/class info
    AllocationContextsResponse expected4 = AllocationContextsResponse.newBuilder()
      .addAllAllocatedClasses(myAllocationConverter.getClassNames())
      .addAllAllocationStacks(myAllocationConverter.getAllocationStacks()).build();
    StreamObserver<AllocationContextsResponse> observer4 = mock(StreamObserver.class);
    myProxy.listAllocationContexts(AllocationContextsRequest.newBuilder()
                                     .addClassIds(expectedEvents.get(0).getAllocatedClassId())
                                     .addStackIds(expectedEvents.get(0).getAllocationStackId()).build(), observer4);
    verify(observer4, times(1)).onNext(expected4);
    verify(observer4, times(1)).onCompleted();
  }

  @Test
  public void testLegacyAllocationTrackingReturningNullData() throws Exception {
    int time1 = 5;
    int time2 = 10;

    // Enable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(true).setRequestTime(time1).build(),
      mock(StreamObserver.class));

    // Disable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(false).setRequestTime(time2).build(),
      mock(StreamObserver.class));

    // Mock completion of the parsing process and returning of null data;
    myReturnNullTrackingData = true;
    myParsingWaitLatch.countDown();
    myParsingDoneLatch.await();
    AllocationEventsRequest eventRequest =
      AllocationEventsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setStartTime(time1).setEndTime(time2).build();
    AllocationEventsResponse expected1 =
      AllocationEventsResponse.newBuilder().setStatus(AllocationEventsResponse.Status.FAILURE_UNKNOWN).build();
    StreamObserver<AllocationEventsResponse> observer1 = mock(StreamObserver.class);
    myProxy.getAllocationEvents(eventRequest, observer1);
    verify(observer1, times(1)).onNext(expected1);
    verify(observer1, times(1)).onCompleted();

    DumpDataRequest dumpRequest =
      DumpDataRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setDumpTime(time1).build();
    DumpDataResponse expected2 =
      DumpDataResponse.newBuilder().setStatus(DumpDataResponse.Status.FAILURE_UNKNOWN).build();
    StreamObserver<DumpDataResponse> observer2 = mock(StreamObserver.class);
    myProxy.getAllocationDump(dumpRequest, observer2);
    verify(observer2, times(1)).onNext(expected2);
    verify(observer2, times(1)).onCompleted();
  }

  /**
   * TODO: to be removed when agent allocation tracking is ready.
   * For now, calling trackAllocation should trigger the legacy workflow instead of reaching the fake memory service at all.
   */
  @Test
  public void testPostOLegacyAllocationTracking() throws Exception {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(26, "Version"));
    when(mockDevice.isOnline()).thenReturn(true);
    ManagedChannel mockChannel = InProcessChannelBuilder.forName("MemoryServiceProxyTest").build();
    MemoryServiceProxy proxy =
      new MemoryServiceProxy(mockDevice, mockChannel, Runnable::run, (device, process) -> getTracker(device, process));
    proxy.startMonitoringApp(
      MemoryStartRequest.newBuilder().setSession(ProfilersTestData.SESSION_DATA).setProcessId(MONITOR_PROCESS_1).build(),
      mock(StreamObserver.class));

    // Explicitly set the fake memory service to fail. The legacy tracking should still go through.
    myService.setExplicitAllocationsStatus(TrackAllocationsResponse.Status.FAILURE_UNKNOWN);

    TrackAllocationsResponse expected = TrackAllocationsResponse.newBuilder().setStatus(TrackAllocationsResponse.Status.SUCCESS)
      .setInfo(AllocationsInfo.newBuilder().setEndTime(DurationData.UNSPECIFIED_DURATION).setStatus(IN_PROGRESS).setLegacy(true).build())
      .build();
    StreamObserver<TrackAllocationsResponse> observer = mock(StreamObserver.class);
    proxy.trackAllocations(TrackAllocationsRequest.newBuilder().setProcessId(MONITOR_PROCESS_1).setEnabled(true).build(), observer);
    assertTrue(myAllocationTrackingState);
    verify(observer, times(1)).onNext(expected);
    verify(observer, times(1)).onCompleted();
  }

  @Test
  public void testForceGarbageCollection() {
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(clientData.getPid()).thenReturn(123);
    when(myDevice.getClients()).thenReturn(new Client[]{client});
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setProcessId(123).build(), mock(StreamObserver.class));
    verify(client, times(1)).executeGarbageCollector();
  }

  @Test
  public void testForceGarbageCollectionWhenDeviceOffline() {
    when(myDevice.isOnline()).thenReturn(false);
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(clientData.getPid()).thenReturn(123);
    when(myDevice.getClients()).thenReturn(new Client[]{client});
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setProcessId(123).build(), mock(StreamObserver.class));
    verify(client, never()).executeGarbageCollector();
  }

  @Test
  public void testForceGarbageCollectionWhenClientIsNull() {
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(clientData.getPid()).thenReturn(123);
    when(myDevice.getClients()).thenReturn(new Client[0]);
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setProcessId(123).build(), mock(StreamObserver.class));
    verify(client, never()).executeGarbageCollector();
  }

  private LegacyAllocationTracker getTracker(IDevice device, int processId) {
    return new LegacyAllocationTracker() {
      @Override
      public boolean trackAllocations(long startTime,
                                      long endTime,
                                      boolean enabled,
                                      @Nullable Executor executor,
                                      @Nullable LegacyAllocationTrackingCallback allocationConsumer) {
        myAllocationTrackingState = enabled;
        if (!enabled) {
          myAllocationConverter.addClassName(CLASS_NAME);
          List<StackTraceElement> stackTraceList = new ArrayList<>();
          stackTraceList.add(new StackTraceElement(CLASS_NAME, METHOD_NAME, FILE_NAME, LINE_NUMBER));
          LegacyAllocationConverter.CallStack stack = myAllocationConverter.addCallStack(stackTraceList);
          myAllocationConverter.addAllocation(new LegacyAllocationConverter.Allocation(CLASS_ID, SIZE, THREAD_ID, stack.getId()));

          new Thread(() -> {
            try {
              myParsingWaitLatch.await();
            }
            catch (InterruptedException ignored) {

            }
            if (myReturnNullTrackingData) {
              allocationConsumer.accept(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            }
            else {
              allocationConsumer.accept(RAW_DATA, myAllocationConverter.getClassNames(), myAllocationConverter.getAllocationStacks(),
                                        myAllocationConverter.getAllocationEvents(startTime, endTime));
            }

            myParsingDoneLatch.countDown();
          }).start();
        }

        return true;
      }
    };
  }
}