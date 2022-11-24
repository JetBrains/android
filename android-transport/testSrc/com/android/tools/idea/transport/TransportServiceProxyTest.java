/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import static com.android.tools.idea.transport.TransportServiceProxy.PRE_LOLLIPOP_FAILURE_REASON;
import static com.android.tools.profiler.proto.Commands.Command.CommandType.BEGIN_SESSION;
import static com.android.tools.profiler.proto.Commands.Command.CommandType.ECHO;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ProfileableClient;
import com.android.ddmlib.ProfileableClientData;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlagSettings;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.MethodDescriptor;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.ServerServiceDefinition;
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder;
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TransportServiceProxyTest {
  @Rule
  public Timeout myTimeout = Timeout.seconds(10);

  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice,
                                startNamedChannel("testBindServiceContainsAllMethods", new FakeTransportService()),
                                new LinkedBlockingDeque<>(), new HashMap<>());

    ServerServiceDefinition serverDefinition = proxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = TransportServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertThat(definedMethods.size()).isEqualTo(allMethods.size());
    assertThat(definedMethods.containsAll(allMethods)).isTrue();
  }

  @Test
  public void testUnknownDeviceLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    Common.Device profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    assertThat(profilerDevice.getModel()).isEqualTo("Unknown");
  }

  @Test
  public void testUnsupportedReason() throws Exception {
    IDevice mockDevice1 = createMockDevice(AndroidVersion.VersionCodes.KITKAT, new Client[0]);
    Common.Device profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice1);
    assertThat(profilerDevice.getUnsupportedReason()).isEqualTo(PRE_LOLLIPOP_FAILURE_REASON);

    IDevice mockDevice2 = createMockDevice(AndroidVersion.VersionCodes.Q, new Client[0]);
    profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice2);
    assertThat(profilerDevice.getUnsupportedReason()).isEmpty();
  }

  @Test
  public void testUnknownEmulatorLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    when(mockDevice.isEmulator()).thenReturn(true);
    when(mockDevice.getAvdName()).thenReturn(null);
    Common.Device profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    assertThat(profilerDevice.getModel()).isEqualTo("Serial");
  }

  @Test
  public void testClientsWithNullDescriptionsNotCached() throws Exception {
    Client client1 = createMockClient(1, "test1", "testClientDescription");
    Client client2 = createMockClient(2, "test2", null);
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client1, client2});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice,
                                startNamedChannel("testClientsWithNullDescriptionsNotCached", new FakeTransportService()),
                                new LinkedBlockingDeque<>(), new HashMap<>());
    Map<Integer, Common.Process> cachedProcesses = proxy.getCachedProcesses();
    assertThat(cachedProcesses.size()).isEqualTo(1);
    Map.Entry<Integer, Common.Process> cachedProcess = cachedProcesses.entrySet().iterator().next();
    assertThat(cachedProcess.getKey()).isEqualTo(client1.getClientData().getPid());
    assertThat(cachedProcess.getValue().getPid()).isEqualTo(1);
    assertThat(cachedProcess.getValue().getPackageName()).isEqualTo("test1");
    assertThat(cachedProcess.getValue().getName()).isEqualTo("testClientDescription");
    assertThat(cachedProcess.getValue().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(cachedProcess.getValue().getAbiCpuArch()).isEqualTo(SdkConstants.CPU_ARCH_ARM);
  }

  @Test
  public void profileableClientsAlsoCached() throws Exception {
    Client client1 = createMockClient(1, "test1", "name1");
    ProfileableClient client2 = createMockProfileableClient(2, "name2");
    IDevice device = createMockDevice(AndroidVersion.VersionCodes.S, new Client[]{client1}, new ProfileableClient[] { client2 });
    Common.Device transportDevice = TransportServiceProxy.transportDeviceFromIDevice(device);
    TransportServiceProxy proxy =
      new TransportServiceProxy(device, transportDevice,
                                startNamedChannel("profileableClientsAlsoCached", new FakeTransportService()),
                                new LinkedBlockingDeque<>(), new HashMap<>());
    Map<Integer, Common.Process> cachedProcesses = proxy.getCachedProcesses();
    assertThat(cachedProcesses.size()).isEqualTo(2);
    Common.Process process1 = cachedProcesses.get(1);
    assertThat(process1.getPid()).isEqualTo(1);
    assertThat(process1.getPackageName()).isEqualTo("test1");
    assertThat(process1.getName()).isEqualTo("name1");
    assertThat(process1.getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(process1.getAbiCpuArch()).isEqualTo(SdkConstants.CPU_ARCH_ARM);
    assertThat(process1.getExposureLevel()).isEqualTo(Common.Process.ExposureLevel.DEBUGGABLE);
    Common.Process process2 = cachedProcesses.get(2);
    assertThat(process2.getPid()).isEqualTo(2);
    assertThat(process2.getName()).isEqualTo("name2");
    assertThat(process2.getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(process2.getAbiCpuArch()).isEqualTo(SdkConstants.CPU_ARCH_ARM);
    assertThat(process2.getExposureLevel()).isEqualTo(Common.Process.ExposureLevel.PROFILEABLE);
  }

  @Ignore("b/126763044")
  @Test
  public void testEventStreaming() throws Exception {
    Client client1 = createMockClient(1, "test1", "testClient1");
    Client client2 = createMockClient(2, "test2", "testClient2");
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client1, client2});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    FakeTransportService thruService = new FakeTransportService();
    ManagedChannel thruChannel = startNamedChannel("testEventStreaming", thruService);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice, thruChannel, new LinkedBlockingDeque<>(), new HashMap<>());
    List<Common.Event> receivedEvents = new ArrayList<>();
    // We should expect six events: two process starts events, followed by event1 and event2, then process ends events.
    CountDownLatch latch = new CountDownLatch(1);
    proxy.getEvents(Transport.GetEventsRequest.getDefaultInstance(), new StreamObserver<Common.Event>() {
      @Override
      public void onNext(Common.Event event) {
        receivedEvents.add(event);
      }

      @Override
      public void onError(Throwable throwable) {
        assert false;
      }

      @Override
      public void onCompleted() {
        latch.countDown();
      }
    });

    Common.Event event1 = Common.Event.newBuilder().setPid(1).setIsEnded(true).build();
    Common.Event endedGroupEvent2 =
      Common.Event.newBuilder().setKind(Common.Event.Kind.ECHO).setPid(2).setGroupId(2).setIsEnded(true).build();
    Common.Event openedGroupEvent3 = Common.Event.newBuilder().setKind(Common.Event.Kind.ECHO).setPid(3).setGroupId(3).build();
    thruService.addEvents(event1, endedGroupEvent2, openedGroupEvent3);
    thruService.stopEventThread();
    thruChannel.shutdownNow();
    proxy.disconnect();
    latch.await();

    assertThat(receivedEvents).hasSize(8);
    // We know event 1 , endedGroupeEven2 and openedGroupEvent3 will arrive in order. But the two processes' events can arrive out of order.
    // So here we only check whether those events are somewhere in the returned list.
    assertThat(receivedEvents.stream().filter(e -> e.getProcess().getProcessStarted().getProcess().getPid() == 1).count()).isEqualTo(1);
    assertThat(receivedEvents.stream().filter(e -> e.getProcess().getProcessStarted().getProcess().getPid() == 2).count()).isEqualTo(1);
    assertThat(receivedEvents.get(2)).isEqualTo(event1);
    assertThat(receivedEvents.get(3)).isEqualTo(endedGroupEvent2);
    assertThat(receivedEvents.get(4)).isEqualTo(openedGroupEvent3);
    // Make sure we only receive end events for those that have group id set and are still not ended.
    assertThat(
      receivedEvents.stream().filter(e -> e.getKind() == Common.Event.Kind.ECHO && e.getGroupId() == 3 && e.getIsEnded()).count())
      .isEqualTo(1);
    assertThat(
      receivedEvents.stream()
        .filter(e -> e.getKind() == Common.Event.Kind.PROCESS && e.getGroupId() == 1 && e.getPid() == 1 && e.getIsEnded()).count())
      .isEqualTo(1);
    assertThat(
      receivedEvents.stream()
        .filter(e -> e.getKind() == Common.Event.Kind.PROCESS && e.getGroupId() == 2 && e.getPid() == 2 && e.getIsEnded()).count())
      .isEqualTo(1);
  }

  @Test
  public void testProxyCommandHandlers() throws Exception {
    Client client = createMockClient(1, "test", "testClientDescription");
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    FakeTransportService thruService = new FakeTransportService();
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice,
                                startNamedChannel("testProxyCommandHandlers", thruService), new LinkedBlockingDeque<>(), new HashMap<>());

    CountDownLatch latch = new CountDownLatch(1);
    proxy.registerCommandHandler(ECHO, cmd -> {
      latch.countDown();
      return Transport.ExecuteResponse.getDefaultInstance();
    });

    StreamObserver<Transport.ExecuteResponse> observer = mock(StreamObserver.class);
    proxy.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder().setType(BEGIN_SESSION))
        .build(),
      observer);
    assertThat(thruService.myLastCommandType).isEqualTo(BEGIN_SESSION);

    proxy.execute(
      Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder().setType(ECHO))
        .build(),
      observer);
    try {
      latch.await();
    }
    catch (InterruptedException ignored) {
    }
    assertThat(thruService.myLastCommandType).isEqualTo(BEGIN_SESSION);
  }

  @Test
  public void testProxyEventPreprocessors() throws Exception {
    Client client = createMockClient(1, "test", "testClientDescription");
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    FakeTransportService thruService = new FakeTransportService();
    ManagedChannel thruChannel = startNamedChannel("testEventPreprocessors", thruService);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice, thruChannel, new LinkedBlockingDeque<>(), new HashMap<>());

    CountDownLatch latch = new CountDownLatch(1);
    List<Common.Event> receivedEvents = new ArrayList<>();
    List<Common.Event> preprocessedEvents = new ArrayList<>();
    Common.Event generatedEvent = Common.Event.getDefaultInstance();
    proxy.registerEventPreprocessor(new TransportEventPreprocessor() {
      @Override
      public boolean shouldPreprocess(Common.Event event) {
        return event.getKind() == Common.Event.Kind.ECHO;
      }

      @Override
      public Iterable<Common.Event> preprocessEvent(Common.Event event) {
        preprocessedEvents.add(event);
        latch.countDown();
        return Collections.singletonList(generatedEvent);
      }
    });
    proxy.getEvents(Transport.GetEventsRequest.getDefaultInstance(), new StreamObserver<Common.Event>() {
      @Override
      public void onNext(Common.Event event) {
        receivedEvents.add(event);
      }

      @Override
      public void onError(Throwable throwable) {
        assert false;
      }

      @Override
      public void onCompleted() {}
    });
    Common.Event eventToPreprocess = Common.Event.newBuilder().setPid(1).setKind(Common.Event.Kind.ECHO).setIsEnded(true).build();
    Common.Event eventToIgnore = Common.Event.newBuilder().setPid(1).setIsEnded(true).build();
    // Add eventToIgnore before eventToPreprocess because the latch-based synchronization is count on eventToPreprocess.
    // If eventToIgnore is added after, the service may stop before the event is sent, or the assertion is checked before
    // the event is received or recorded.
    thruService.addEvents(eventToIgnore, eventToPreprocess);
    thruService.stopEventThread();
    thruChannel.shutdownNow();
    proxy.disconnect();
    latch.await();
    assertThat(receivedEvents).containsAllOf(eventToPreprocess, eventToIgnore, generatedEvent);
    assertThat(preprocessedEvents).containsExactly(eventToPreprocess);
  }

  @Test
  public void testProxyDataPreprocessor() throws Exception {
    //Setup
    Client client = createMockClient(1, "test", "testClientDescription");
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    FakeTransportService thruService = new FakeTransportService();
    ManagedChannel thruChannel = startNamedChannel("testProxyDataPreprocessor", thruService);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice, thruChannel, new LinkedBlockingDeque<>(), new HashMap<>());
    // Fake Data Preprocessor.
    List<ByteString> receivedData = new ArrayList<>();
    TransportBytesPreprocessor preprocessor = new TransportBytesPreprocessor() {
      @Override
      public boolean shouldPreprocess(Transport.BytesRequest request) {
        return request.getId().equals("1");
      }

      @NotNull
      @Override
      public ByteString preprocessBytes(String id, ByteString event) {
        return ByteString.copyFromUtf8("WORLD");
      }
    };
    proxy.registerDataPreprocessor(preprocessor);

    // Handle returning data to proxy service.
    Transport.BytesRequest.Builder request = Transport.BytesRequest.newBuilder();
    StreamObserver<Transport.BytesResponse> validation = new StreamObserver<Transport.BytesResponse>() {
      @Override
      public void onNext(Transport.BytesResponse response) {
        receivedData.add(response.getContents());
      }

      @Override
      public void onError(Throwable throwable) { assert false;}

      @Override
      public void onCompleted() {}
    };

    // Run test.
    proxy.getBytes(request.build(), validation);
    request.setId("1");
    proxy.getBytes(request.build(), validation);
    // Clean up
    thruService.stopEventThread();
    thruChannel.shutdownNow();
    proxy.disconnect();
    // Validate
    assertThat(receivedData.get(0)).isEqualTo(FakeTransportService.TEST_BYTES);
    assertThat(receivedData.get(1)).isEqualTo(preprocessor.preprocessBytes("1", FakeTransportService.TEST_BYTES));
  }

  @Test
  public void bootIdIsSetCorrectly() throws Exception {
    Client client1 = createMockClient(1, "test1", "name1");
    ProfileableClient client2 = createMockProfileableClient(2, "name2");
    IDevice device = createMockDevice(AndroidVersion.VersionCodes.S, new Client[]{client1}, new ProfileableClient[] { client2 });
    Common.Device transportDevice = TransportServiceProxy.transportDeviceFromIDevice(device);
    assertThat(transportDevice.getBootId()).isEqualTo("boot-id");
  }

  /**
   * @param uniqueName Name should be unique across tests.
   */
  private ManagedChannel startNamedChannel(String uniqueName, FakeTransportService thruService) throws IOException {
    InProcessServerBuilder builder = InProcessServerBuilder.forName(uniqueName);
    builder.addService(thruService);
    Server server = builder.build();
    server.start();

    return InProcessChannelBuilder.forName(uniqueName).build();
  }

  @NotNull
  private IDevice createMockDevice(int version, @NotNull Client[] clients, @NotNull ProfileableClient[] profileables) throws Exception {
    ProfileableClient[] allProfileables =
      version >= AndroidVersion.VersionCodes.S
      ? ArrayUtils.addAll(Arrays.stream(clients).map(this::createMockProfileableClient).toArray(ProfileableClient[]::new), profileables)
      : new ProfileableClient[0];
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(version, null));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(clients);
    when(mockDevice.getProfileableClients()).thenReturn(allProfileables);
    when(mockDevice.getState()).thenReturn(IDevice.DeviceState.ONLINE);
    when(mockDevice.getAbis()).thenReturn(Collections.singletonList("armeabi"));
    when(mockDevice.getProperty(IDevice.PROP_BUILD_TAGS)).thenReturn("release-keys");
    when(mockDevice.getProperty(IDevice.PROP_BUILD_TYPE)).thenReturn("user");
    when(mockDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI)).thenReturn("armeabi");
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        ((IShellOutputReceiver)args[1]).addOutput("boot-id\n".getBytes(), 0, 8);
        ((IShellOutputReceiver)args[1]).flush();
        return null;
      }
    }).when(mockDevice).executeShellCommand(anyString(), any(IShellOutputReceiver.class));
    return mockDevice;
  }

  @NotNull
  private IDevice createMockDevice(int version, @NotNull Client[] clients) throws Exception {
    return createMockDevice(version, clients, new ProfileableClient[0]);
  }

  @NotNull
  private Client createMockClient(int pid, @Nullable String packageName, @Nullable String clientDescription) {
    ClientData mockData = mock(ClientData.class);
    when(mockData.getPid()).thenReturn(pid);
    when(mockData.getPackageName()).thenReturn(packageName);
    when(mockData.getClientDescription()).thenReturn(clientDescription);

    Client mockClient = mock(Client.class);
    when(mockClient.getClientData()).thenReturn(mockData);
    return mockClient;
  }

  @NotNull
  private ProfileableClient createMockProfileableClient(int pid, String processName) {
    ProfileableClientData mockData = mock(ProfileableClientData.class);
    when(mockData.getPid()).thenReturn(pid);
    when(mockData.getProcessName()).thenReturn(processName);
    ProfileableClient mockClient = mock(ProfileableClient.class);
    when(mockClient.getProfileableClientData()).thenReturn(mockData);
    return mockClient;
  }

  @NotNull
  private ProfileableClient createMockProfileableClient(Client client) {
    return createMockProfileableClient(client.getClientData().getPid(), client.getClientData().getClientDescription());
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {
    final LinkedBlockingDeque<Common.Event> myEventQueue = new LinkedBlockingDeque<>();
    @Nullable private Thread myEventThread;
    @Nullable private Commands.Command.CommandType myLastCommandType;
    static final ByteString TEST_BYTES = ByteString.copyFromUtf8("Hello");

    @Override
    public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
      responseObserver.onNext(TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getEvents(Transport.GetEventsRequest request, StreamObserver<Common.Event> responseObserver) {
      myEventThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted() || !myEventQueue.isEmpty()) {
          try {
            Common.Event event = myEventQueue.take();
            if (event != null) {
              responseObserver.onNext(event);
            }
          }
          catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        }
        responseObserver.onCompleted();
      });
      myEventThread.start();
    }

    @Override
    public void getBytes(Transport.BytesRequest request, StreamObserver<Transport.BytesResponse> responseObserver) {
      responseObserver.onNext(Transport.BytesResponse.newBuilder().setContents(TEST_BYTES).build());
      responseObserver.onCompleted();
    }

    @Override
    public void execute(Transport.ExecuteRequest request, StreamObserver<Transport.ExecuteResponse> responseObserver) {
      myLastCommandType = request.getCommand().getType();
      responseObserver.onNext(Transport.ExecuteResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    void addEvents(@NotNull Common.Event... events) {
      for (Common.Event event : events) {
        myEventQueue.offer(event);
      }
      while (!myEventQueue.isEmpty()) {
        try {
          // Wait until all events have been sent through.
          Thread.sleep(10);
        }
        catch (InterruptedException exception) {
        }
      }
    }

    void stopEventThread() {
      if (myEventThread != null) {
        myEventThread.interrupt();
      }
    }
  }
}
