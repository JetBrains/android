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

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.event.EventProfiler;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.network.NetworkProfiler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
final public class StudioProfilers extends AspectModel<ProfilerAspect> {
  public static final int INVALID_PROCESS_ID = -1;

  private final ProfilerClient myClient;
  @Nullable
  private String myPreferredProcessName;

  private Range mySelectionRangeUs;
  private final Range myViewRangeUs;
  private final Range myDataRangUs;
  private final List<StudioProfiler> myProfilers;

  private Map<Profiler.Device, List<Profiler.Process>> myProcesses;

  private Profiler.Device myDevice;
  private long myDeviceDeltaNs;
  private long myDeviceStartUs;

  @Nullable
  private Profiler.Process myProcess;

  private boolean myConnected;

  private Stage myStage;

  public StudioProfilers(ProfilerClient service) {
    myClient = service;
    myPreferredProcessName = null;
    myStage = null;
    myProfilers = ImmutableList.of(
      new EventProfiler(this),
      new CpuProfiler(this),
      new MemoryProfiler(this),
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

  public void setPreferredProcessName(@Nullable String name) {
    myPreferredProcessName = name;
  }

  /**
   * Polls the server for new devices/processes.
   * TODO: Investigate a streaming notification service.
   */
  private void run() {
    // TODO: Allow clean exit of this thread.
    try {
      while (true) {
        try {
          Profiler.GetDevicesResponse response = myClient.getProfilerClient().getDevices(Profiler.GetDevicesRequest.getDefaultInstance());
          long nowNs = System.nanoTime();
          if (!myConnected) {
            // We were not connected to a service, we don't know yet whether there are devices available.
            // TODO: modify get times to be on the selected device and separate
            // device polling, from streaming mode.
            Profiler.TimesResponse times = myClient.getProfilerClient().getTimes(Profiler.TimesRequest.getDefaultInstance());
            long deviceNowNs = times.getTimestampNs();
            myDeviceStartUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
            myDeviceDeltaNs = deviceNowNs - nowNs;
            myDataRangUs.set(myDeviceStartUs, myDeviceStartUs);
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
            setProcess(null);

            changed(ProfilerAspect.DEVICES);
            changed(ProfilerAspect.PROCESSES);
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
    catch (Throwable t) {
      System.err.println("Run is ending....");
    }
  }


  /**
   * Chooses the given device. If the device is not known or null, the first available one will be chosen instead.
   */
  public void setDevice(Profiler.Device device) {
    Set<Profiler.Device> devices = myProcesses.keySet();
    if (!devices.contains(device)) {
      // Device no longer exists, or is unknown. Choose a device from the available ones if there is one.
      device = devices.isEmpty() ? null : devices.iterator().next();
    }
    if (!Objects.equals(device, myDevice)) {
      myDevice = device;
      changed(ProfilerAspect.DEVICES);

      // The device has changed, reset the process
      setProcess(null);
    }
  }

  /**
   * Chooses a process, and starts profiling it if not already (and stops profiling the previous
   * one).
   *
   * @param process the process that will be selected. If it is null, a process will be determined
   *                automatically by heuristics.
   */
  public void setProcess(@Nullable Profiler.Process process) {
    List<Profiler.Process> processes = myProcesses.get(myDevice);
    if (process == null || processes == null || !processes.contains(process)) {
      process = getPreferredProcess(processes);
    }
    if (!Objects.equals(process, myProcess)) {
      if (myDevice != null && myProcess != null) {
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

  /**
   * Chooses a process among all potential candidates starting from the project's app process,
   * and then the one previously used. If no candidate is available, return the first available
   * process.
   */
  @Nullable
  private Profiler.Process getPreferredProcess(List<Profiler.Process> processes) {
    if (processes == null || processes.isEmpty()) {
      return null;
    }
    // Prefer the project's app if available.
    if (myPreferredProcessName != null) {
      for (Profiler.Process process : processes) {
        if (process.getName().equals(myPreferredProcessName)) {
          return process;
        }
      }
    }
    // Next, prefer the one previously used, either selected by user or automatically.
    if (myProcess != null && processes.contains(myProcess)) {
      return myProcess;
    }
    // No preferred candidate. Choose a new process.
    return processes.get(0);
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
    return myProcess != null ? myProcess.getPid() : INVALID_PROCESS_ID;
  }

  public void setStage(Stage stage) {
    if (myStage != null) {
      myStage.exit();
    }
    myStage = stage;
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public Range getSelectionRange() {
    return mySelectionRangeUs;
  }

  @NotNull public Range getViewRange() {
    return myViewRangeUs;
  }

  @NotNull public Range getDataRange() {
    return myDataRangUs;
  }

  public long getDeviceStartUs() {
    return myDeviceStartUs;
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

  public <T extends StudioProfiler> T getProfiler(Class<T> clazz) {
    for (StudioProfiler profiler : myProfilers) {
      if (clazz.isInstance(profiler)) {
        return clazz.cast(profiler);
      }
    }
    return null;
  }
}
