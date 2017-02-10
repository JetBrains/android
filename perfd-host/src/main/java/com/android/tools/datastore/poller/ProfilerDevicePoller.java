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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.Device;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import com.google.common.collect.Maps;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ProfilerDevicePoller extends PollRunner {
  private ProfilerTable myTable;
  private DataStoreService myService;
  private ProfilerServiceGrpc.ProfilerServiceBlockingStub myPollingService;
  private Map<Common.Session, Set<Profiler.Process>> myActiveProcesses = new HashMap<>();

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
        // TODO Store off session, and if it changes fix any session specific data in the database.
        Common.Session session = Common.Session.newBuilder()
          .setBootId(device.getBootId())
          .setDeviceSerial(device.getSerial())
          .build();
        myService.setConnectedClients(session, (ManagedChannel)myPollingService.getChannel());
        Profiler.GetProcessesRequest processesRequest =
          Profiler.GetProcessesRequest.newBuilder().setSession(session).build();
        Profiler.GetProcessesResponse processesResponse = myPollingService.getProcesses(processesRequest);
        // Gather the list of last known active processes.
        Set<Profiler.Process> deadProcesses = myActiveProcesses.containsKey(session) ? myActiveProcesses.get(session) : new HashSet<>();
        Set<Profiler.Process> newProcesses  = new HashSet<>();
        for (Profiler.Process process : processesResponse.getProcessList()) {
          myTable.insertOrUpdateProcess(session, process);
          newProcesses.add(process);
          // Remove any new processes from the list of last known processes.
          // Any processes that remain in the list is our list of dead processes.
          deadProcesses.remove(process);
        }

        //TODO: think about moving this to the device proxy.
        myActiveProcesses.put(session, newProcesses);
        killProcesses(session, deadProcesses);
      }
    }
    catch (StatusRuntimeException ex) {
      // We expect this to get called when connection to the device is lost.
      // To properly clean up the state we first set all ALIVE processes to DEAD
      // then we disconnect the channel.
      for(Common.Session session : myActiveProcesses.keySet()) {
        killProcesses(session, myActiveProcesses.get(session));
        myService.disconnect(session);
      }
    }
  }

  private void killProcesses(Common.Session session, Set<Profiler.Process> processes) {
    for (Profiler.Process process : processes) {
      Profiler.Process updatedProcess = process.toBuilder()
        .setState(Profiler.Process.State.DEAD)
        .build();
      myTable.insertOrUpdateProcess(session, updatedProcess);
    }
  }
}
