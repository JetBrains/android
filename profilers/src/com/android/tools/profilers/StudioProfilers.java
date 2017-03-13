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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.event.EventProfiler;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.network.NetworkProfiler;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The suite of profilers inside Android Studio. This object is responsible for maintaining the information
 * global across all the profilers, device management, process management, current state of the tool etc.
 */
public class StudioProfilers extends AspectModel<ProfilerAspect> implements Updatable {

  public static final int INVALID_PROCESS_ID = -1;

  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;

  public static final int TIMELINE_BUFFER = 1;

  private final ProfilerClient myClient;

  private final ProfilerTimeline myTimeline;

  private final List<StudioProfiler> myProfilers;

  @NotNull
  private final IdeProfilerServices myIdeServices;

  private Map<Profiler.Device, List<Profiler.Process>> myProcesses;

  @Nullable
  private Profiler.Process myProcess;

  private boolean myAgentAttached;

  @Nullable
  private String myPreferredProcessName;

  private Profiler.Device myDevice;

  @Nullable
  private Common.Session mySessionData;

  @NotNull
  private Stage myStage;

  private Updater myUpdater;

  @NotNull
  private RelativeTimeConverter myRelativeTimeConverter;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevices;

  private long myDeviceTimeNs;

  private long myHostTimeNs;

  private boolean myConnected;

  public StudioProfilers(ProfilerClient client, @NotNull IdeProfilerServices ideServices) {
    this(client, ideServices, new FpsTimer(PROFILERS_UPDATE_RATE));
  }

  @VisibleForTesting
  public StudioProfilers(ProfilerClient client, @NotNull IdeProfilerServices ideServices, @NotNull StopwatchTimer timer) {
    myClient = client;
    myIdeServices = ideServices;
    myPreferredProcessName = null;
    myStage = new NullMonitorStage(this);
    myStage.enter();

    myUpdater = new Updater(timer);
    myProfilers = ImmutableList.of(
      new EventProfiler(this),
      new CpuProfiler(this),
      new MemoryProfiler(this),
      new NetworkProfiler(this));

    myRelativeTimeConverter = new RelativeTimeConverter(0);
    myTimeline = new ProfilerTimeline(myRelativeTimeConverter);

    myProcesses = Maps.newHashMap();
    myConnected = false;
    myDevice = null;
    myProcess = null;
    mySessionData = Common.Session.getDefaultInstance();

    myViewAxis = new AxisComponentModel(myTimeline.getViewRange(), TimeAxisFormatter.DEFAULT);
    myViewAxis.setGlobalRange(myTimeline.getDataRange());

    myUpdater.register(myTimeline);
    myUpdater.register(myViewAxis);
    myUpdater.register(this);
  }

  public List<Profiler.Device> getDevices() {
    return Lists.newArrayList(myProcesses.keySet());
  }

  public void setPreferredProcessName(@Nullable String name) {
    myPreferredProcessName = name;
  }

