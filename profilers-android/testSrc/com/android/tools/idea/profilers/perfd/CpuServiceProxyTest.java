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
import com.android.tools.idea.profilers.LegacyCpuTraceProfiler;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CpuServiceProxyTest {
  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(1, "API"));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(new Client[0]);
    ManagedChannel channel = InProcessChannelBuilder.forName("CpuServiceProxyTest").build();
    CpuServiceProxy proxy = new CpuServiceProxy(mockDevice, channel, new FakeLegacyCpuTraceProfiler());

    ServerServiceDefinition serverDefinition = proxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = CpuServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertThat(definedMethods.size()).isEqualTo(allMethods.size());
    definedMethods.containsAll(allMethods);
  }

  private static class FakeLegacyCpuTraceProfiler implements LegacyCpuTraceProfiler {
    @Override
    public CpuProfilingAppStartResponse startProfilingApp(CpuProfilingAppStartRequest request) {
      return CpuProfilingAppStartResponse.newBuilder().build();
    }

    @Override
    public CpuProfilingAppStopResponse stopProfilingApp(CpuProfilingAppStopRequest request) {
      return CpuProfilingAppStopResponse.newBuilder().build();
    }

    @Override
    public ProfilingStateResponse checkAppProfilingState(ProfilingStateRequest request) {
      return ProfilingStateResponse.newBuilder().build();
    }
  }
}