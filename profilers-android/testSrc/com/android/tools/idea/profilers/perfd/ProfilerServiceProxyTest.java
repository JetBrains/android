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

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.internal.ServerImpl;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProfilerServiceProxyTest {

  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    ProfilerServiceProxy proxy = new ProfilerServiceProxy(mockDevice, startNamedChannel("testBindServiceContainsAllMethods"));

    ServerServiceDefinition serverDefinition = proxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = ProfilerServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertThat(definedMethods.size()).isEqualTo(allMethods.size());
    definedMethods.containsAll(allMethods);
  }

  @Test
  public void testUnknownDeviceLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    Common.Device profilerDevice = ProfilerServiceProxy.profilerDeviceFromIDevice(mockDevice);
    assertThat(profilerDevice.getModel()).isEqualTo("Unknown");
  }

  @Test
  public void testUnknownEmulatorLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    when(mockDevice.isEmulator()).thenReturn(true);
    when(mockDevice.getAvdName()).thenReturn(null);
    Common.Device profilerDevice = ProfilerServiceProxy.profilerDeviceFromIDevice(mockDevice);

    assertThat(profilerDevice.getModel()).isEqualTo("Unknown");
  }

  @Test
  public void testClientsWithNullDescriptionsNotCached() throws Exception {
    Client client1 = createMockClient(1, "test1", "testClientDescription");
    Client client2 = createMockClient(2, "test2", null);
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client1, client2});

    ProfilerServiceProxy proxy = new ProfilerServiceProxy(mockDevice, startNamedChannel("testClientsWithNullDescriptionsNotAdded"));
    Map<Client, Common.Process> cachedProcesses = proxy.getCachedProcesses();
    assertThat(cachedProcesses.size()).isEqualTo(1);
    Map.Entry<Client, Common.Process> cachedProcess = cachedProcesses.entrySet().iterator().next();
    assertThat(cachedProcess.getKey()).isEqualTo(client1);
    assertThat(cachedProcess.getValue().getPid()).isEqualTo(1);
    assertThat(cachedProcess.getValue().getName()).isEqualTo("testClientDescription");
    assertThat(cachedProcess.getValue().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(cachedProcess.getValue().getAbiCpuArch()).isEqualTo(SdkConstants.CPU_ARCH_ARM);
  }

  /**
   * @param uniqueName Name should be unique across tests.
   */
  private ManagedChannel startNamedChannel(String uniqueName) throws IOException {
    InProcessServerBuilder builder = InProcessServerBuilder.forName(uniqueName);
    builder.addService(new FakeProfilerService());
    ServerImpl server = builder.build();
    server.start();

    return InProcessChannelBuilder.forName(uniqueName).build();
  }

  @NotNull
  private IDevice createMockDevice(int version, @NotNull Client[] clients) {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(version, "API"));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(clients);
    when(mockDevice.getState()).thenReturn(IDevice.DeviceState.ONLINE);
    when(mockDevice.getAbis()).thenReturn(Collections.singletonList("armeabi"));
    return mockDevice;
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

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(Common.Device.newBuilder()
                                                                                   .setSerial("Serial")
                                                                                   .setBootId("Boot")
                                                                                   .build()).build());
      responseObserver.onCompleted();
    }

    @Override
    public void getCurrentTime(Profiler.TimeRequest request, StreamObserver<Profiler.TimeResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}