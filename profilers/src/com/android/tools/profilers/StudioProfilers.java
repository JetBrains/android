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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profilers.cpu.CpuProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.energy.EnergyProfiler;
import com.android.tools.profilers.energy.EnergyProfilerStage;
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
  /**
   * The number of updates per second our simulated object models receive.
   */
  public static final int PROFILERS_UPDATE_RATE = 60;

  private final ProfilerClient myClient;

  private final ProfilerTimeline myTimeline;

  private final List<StudioProfiler> myProfilers;

  @NotNull
  private final IdeProfilerServices myIdeServices;

  /**
   * Processes from devices come from the latest update, and are filtered to include only ALIVE ones and {@code myProcess}.
   */
  private Map<Common.Device, List<Common.Process>> myProcesses;

  @NotNull private final SessionsManager mySessionsManager;

  @Nullable
  private Common.Process myProcess;

  private AgentStatusResponse.Status myAgentStatus;

  @Nullable
  private String myPreferredProcessName;

  private Common.Device myDevice;

  /**
   * The session that is currently selected.
   */
  @NotNull
  private Common.Session mySession;

  @NotNull
  private Stage myStage;

  private Updater myUpdater;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevices;

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
    ImmutableList.Builder<StudioProfiler> profilersBuilder = new ImmutableList.Builder<>();
    profilersBuilder.add(new EventProfiler(this));
    profilersBuilder.add(new CpuProfiler(this));
    profilersBuilder.add(new MemoryProfiler(this));
    profilersBuilder.add(new NetworkProfiler(this));
    if (myIdeServices.getFeatureConfig().isEnergyProfilerEnabled()) {
      profilersBuilder.add(new EnergyProfiler(this));
    }
    myProfilers = profilersBuilder.build();

    myTimeline = new ProfilerTimeline();
    myTimeline.getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      if (!myTimeline.getSelectionRange().isEmpty()) {
        myTimeline.setStreaming(false);
      }
    });

    myProcesses = Maps.newHashMap();
    myDevice = null;
    myProcess = null;

    // TODO: StudioProfilers initalizes with a default session, which a lot of tests now relies on to avoid a NPE.
    // We should clean all the tests up to either have StudioProfilers create a proper session first or handle the null cases better.
    mySession = Common.Session.getDefaultInstance();
    mySessionsManager = new SessionsManager(this);
    mySessionsManager.addDependency(this).onChange(SessionAspect.SELECTED_SESSION, this::sessionsChanged);

    myViewAxis = new AxisComponentModel(myTimeline.getViewRange(), TimeAxisFormatter.DEFAULT);
    myViewAxis.setGlobalRange(myTimeline.getDataRange());

    myUpdater.register(myTimeline);
    myUpdater.register(myViewAxis);
    myUpdater.register(this);
  }

  public boolean isStopped() {
    return !myUpdater.isRunning();
  }

  public void stop() {
    if (isStopped()) {
      // Profiler is already stopped. Nothing to do. Ideally, this method shouldn't be called when the profiler is already stopped.
      // However, some exceptions might be thrown when listeners are notified about ProfilerAspect.STAGE aspect change and react
      // accordingly. In this case, we could end up with an inconsistent model and allowing to try to call stop and notify the listeners
      // again can only make it worse. Therefore, we return early to avoid making the model problem bigger.
      return;
    }
    // The following line can't throw an exception, will stop the updater's timer and guarantees future calls to isStopped() return true.
    myUpdater.stop();
    // The following lines trigger aspect changes and, therefore, can make many models to update. That might cause an exception to be thrown
    // and make some models inconsistent. In this case, we want future calls to this method to return early, as we can only make the
    // inconsistency worse if we call these lines again.
    setDevice(null);
    changed(ProfilerAspect.STAGE);
  }

  public List<Common.Device> getDevices() {
    return Lists.newArrayList(myProcesses.keySet());
  }

  /**
   * Tells the profiler to select and profile the process of the same name next time it is detected.
   */
  public void setPreferredProcessName(@Nullable String name) {
    myPreferredProcessName = name;
    // Checks whether we can switch immediately if the process is already there.
    setProcess(null);
  }

  @Override
  public void update(long elapsedNs) {
    myRefreshDevices += elapsedNs;
    if (myRefreshDevices < TimeUnit.SECONDS.toNanos(1)) {
      return;
    }
    myRefreshDevices = 0;

    try {
      GetDevicesResponse response = myClient.getProfilerClient().getDevices(GetDevicesRequest.getDefaultInstance());
      Set<Common.Device> devices = new HashSet<>(response.getDeviceList());
      Map<Common.Device, List<Common.Process>> newProcesses = new HashMap<>();
      for (Common.Device device : devices) {
        GetProcessesRequest request = GetProcessesRequest.newBuilder().setDeviceId(device.getDeviceId()).build();
        GetProcessesResponse processes = myClient.getProfilerClient().getProcesses(request);

        int lastProcessId = myProcess == null ? 0 : myProcess.getPid();
        List<Common.Process> processList = processes.getProcessList()
          .stream()
          .filter(process -> process.getState() == Common.Process.State.ALIVE ||
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

      mySessionsManager.update();

      // A heartbeat event may not have been sent by perfa when we first profile an app, here we keep pinging the status and
      // fire the corresponding change and tracking events.
      if (isSessionAlive(mySession)) {
        AgentStatusResponse.Status agentStatus = getAgentStatus();
        if (myAgentStatus != agentStatus) {
          myAgentStatus = agentStatus;
          changed(ProfilerAspect.AGENT);
          if (myAgentStatus == AgentStatusResponse.Status.ATTACHED) {
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
  private Common.Device findPreferredDevice() {
    if (myDevice != null && myDevice.getState().equals(Common.Device.State.ONLINE) && deviceHasAliveProcesses(myDevice)) {
      // Current selected device is online and has alive processes that can be profiled. We don't need to change it.
      return myDevice;
    }

    Set<Common.Device> devices = myProcesses.keySet();
    Set<Common.Device> onlineDevices =
      devices.stream().filter(device -> device.getState().equals(Common.Device.State.ONLINE)).collect(Collectors.toSet());
    if (!onlineDevices.isEmpty()) {
      // There are online devices. First try to find a device with alive processes that can be profiled.
      // If cant't find one, return any online device.
      Common.Device anyOnlineDevice = onlineDevices.iterator().next();
      return onlineDevices.stream().filter(this::deviceHasAliveProcesses).findAny().orElse(anyOnlineDevice);
    }

    // In case the currently selected device state has changed, it will be represented as a new Common.Device object.
    // Therefore, try to find a device with same serial as the selected one. If can't find it, return any device.
    Common.Device anyDevice = devices.isEmpty() ? null : devices.iterator().next();
    return myDevice == null ? anyDevice :
           devices.stream().filter(device -> device.getSerial().equals(myDevice.getSerial())).findAny().orElse(anyDevice);
  }

  private boolean deviceHasAliveProcesses(@NotNull Common.Device device) {
    List<Common.Process> deviceProcesses = myProcesses.get(device);
    if (deviceProcesses == null) {
      return false;
    }
    for (Common.Process process : deviceProcesses) {
      if (process.getState().equals(Common.Process.State.ALIVE)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Chooses the given device. If the device is not known or null, the first available one will be chosen instead.
   */
  public void setDevice(Common.Device device) {
    if (!Objects.equals(device, myDevice)) {
      // The device has changed and we need to reset the process.
      // First, end the current session on the previous device.
      mySessionsManager.endCurrentSession();
      myDevice = device;
      changed(ProfilerAspect.DEVICES);

      // Then set a new process.
      setProcess(null);
    }
  }

  public void setMonitoringStage() {
    setStage(new StudioMonitorStage(this));
  }

  /**
   * Chooses a process, and starts profiling it if not already (and stops profiling the previous one).
   *
   * @param process the process that will be selected. If it is null, a process will be determined
   *                automatically by heuristics.
   */
  public void setProcess(@Nullable Common.Process process) {
    List<Common.Process> processes = myProcesses.get(myDevice);
    if (process == null || processes == null || !processes.contains(process)) {
      process = getPreferredProcess(processes);
    }
    else {
      // The user wants to select a different process explicitly.
      // If the user intentionally selects something else, the profiler should not switch
      // back to the preferred process in any cases.
      myPreferredProcessName = null;
    }

    if (!Objects.equals(process, myProcess)) {
      // First make sure to end the previous session.
      mySessionsManager.endCurrentSession();

      myProcess = process;
      changed(ProfilerAspect.PROCESSES);
      mySessionsManager.beginSession(myDevice, myProcess);
    }
  }

  private void sessionsChanged() {
    Common.Session newSession = mySessionsManager.getSession();
    if (newSession.equals(mySession)) {
      return;
    }

    // The current selected session has not changed but it has gone from live to finished, stop all profilers.
    if (mySession.getSessionId() == newSession.getSessionId() &&
        isSessionAlive(mySession) && !isSessionAlive(newSession)) {
      mySession = newSession;
      myProfilers.forEach(profiler -> profiler.stopProfiling(mySession));
      myTimeline.setIsPaused(true);
      return;
    }

    mySession = newSession;
    myAgentStatus = getAgentStatus();

    // The new session is just starting, start the profilers.
    if (isSessionAlive(mySession)) {
      myProfilers.forEach(profiler -> profiler.startProfiling(mySession));
      myTimeline.reset(mySession.getStartTimestamp());

      // Navigate back to the monitor stage whenever we are starting a new session.
      setStage(new StudioMonitorStage(this));
      myIdeServices.getFeatureTracker().trackProfilingStarted();
      if (myAgentStatus == AgentStatusResponse.Status.ATTACHED) {
        getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
      }
    }
    else if (Common.Session.getDefaultInstance().equals(newSession)) {
      setStage(new NullMonitorStage(this));
    }

    // Profilers can query data depending on whether the agent is set. Even though we set the status above, delay until after the session
    // is properly assigned before firing this aspect change.
    changed(ProfilerAspect.AGENT);
  }

  @NotNull
  public String getSessionDisplayName() {
    if (Common.Session.getDefaultInstance().equals(mySession)) {
      return "";
    }

    GetSessionMetaDataResponse response = myClient.getProfilerClient()
      .getSessionMetaData(GetSessionMetaDataRequest.newBuilder().setSessionId(mySession.getSessionId()).build());
    return response.getData().getSessionName();
  }

  /**
   * Chooses a process among all potential candidates starting from the project's app process,
   * and then the one previously used. If no candidate is available, return the first available
   * process.
   */
  @Nullable
  private Common.Process getPreferredProcess(List<Common.Process> processes) {
    if (processes == null || processes.isEmpty()) {
      return null;
    }
    // Prefer the project's app if available.
    if (myPreferredProcessName != null) {
      for (Common.Process process : processes) {
        if (process.getName().equals(myPreferredProcessName) && process.getState() == Common.Process.State.ALIVE) {
          return process;
        }
      }
    }
    // Next, prefer the one previously used, either selected by user or automatically (even if the process has switched states)
    if (myProcess != null) {
      for (Common.Process process : processes) {
        if (isSameProcess(myProcess, process)) {
          return process;
        }
      }
    }
    // No preferred candidate. Choose a new process.
    return processes.get(0);
  }

  @NotNull
  private AgentStatusResponse.Status getAgentStatus() {
    if (Common.Session.getDefaultInstance().equals(mySession)) {
      return AgentStatusResponse.getDefaultInstance().getStatus();
    }

    AgentStatusRequest statusRequest =
      AgentStatusRequest.newBuilder().setPid(mySession.getPid()).setDeviceId(mySession.getDeviceId()).build();
    return myClient.getProfilerClient().getAgentStatus(statusRequest).getStatus();
  }

  /**
   * @return true if the processes are the same, false otherwise. The reason Objects.equals is not used here is because the states could
   * have changed between process1 and process2, but they should be considered the same as long as we have matching pids and names, so we
   * don't reset the stage.
   */
  private static boolean isSameProcess(@Nullable Common.Process process1, @Nullable Common.Process process2) {
    return process1 != null &&
           process2 != null &&
           process1.getPid() == process2.getPid() && process1.getName().equals(process2.getName());
  }

  public List<Common.Process> getProcesses() {
    List<Common.Process> processes = myProcesses.get(myDevice);
    return processes == null ? ImmutableList.of() : processes;
  }

  @NotNull
  public Stage getStage() {
    return myStage;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  @NotNull
  public SessionsManager getSessionsManager() {
    return mySessionsManager;
  }

  /**
   * @return the active session, otherwise {@link Common.Session#getDefaultInstance()} if no session is currently being profiled.
   */
  @NotNull
  public Common.Session getSession() {
    return mySession;
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

  @Nullable
  public Common.Device getDevice() {
    return myDevice;
  }

  @Nullable
  public Common.Process getProcess() {
    return myProcess;
  }

  private boolean isSessionAlive(@NotNull Common.Session session) {
    return session.getEndTimestamp() == Long.MAX_VALUE;
  }

  public boolean isLiveAllocationEnabled() {
    return getIdeServices().getFeatureConfig().isLiveAllocationsEnabled() && getDevice() != null &&
           getDevice().getFeatureLevel() >= AndroidVersion.VersionCodes.O && isAgentAttached();
  }

  public boolean isAgentAttached() {
    return myAgentStatus == AgentStatusResponse.Status.ATTACHED;
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

  /**
   * Return the list of stages that target a specific profiler, which a user might want to jump
   * between. This should exclude things like the top-level profiler stage, null stage, etc.
   */
  public List<Class<? extends Stage>> getDirectStages() {
    ImmutableList.Builder<Class<? extends Stage>> listBuilder = ImmutableList.builder();
    listBuilder.add(CpuProfilerStage.class);
    listBuilder.add(MemoryProfilerStage.class);
    listBuilder.add(NetworkProfilerStage.class);
    if (getIdeServices().getFeatureConfig().isEnergyProfilerEnabled()) {
      listBuilder.add(EnergyProfilerStage.class);
    }
    return listBuilder.build();
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
