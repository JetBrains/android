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
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.ProfilerTable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.Device;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.StatusRuntimeException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ProfilerDevicePoller extends PollRunner implements DataStoreTable.DataStoreTableErrorCallback {
  private static final class DeviceData {
    public final Profiler.Device device;
    public final Set<Profiler.Process> processes = new HashSet<>();
    public DeviceData(Device device) {
      this.device = device;
    }
  }

  private final ProfilerTable myTable;
  private final DataStoreService myService;
  private final ProfilerServiceGrpc.ProfilerServiceBlockingStub myPollingService;
  private final Map<Common.Session, DeviceData> myDevices = new HashMap<>();

  public ProfilerDevicePoller(DataStoreService service,
                              ProfilerTable table,
                              ProfilerServiceGrpc.ProfilerServiceBlockingStub pollingService) {
    super(TimeUnit.SECONDS.toNanos(1));
    myTable = table;
    myService = service;
    myPollingService = pollingService;
  }

  @Override
  public void onDataStoreError(Throwable t) {
    if (myTable.isClosed()) {
      // This can happend if we encounter a database error. The database error will be logged at time of error,
      // and we will stop all polling here.
      disconnect();
    }
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
        DeviceData deviceData = myDevices.computeIfAbsent(session, s -> new DeviceData(device));

        myService.setConnectedClients(session, myPollingService.getChannel());
        Profiler.GetProcessesRequest processesRequest =
          Profiler.GetProcessesRequest.newBuilder().setSession(session).build();
        Profiler.GetProcessesResponse processesResponse = myPollingService.getProcesses(processesRequest);

        // Gather the list of last known active processes.
        Set<Profiler.Process> liveProcesses = new HashSet<>();

        for (Profiler.Process process : processesResponse.getProcessList()) {
          myTable.insertOrUpdateProcess(session, process);
          liveProcesses.add(process);

          // Remove any new processes from the list of last known processes.
          // Any processes that remain in the list is our list of dead processes.
          deviceData.processes.remove(process);

          Profiler.AgentStatusRequest agentStatusRequest =
            Profiler.AgentStatusRequest.newBuilder().setProcessId(process.getPid()).setSession(session).build();
          Profiler.AgentStatusResponse agentStatusResponse = myPollingService.getAgentStatus(agentStatusRequest);
          myTable.updateAgentStatus(session, process, agentStatusResponse);
        }

        //TODO: think about moving this to the device proxy.
        // At this point, deviceData.processes only processes that don't match active processes,
        // meaning they were just killed
        killProcesses(session, deviceData.processes);
        deviceData.processes.clear();
        deviceData.processes.addAll(liveProcesses);
      }
    }
    catch (StatusRuntimeException ex) {
      // We expect this to get called when connection to the device is lost.
      // To properly clean up the state we first set all ALIVE processes to DEAD
      // then we disconnect the channel.
      disconnect();
    }
  }

  private void disconnect() {
    for (Map.Entry<Common.Session, DeviceData> entry : myDevices.entrySet()) {
      Common.Session session = entry.getKey();
      DeviceData activeDevice = entry.getValue();
      disconnectDevice(activeDevice.device);
      killProcesses(session, activeDevice.processes);
      myService.disconnect(session);
    }
    myDevices.clear();
  }

  private void disconnectDevice(Device device) {
    Device disconnectedDevice = device.toBuilder().setState(Device.State.DISCONNECTED).build();
    myTable.insertOrUpdateDevice(disconnectedDevice);
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
