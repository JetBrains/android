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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProfilerServiceProxyTest {

  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    IDevice mockDevice = createMockDevice();
    ProfilerServiceProxy proxy = new ProfilerServiceProxy(mockDevice, startNamedChannel("testBindServiceContainsAllMethods"));

    ServerServiceDefinition serverDefinition = proxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = ProfilerServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertEquals(allMethods.size(), definedMethods.size());
    definedMethods.containsAll(allMethods);
  }

  @Test
  public void testUnknownDeviceLabel() throws Exception {
    IDevice mockDevice = createMockDevice();
    Profiler.Device profilerDevice = ProfilerServiceProxy.profilerDeviceFromIDevice(mockDevice);
    assertEquals("Unknown", profilerDevice.getModel());
  }

  @Test
  public void testUnknownEmulatorLabel() throws Exception {
    IDevice mockDevice = createMockDevice();
    when(mockDevice.isEmulator()).thenReturn(true);
    when(mockDevice.getAvdName()).thenReturn(null);
    Profiler.Device profilerDevice = ProfilerServiceProxy.profilerDeviceFromIDevice(mockDevice);

    assertEquals("Unknown", profilerDevice.getModel());
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
  private IDevice createMockDevice() {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(1, "API"));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(new Client[0]);
    when(mockDevice.getState()).thenReturn(IDevice.DeviceState.ONLINE);
    return mockDevice;
  }

  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
    @Override
    public void getDevices(Profiler.GetDevicesRequest request, StreamObserver<Profiler.GetDevicesResponse> responseObserver) {
      responseObserver.onNext(Profiler.GetDevicesResponse.newBuilder().addDevice(Profiler.Device.newBuilder()
                                                                                   .setSerial("Serial")
                                                                                   .setBootId("Boot")
                                                                                   .build()).build());
      responseObserver.onCompleted();
    }
  }
}