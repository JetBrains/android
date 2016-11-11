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
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.network.NetworkProfiler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.StatusRuntimeException;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
final public class StudioProfilers extends AspectModel<ProfilerAspect> {

  private final ProfilerClient myClient;

  private Range mySelectionRangeUs;
  private final Range myViewRangeUs;
  private final Range myDataRangUs;
  private final List<StudioProfiler> myProfilers;

  private Map<Profiler.Device, List<Profiler.Process>> myProcesses;

  private Profiler.Device myDevice;
  private long myDeviceDeltaNs;
  private Profiler.Process myProcess;
  private boolean myConnected;

  private Stage myStage;

  public StudioProfilers(ProfilerClient service) {
    myClient = service;
    myStage = null;
    myProfilers = ImmutableList.of(
      new CpuProfiler(this),
      new NetworkProfiler(this));

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
        long nowNs = System.nanoTime();
        if (!myConnected) {
          Profiler.TimesResponse times = myClient.getProfilerClient().getTimes(Profiler.TimesRequest.getDefaultInstance());
          long deviceNowNs = times.getTimestampNs();
          long deviceNowUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
          myDeviceDeltaNs = deviceNowNs - nowNs;
          myDataRangUs.set(deviceNowUs, deviceNowUs);
          this.changed(ProfilerAspect.CONNECTION);
        }

        long deviceNowNs = nowNs + myDeviceDeltaNs;
        long deviceNowUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
        myViewRangeUs.set(deviceNowUs - TimeUnit.SECONDS.toMicros(10), deviceNowUs);
        myDataRangUs.setMax(deviceNowUs);

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
          // Attempt to choose the currently profiled device and process
          setDevice(myDevice);
          setProcess(myProcess);
        }
      }
      catch (StatusRuntimeException e) {
        myConnected = false;
        this.changed(ProfilerAspect.CONNECTION);
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


  /**
   * Chooses the given device. If the device is not known or null, the first available one will be chosen instead.
   */
  public void setDevice(Profiler.Device device) {
      Set<Profiler.Device> devices = myProcesses.keySet();
      if (!devices.contains(device)) {
        // Device no longer exists, or is unknown. Choose a device from the available ones if there is one.
        device  = devices.isEmpty() ? null : devices.iterator().next();
      }
      if ((device != null && !device.equals(myDevice)) || myDevice != null) {
        myDevice = device;
        changed(ProfilerAspect.DEVICES);

        // The device has changed, reset the process
        setProcess(null);
      }
  }


  /**
   * Chooses the given process. If the process is not known or null, the first available one will be chosen instead.
   */
  public void setProcess(Profiler.Process process) {
    List<Profiler.Process> processes = myProcesses.get(myDevice);
    if (processes == null || !processes.contains(process)) {
      // The process doesn't belong to the current device. Choose a new process.
      process = (processes == null || processes.isEmpty()) ? null : processes.iterator().next();
    }
    if ((process != null && !process.equals(myProcess)) || myProcess != null) {

      if (myProcess != null) {
        myProfilers.forEach(profiler -> profiler.stopProfiling(myProcess));
      }

      myProcess = process;
      changed(ProfilerAspect.PROCESSES);

      if (myProcess != null) {
        myProfilers.forEach(profiler -> profiler.startProfiling(myProcess));
      }
      setStage(new StudioMonitorStage(this));
    }
  }

  public boolean isConnected() {
    return myConnected;
  }

  public List<Profiler.Process> getProcesses() {
    List<Profiler.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  public Stage getStage() {
    return myStage;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  public int getProcessId() {
    return myProcess.getPid();
  }

  public void setStage(Stage stage) {
    if (myStage != null) {
      myStage.exit();
    }
    myStage = stage;
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
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


  public List<StudioProfiler> getProfilers() {
    return myProfilers;
  }
}
