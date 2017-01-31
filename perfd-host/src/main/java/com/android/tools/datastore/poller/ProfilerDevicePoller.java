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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.Device;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProfilerDevicePoller extends PollRunner {
  private ProfilerTable myTable;
  private DataStoreService myService;
  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myPollingService;

  public ProfilerDevicePoller(DataStoreService service,
                              ProfilerTable table,
                              ProfilerServiceGrpc.ProfilerServiceBlockingStub pollingService) {
    super(TimeUnit.SECONDS.toNanos(1));
    myTable = table;
    myService = service;
    myPollingService = pollingService;
  }

  @Override
  public void poll() {
    try {
      Profiler.GetDevicesRequest devicesRequest = Profiler.GetDevicesRequest.newBuilder().build();
      Profiler.GetDevicesResponse deviceResponse = myPollingService.getDevices(devicesRequest);
      for (Device device : deviceResponse.getDeviceList()) {
        myTable.insertOrUpdateDevice(device);
        Profiler.GetProcessesRequest processesRequest =
          Profiler.GetProcessesRequest.newBuilder().setDeviceSerial(device.getSerial()).build();
        Profiler.GetProcessesResponse processesResponse = myPollingService.getProcesses(processesRequest);
        for (Profiler.Process process : processesResponse.getProcessList()) {
          myTable.insertOrUpdateProcess(device.getSerial(), process);
        }
      }
    }
    catch (StatusRuntimeException ex) {
      myService.disconnect((ManagedChannel)myPollingService.getChannel());
    }
  }
}
