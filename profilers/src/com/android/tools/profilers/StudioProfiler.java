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

import com.android.tools.adtui.Range;
import com.android.tools.profiler.proto.Profiler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.StatusRuntimeException;

import java.util.*;

final public class StudioProfiler extends AspectModel<Aspect> {

  private final ProfilerClient myClient;

  private Range mySelectionRangeUs;
  private final Range myViewRangeUs;
  private final Range myDataRangUs;

  private Map<Profiler.Device, List<Profiler.Process>> myProcesses;
  private Profiler.Device myDevice;
  private Profiler.Process myProcess;
  private boolean myConnected;
  private StudioProfilerStage myStage;
  private long myDeviceDeltaUs;

  public StudioProfiler(ProfilerClient service) {
    myClient = service;
    myStage = null;

    myViewRangeUs = new Range();
    myDataRangUs = new Range();
    mySelectionRangeUs = null;

    myProcesses = Maps.newHashMap();
    myConnected = false;
    myDevice = null;
    myProcess = null;

    new Thread(this::run, "Profiler poller").start();
  }

  public List<Profiler.Device> getDevices() {
    return Lists.newArrayList(myProcesses.keySet());
  }

  /**
   * Polls the server for new devices/processes.
   * TODO: Investigate a streaming notification service.
   */
  private void run() {
    // TODO: Allow clean exit of this thread.
    while (true) {
      try {
        Profiler.GetDevicesResponse response = myClient.getProfilerClient().getDevices(Profiler.GetDevicesRequest.getDefaultInstance());
        long now = System.nanoTime();
        if (!myConnected) {
          Profiler.TimesResponse times = myClient.getProfilerClient().getTimes(Profiler.TimesRequest.getDefaultInstance());
          long deviceNow = times.getTimestampNs();
          myDeviceDeltaUs = deviceNow - now;
          myDataRangUs.set(deviceNow / 1000, deviceNow / 1000);
          this.changed(Aspect.CONNECTION);
        }

        long deviceNow = now + myDeviceDeltaUs;
        myViewRangeUs.set(deviceNow / 1000 - 10000000, deviceNow / 1000);
        myDataRangUs.setMax(deviceNow / 1000);

        myConnected = true;
        Set<Profiler.Device> devices = new HashSet<>(response.getDeviceList());
        Map<Profiler.Device, List<Profiler.Process>> newProcesses = new HashMap<>();
        for (Profiler.Device device : devices) {
          Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder().setSerial(device.getSerial()).build();
          Profiler.GetProcessesResponse processes = myClient.getProfilerClient().getProcesses(request);
          newProcesses.put(device, processes.getProcessList());
        }
        if (!newProcesses.equals(myProcesses)) {
          myProcesses = newProcesses;

          if (myDevice == null && !devices.isEmpty()) {
            myDevice = devices.iterator().next();
          }
          else if (myDevice != null && !devices.contains(myDevice)) {
            myDevice = null;
          }

          if (myDevice != null && myProcess == null && !myProcesses.get(myDevice).isEmpty()) {
            myProcess = myProcesses.get(myDevice).iterator().next();
            setStage(new StudioMonitor(this));
          }

          this.changed(Aspect.DEVICES);
        }
      }
      catch (StatusRuntimeException e) {
        myConnected = false;
        this.changed(Aspect.CONNECTION);
        System.err.println("Cannot find profiler service, retrying...");
      }
      try {
        Thread.sleep(1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean isConnected() {
    return myConnected;
  }

  public List<Profiler.Process> getProcesses() {
    List<Profiler.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  public StudioProfilerStage getStage() {
    return myStage;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  public int getProcessId() {
    return myProcess.getPid();
  }

  public void setStage(StudioMonitor stage) {
    if (myStage != null) {
      myStage.exit();
    }
    myStage = stage;
    myStage.enter();
    this.changed(Aspect.STAGE);
  }

  public Range getViewRange() {
    return myViewRangeUs;
  }

  public Profiler.Device getDevice() {
    return myDevice;
  }

  public Profiler.Process getProcess() {
    return myProcess;
  }

  public void setDevice(Profiler.Device device) {
    myDevice = device;
  }

  public void setProcess(Profiler.Process process) {
    if (!myProcess.equals(process)) {
      myProcess = process;
      setStage(new StudioMonitor(this));
    }
  }
}
