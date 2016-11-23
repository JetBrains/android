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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.jetbrains.annotations.NotNull;

public class ProfilerClient {

  @NotNull private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myProfilerClient;
  @NotNull private final MemoryServiceGrpc.MemoryServiceBlockingStub myMemoryClient;
  @NotNull private final CpuServiceGrpc.CpuServiceBlockingStub myCpuClient;
  @NotNull private final NetworkServiceGrpc.NetworkServiceBlockingStub myNetworkClient;
  @NotNull private final EventServiceGrpc.EventServiceBlockingStub myEventClient;
  @NotNull private final EnergyServiceGrpc.EnergyServiceBlockingStub myEnergyClient;

  public ProfilerClient(String name) {
    // Stash the currently set context class loader so ManagedChannelProvider can find an appropriate implementation.
    ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(ManagedChannelBuilder.class.getClassLoader());
    ManagedChannel channel = InProcessChannelBuilder.forName(name).usePlaintext(true).build();
    Thread.currentThread().setContextClassLoader(stashedContextClassLoader);

    myProfilerClient = ProfilerServiceGrpc.newBlockingStub(channel);
    myMemoryClient = MemoryServiceGrpc.newBlockingStub(channel);
    myCpuClient = CpuServiceGrpc.newBlockingStub(channel);
    myNetworkClient = NetworkServiceGrpc.newBlockingStub(channel);
    myEventClient = EventServiceGrpc.newBlockingStub(channel);
    myEnergyClient = EnergyServiceGrpc.newBlockingStub(channel);
  }

  @NotNull
  public ProfilerServiceGrpc.ProfilerServiceBlockingStub getProfilerClient() {
    return myProfilerClient;
  }

  @NotNull
  public MemoryServiceGrpc.MemoryServiceBlockingStub getMemoryClient() {
    return myMemoryClient;
  }

  @NotNull
  public CpuServiceGrpc.CpuServiceBlockingStub getCpuClient() {
    return myCpuClient;
  }

  @NotNull
  public NetworkServiceGrpc.NetworkServiceBlockingStub getNetworkClient() {
    return myNetworkClient;
  }

  @NotNull
  public EventServiceGrpc.EventServiceBlockingStub getEventClient() {
    return myEventClient;
  }

  @NotNull
  public EnergyServiceGrpc.EnergyServiceBlockingStub getEnergyClient() {
    return myEnergyClient;
  }
}