  @Override
  public void update(long elapsedNs) {
    myRefreshDevices += elapsedNs;
    if (myRefreshDevices < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    myRefreshDevices = 0;

    try {
      Profiler.GetDevicesResponse response = myClient.getProfilerClient().getDevices(Profiler.GetDevicesRequest.getDefaultInstance());
      if (!myConnected) {
        this.changed(ProfilerAspect.CONNECTION);
      }

      myConnected = true;
      Set<Profiler.Device> devices = new HashSet<>(response.getDeviceList());
      Map<Profiler.Device, List<Profiler.Process>> newProcesses = new HashMap<>();
      for (Profiler.Device device : devices) {
        Common.Session session = Common.Session.newBuilder()
          .setDeviceSerial(device.getSerial())
          .setBootId(device.getBootId())
          .build();
        Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder().setSession(session).build();
        Profiler.GetProcessesResponse processes = myClient.getProfilerClient().getProcesses(request);

        int lastProcessId = myProcess == null ? 0 : myProcess.getPid();
        List<Profiler.Process> processList = processes.getProcessList()
          .stream()
          .filter(process -> process.getState() == Profiler.Process.State.ALIVE ||
                             process.getPid() == lastProcessId)
          .collect(Collectors.toList());

        newProcesses.put(device, processList);
      }

      if (!newProcesses.equals(myProcesses)) {
        myProcesses = newProcesses;
        // Find and set preferred device
        setDevice(findPreferredDevice());
        setProcess(null);

        // These need to be fired every time the process list changes so that the device/process dropdown always reflects the latest.
        changed(ProfilerAspect.DEVICES);
        changed(ProfilerAspect.PROCESSES);
      }

      // Ping to see if perfa is alive.
      if (myProcess != null) {
        Common.Session session = Common.Session.newBuilder()
          .setDeviceSerial(myDevice.getSerial())
          .setBootId(myDevice.getBootId())
          .build();
        Profiler.AgentStatusRequest statusRequest =
          Profiler.AgentStatusRequest.newBuilder().setProcessId(myProcess.getPid()).setSession(session).build();
        Profiler.AgentStatusResponse statusResponse = myClient.getProfilerClient().getAgentStatus(statusRequest);

        boolean agentAttach = statusResponse.getStatus() == Profiler.AgentStatusResponse.Status.ATTACHED;
        if (myAgentAttached != agentAttach) {
          myAgentAttached = agentAttach;
          changed(ProfilerAspect.AGENT);

          if (agentAttach) {
            getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
          }
        }
      }
    }
    catch (StatusRuntimeException e) {
      // TODO: Clean up this exception, this has the potential to capture some subtle bugs
      // As an example the MemoryProfilerStateTest:testAgentStatusUpdatesObjectSeries depends on this exception being thrown
      // the exception gets thrown due to startMonitor being called on a service the test didn't setup, this seems like an
      // unintentional side effect of the state of the test that sets this class up properly, if we handle the exception elsewhere
      // the test will fail as a different service will run and an UNIMPLEMENTED exception will be thrown.
      myConnected = false;
      this.changed(ProfilerAspect.CONNECTION);
      System.err.println("Cannot find profiler service, retrying...");
    }
  }

  /**
   * Finds and returns the preferred devices depending on the current state of the devices connected and their processes.
   * If the currently selected device is ONLINE and has alive processes that can be profiled, it should remain selected.
   * Online devices (first the ones with alive processes that can profiled) have preference over disconnected/offline ones.
   * Finally, currently selected device (in case its state has changed) has preference over others.
   */
  @Nullable
  private Profiler.Device findPreferredDevice() {
    if (myDevice != null && myDevice.getState().equals(Profiler.Device.State.ONLINE) && deviceHasAliveProcesses(myDevice)) {
      // Current selected device is online and has alive processes that can be profiled. We don't need to change it.
      return myDevice;
    }

    Set<Profiler.Device> devices = myProcesses.keySet();
    Set<Profiler.Device> onlineDevices =
      devices.stream().filter(device -> device.getState().equals(Profiler.Device.State.ONLINE)).collect(Collectors.toSet());
    if (!onlineDevices.isEmpty()) {
      // There are online devices. First try to find a device with alive processes that can be profiled.
      // If cant't find one, return any online device.
      Profiler.Device anyOnlineDevice = onlineDevices.iterator().next();
      return onlineDevices.stream().filter(this::deviceHasAliveProcesses).findAny().orElse(anyOnlineDevice);
    }

    // In case the currently selected device state has changed, it will be represented as a new Profiler.Device object.
    // Therefore, try to find a device with same serial as the selected one. If can't find it, return any device.
    Profiler.Device anyDevice = devices.isEmpty() ? null : devices.iterator().next();
    return myDevice == null ? anyDevice :
           devices.stream().filter(device -> device.getSerial().equals(myDevice.getSerial())).findAny().orElse(anyDevice);
  }

  private boolean deviceHasAliveProcesses(@NotNull Profiler.Device device) {
    List<Profiler.Process> deviceProcesses = myProcesses.get(device);
    if (deviceProcesses == null) {
      return false;
    }
    for (Profiler.Process process : deviceProcesses) {
      if (process.getState().equals(Profiler.Process.State.ALIVE)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Chooses the given device. If the device is not known or null, the first available one will be chosen instead.
   */
  public void setDevice(Profiler.Device device) {
    if (!Objects.equals(device, myDevice)) {
      myDevice = device;
      changed(ProfilerAspect.DEVICES);

      if (myDevice != null) {
        mySessionData = Common.Session.newBuilder()
          .setDeviceSerial(myDevice.getSerial())
          .setBootId(myDevice.getBootId())
          .build();
        Profiler.TimeResponse
          response = myClient.getProfilerClient().getCurrentTime(Profiler.TimeRequest.newBuilder().setSession(mySessionData).build());
        myHostTimeNs = System.nanoTime();
        myDeviceTimeNs = response.getTimestampNs();
      }
      else {
        mySessionData = null;
      }

      // The device has changed, reset the process
      setProcess(null);
    }
  }

  public void setMonitoringStage() {
    setStage(new StudioMonitorStage(this));
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
      if (myDevice != null && myProcess != null &&
          myDevice.getState() == Profiler.Device.State.ONLINE &&
          myProcess.getState() == Profiler.Process.State.ALIVE) {
        myProfilers.forEach(profiler -> profiler.stopProfiling(getSession(), myProcess));
      }

      boolean onlyStateChanged = isSameProcess(myProcess, process);
      myProcess = process;
      changed(ProfilerAspect.PROCESSES);

      if (myDevice != null && myProcess != null &&
          myDevice.getState() == Profiler.Device.State.ONLINE &&
          myProcess.getState() == Profiler.Process.State.ALIVE) {

        // Calculating the current device time, so we can properly estimate how long the timeline should be when selecting
        // the running application.
        // Due to plug/unplug changing the application start time, when you plug/unplug the delta time is small.
        // Without getting the current time here, if you switch to an application that has been running for a while, the timeline will
        // not match the profiling data.
        long currentDeviceTime = ((System.nanoTime() - myHostTimeNs) + myDeviceTimeNs);
        long runTime = currentDeviceTime - myProcess.getStartTimestampNs();
        myRelativeTimeConverter = new RelativeTimeConverter(myProcess.getStartTimestampNs() - TimeUnit.SECONDS.toNanos(TIMELINE_BUFFER));
        myTimeline.reset(myRelativeTimeConverter, runTime);
        myProfilers.forEach(profiler -> profiler.startProfiling(getSession(), myProcess));
      }
      else {
        myTimeline.setIsPaused(true);
      }

      if (!onlyStateChanged) {
        if (myProcess == null) {
          setStage(new NullMonitorStage(this));
        } else {
          setStage(new StudioMonitorStage(this));
        }
        myIdeServices.getFeatureTracker().trackProfilingStarted();
      }
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
        if (process.getName().equals(myPreferredProcessName) && process.getState() == Profiler.Process.State.ALIVE) {
          return process;
        }
      }
    }
    // Next, prefer the one previously used, either selected by user or automatically (even if the process has switched states)
    if (myProcess != null) {
      for (Profiler.Process process : processes) {
        if (isSameProcess(myProcess, process)) {
          return process;
        }
      }
    }
    // No preferred candidate. Choose a new process.
    return processes.get(0);
  }

  private boolean isSameProcess(@Nullable Profiler.Process process1, @Nullable Profiler.Process process2) {
    return process1 != null &&
           process2 != null &&
           process1.getPid() == process2.getPid() && process1.getName().equals(process2.getName());
  }

  public List<Profiler.Process> getProcesses() {
    List<Profiler.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  @NotNull
  public Stage getStage() {
    return myStage;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  public int getProcessId() {
    return myProcess != null ? myProcess.getPid() : INVALID_PROCESS_ID;
  }

  @Nullable
  public Common.Session getSession() {
    return mySessionData;
  }

  public void setStage(@NotNull Stage stage) {
    myStage.exit();
    getTimeline().getSelectionRange().clear();
    myStage = stage;
    myStage.enter();
    this.changed(ProfilerAspect.STAGE);
  }

  @NotNull
  public ProfilerTimeline getTimeline() {
    return myTimeline;
  }

  @NotNull
  public RelativeTimeConverter getRelativeTimeConverter() {
    return myRelativeTimeConverter;
  }

  public Profiler.Device getDevice() {
    return myDevice;
  }

  public Profiler.Process getProcess() {
    return myProcess;
  }

  public boolean isAgentAttached() {
    return myAgentAttached;
  }

  public List<StudioProfiler> getProfilers() {
    return myProfilers;
  }

  public ProfilerMode getMode() {
    return myStage.getProfilerMode();
  }

  public void modeChanged() {
    changed(ProfilerAspect.MODE);
  }

  @NotNull
  public IdeProfilerServices getIdeServices() {
    return myIdeServices;
  }

  public Updater getUpdater() {
    return myUpdater;
  }

  public AxisComponentModel getViewAxis() {
    return myViewAxis;
  }

  public List<Class<? extends Stage>> getDirectStages() {
    return ImmutableList.of(
      CpuProfilerStage.class,
      MemoryProfilerStage.class,
      NetworkProfilerStage.class
    );
  }

  @NotNull
  public Class<? extends Stage> getStageClass() {
    return myStage.getClass();
  }

  // TODO: Unify with how monitors expand.
  public void setNewStage(Class<? extends Stage> clazz) {
    try {
      Constructor<? extends Stage> constructor = clazz.getConstructor(StudioProfilers.class);
      Stage stage = constructor.newInstance(this);
      setStage(stage);
    }
    catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      // will not happen
    }
  }
}
