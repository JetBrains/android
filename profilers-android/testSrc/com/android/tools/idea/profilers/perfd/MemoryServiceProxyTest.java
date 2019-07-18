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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.annotations.Nullable;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.profilers.LegacyAllocationTracker;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.TrackStatus;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryProfiler.ForceGarbageCollectionRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.memory.FakeMemoryService;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryServiceProxyTest {
  private static final Common.Session SESSION1 = Common.Session.newBuilder().setSessionId(1).setPid(1).build();
  private static final Common.Session SESSION2 = Common.Session.newBuilder().setSessionId(2).setPid(2).build();

  /**
   * Auxiliary static test data used for verifying legacy allocation tracking workflow
   */
  private static final byte[] RAW_DATA = new byte[]{'a'};

  @NotNull private final FakeMemoryService myService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryServiceProxyTest", myService);
  @NotNull private MemoryServiceProxy myProxy;

  // Mockable tracker states
  private CountDownLatch myParsingWaitLatch;
  private CountDownLatch myParsingDoneLatch;
  private boolean myAllocationTrackingState;
  private boolean myReturnNullTrackingData;
  private IDevice myDevice;
  private Map<String, ByteString> myProxyBytesCache = new HashMap<>();

  @Before
  public void setUp() {
    myReturnNullTrackingData = false;
    myAllocationTrackingState = false;
    myParsingWaitLatch = new CountDownLatch(1);
    myParsingDoneLatch = new CountDownLatch(1);

    myDevice = mock(IDevice.class);
    when(myDevice.getSerialNumber()).thenReturn("Serial");
    when(myDevice.getName()).thenReturn("Device");
    // api version < 26  to enable legacy tracking features.
    when(myDevice.getVersion()).thenReturn(new AndroidVersion(AndroidVersion.VersionCodes.BASE, "Version"));
    when(myDevice.isOnline()).thenReturn(true);

    ManagedChannel mockChannel = InProcessChannelBuilder.forName("MemoryServiceProxyTest").build();
    myProxy = new MemoryServiceProxy(myDevice, mockChannel, Runnable::run, (device, process) -> getTracker(device, process),
                                     myProxyBytesCache);

    // Monitoring two processes simultaneously
    myProxy.startMonitoringApp(MemoryStartRequest.newBuilder().setSession(SESSION1).build(), mock(StreamObserver.class));
    myProxy.startMonitoringApp(MemoryStartRequest.newBuilder().setSession(SESSION2).build(), mock(StreamObserver.class));
  }

  @After
  public void tearDown() throws Exception {
    // Stop the two process monitoring
    myProxy.stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(SESSION1).build(), mock(StreamObserver.class));
    myProxy.stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(SESSION2).build(), mock(StreamObserver.class));
  }

  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    ServerServiceDefinition serverDefinition = myProxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = MemoryServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertThat(definedMethods.size()).isEqualTo(allMethods.size());
    definedMethods.containsAll(allMethods);
  }

  @Test
  public void testGetDataWithoutAllocationsInfo() throws Exception {
    MemoryData.MemorySample memSample = MemoryData.MemorySample.newBuilder().setTimestamp(1)
      .setMemoryUsage(Memory.MemoryUsageData.newBuilder().setJavaMem(1)).build();
    MemoryData memData = MemoryProfiler.MemoryData.newBuilder().addMemSamples(memSample).build();
    myService.setMemoryData(memData);

    StreamObserver<MemoryData> observer = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setSession(SESSION1).build(), observer);
    verify(observer, times(1)).onNext(memData);
    verify(observer, times(1)).onCompleted();
  }

  @Test
  public void testGetDataWithAllocationsInfo() throws Exception {
    int startTime1 = 5;
    int startTime2 = 10;
    // Enable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION1).setEnabled(true).setRequestTime(startTime1).build(),
      mock(StreamObserver.class));

    // Enable a tracking sesion on Process 2
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION2).setEnabled(true).setRequestTime(startTime2).build(),
      mock(StreamObserver.class));

    MemoryData expected1 = MemoryData.newBuilder()
      .addAllocationsInfo(AllocationsInfo.newBuilder().setStartTime(startTime1).setEndTime(Long.MAX_VALUE).setLegacy(true).build())
      .setEndTimestamp(startTime1)
      .build();
    StreamObserver<MemoryData> observer1 = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setSession(SESSION1).setStartTime(0).setEndTime(11).build(), observer1);
    verify(observer1, times(1)).onNext(expected1);
    verify(observer1, times(1)).onCompleted();

    MemoryData expected2 = MemoryData.newBuilder()
      .addAllocationsInfo(AllocationsInfo.newBuilder().setStartTime(startTime2).setEndTime(Long.MAX_VALUE).setLegacy(true).build())
      .setEndTimestamp(startTime2)
      .build();
    StreamObserver<MemoryData> observer2 = mock(StreamObserver.class);
    myProxy.getData(MemoryRequest.newBuilder().setSession(SESSION2).setStartTime(0).setEndTime(11).build(), observer2);
    verify(observer2, times(1)).onNext(expected2);
    verify(observer2, times(1)).onCompleted();
  }

  @Test
  public void testLegacyAllocationTrackingWorkflow() throws Exception {
    int time1 = 5;
    int time2 = 10;

    // Enable a tracking session on Process 1
    TrackAllocationsResponse expected1 = TrackAllocationsResponse.newBuilder()
      .setStatus(TrackStatus.newBuilder().setStatus(TrackStatus.Status.SUCCESS).setStartTime(time1))
      .setInfo(
        AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(Long.MAX_VALUE).setLegacy(true).build()).build();
    StreamObserver<TrackAllocationsResponse> observer1 = mock(StreamObserver.class);
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION1).setEnabled(true).setRequestTime(time1).build(),
      observer1);
    assertThat(myAllocationTrackingState).isTrue();
    verify(observer1, times(1)).onNext(expected1);
    verify(observer1, times(1)).onCompleted();

    // Verify that query for bytes on at ongoing session does not return any byte contents
    assertThat(myProxyBytesCache.containsKey(Integer.toString(time1))).isFalse();

    // Disable a tracking session on Process 1
    TrackAllocationsResponse expected2 = TrackAllocationsResponse.newBuilder()
      .setStatus(TrackStatus.newBuilder().setStatus(TrackStatus.Status.SUCCESS).setStartTime(time1))
      .setInfo(AllocationsInfo.newBuilder().setStartTime(time1).setEndTime(time2).setSuccess(true).setLegacy(true).build())
      .build();
    StreamObserver<TrackAllocationsResponse> observer2 = mock(StreamObserver.class);
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION1).setEnabled(false).setRequestTime(time2).build(),
      observer2);
    verify(observer2, times(1)).onNext(expected2);
    verify(observer2, times(1)).onCompleted();

    // Mock completion of the parsing process and the resulting data.
    myParsingWaitLatch.countDown();
    myParsingDoneLatch.await();
    assertThat(myProxyBytesCache.get(Integer.toString(time1))).isEqualTo(ByteString.copyFrom(RAW_DATA));
  }

  @Test
  public void testLegacyAllocationTrackingReturningNullData() throws Exception {
    int time1 = 5;
    int time2 = 10;

    // Enable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION1).setEnabled(true).setRequestTime(time1).build(),
      mock(StreamObserver.class));

    // Disable a tracking session on Process 1
    myProxy.trackAllocations(
      TrackAllocationsRequest.newBuilder().setSession(SESSION1).setEnabled(false).setRequestTime(time2).build(),
      mock(StreamObserver.class));

    // Mock completion of the parsing process and returning of null data;
    myReturnNullTrackingData = true;
    myParsingWaitLatch.countDown();
    myParsingDoneLatch.await();
    assertThat(myProxyBytesCache.get(Integer.toString(time1))).isEqualTo(ByteString.EMPTY);
  }

  @Test
  public void testForceGarbageCollection() {
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(clientData.getPid()).thenReturn(SESSION1.getPid());
    when(myDevice.getClients()).thenReturn(new Client[]{client});
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setSession(SESSION1).build(), mock(StreamObserver.class));
    verify(client, times(1)).executeGarbageCollector();
  }

  @Test
  public void testForceGarbageCollectionWhenDeviceOffline() {
    when(myDevice.isOnline()).thenReturn(false);
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(myDevice.getClients()).thenReturn(new Client[]{client});
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setSession(SESSION1).build(), mock(StreamObserver.class));
    verify(client, never()).executeGarbageCollector();
  }

  @Test
  public void testForceGarbageCollectionWhenClientIsNull() {
    Client client = mock(Client.class);
    ClientData clientData = mock(ClientData.class);
    when(client.getClientData()).thenReturn(clientData);
    when(clientData.getPid()).thenReturn(SESSION1.getPid());
    when(myDevice.getClients()).thenReturn(new Client[0]);
    myProxy.forceGarbageCollection(ForceGarbageCollectionRequest.newBuilder().setSession(SESSION1).build(), mock(StreamObserver.class));
    verify(client, never()).executeGarbageCollector();
  }

  private LegacyAllocationTracker getTracker(IDevice device, int processId) {
    return new LegacyAllocationTracker() {
      @Override
      public boolean trackAllocations(long startTime,
                                      long endTime,
                                      boolean enabled,
                                      @Nullable Executor executor,
                                      @Nullable Consumer<byte[]> allocationConsumer) {
        myAllocationTrackingState = enabled;
        if (!enabled) {
          new Thread(() -> {
            try {
              myParsingWaitLatch.await();
            }
            catch (InterruptedException ignored) {
            }

            allocationConsumer.accept(myReturnNullTrackingData ? null : RAW_DATA);
            myParsingDoneLatch.countDown();
          }).start();
        }

        return true;
      }
    };
  }
}