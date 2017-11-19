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

  /**
   * How much of the timeline is hidden from the main data range.
   * A value of zero indicates that we synchronize the data max value with the current time on device.
   */
  public static final int TIMELINE_BUFFER = 0;

  private final ProfilerClient myClient;

  private final ProfilerTimeline myTimeline;

  private final List<StudioProfiler> myProfilers;

  @NotNull
  private final IdeProfilerServices myIdeServices;

  /**
   * Processes from devices come from the latest update, and are filtered to include only ALIVE ones and {@code myProcess}.
   */
  private Map<Common.Device, List<Common.Process>> myProcesses;

  @Nullable
  private Common.Process myProcess;

  private AgentStatusResponse.Status myAgentStatus;

  @Nullable
  private String myPreferredProcessName;

  private Common.Device myDevice;

  @Nullable
  private Common.Session mySessionData;

  @NotNull
  private Stage myStage;

  private Updater myUpdater;

  @NotNull
  private RelativeTimeConverter myRelativeTimeConverter;

  private AxisComponentModel myViewAxis;

  private long myRefreshDevices;

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
    myTimeline.getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      if (!myTimeline.getSelectionRange().isEmpty()) {
        myTimeline.setStreaming(false);
      }
    });

    myProcesses = Maps.newHashMap();
    myConnected = false;
    myDevice = null;
    myProcess = null;
    // TODO: StudioProfilers initalizes with a default session, which a lot of tests now relies on to avoid a NPE.
    // We should clean all the tests up to either have StudioProfilers create a proper session first or handle the null cases better.
    mySessionData = Common.Session.getDefaultInstance();

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
      if (!myConnected) {
        this.changed(ProfilerAspect.CONNECTION);
      }

      myConnected = true;
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

      // A heartbeat event may not have been sent by perfa when we first profile an app, here we keep pinging the status and
      // fire the corresponding change and tracking events.
      if (myProcess != null) {
        AgentStatusResponse.Status agentStatus = getAgentStatus();
        if (myAgentStatus != agentStatus) {
          myAgentStatus = agentStatus;
          changed(ProfilerAspect.AGENT);
          if (isProcessAlive() && myAgentStatus == AgentStatusResponse.Status.ATTACHED) {
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
      // First, stop profiling the current process on the previous device.
      if (myDevice != null && myProcess != null &&
          myDevice.getDeviceId() == myProcess.getDeviceId() &&
          myDevice.getState() == Common.Device.State.ONLINE &&
          myProcess.getState() == Common.Process.State.ALIVE) {
        endSession();
      }

      mySessionData = null;

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
   * Chooses a process, and starts profiling it if not already (and stops profiling the previous
   * one).
   *
   * @param process the process that will be selected. If it is null, a process will be determined
   *                automatically by heuristics.
   */
  public void setProcess(@Nullable Common.Process process) {
    List<Common.Process> processes = myProcesses.get(myDevice);
    if (process == null || processes == null || !processes.contains(process)) {
      process = getPreferredProcess(processes);
    }
    if (!Objects.equals(process, myProcess)) {
      if (myDevice != null && myProcess != null &&
          myDevice.getState() == Common.Device.State.ONLINE &&
          // Avoids calling endSession() on a previous process if the device has already changed.
          // In those cases, endSession() should have already been called during setDevice.
          myDevice.getDeviceId() == myProcess.getDeviceId() &&
          myProcess.getState() == Common.Process.State.ALIVE) {
        endSession();
      }
      boolean onlyStateChanged = isSameProcess(myProcess, process);
      myProcess = process;
      changed(ProfilerAspect.PROCESSES);
      myAgentStatus = getAgentStatus();
      if (myDevice != null && myProcess != null &&
          myDevice.getState() == Common.Device.State.ONLINE &&
          myProcess.getState() == Common.Process.State.ALIVE) {
        // Starts a new session.
        beginSession();

        TimeResponse response =
          myClient.getProfilerClient().getCurrentTime(TimeRequest.newBuilder().setDeviceId(myDevice.getDeviceId()).build());
        long currentDeviceTime = response.getTimestampNs();
        long runTime = currentDeviceTime - myProcess.getStartTimestampNs();
        myRelativeTimeConverter = new RelativeTimeConverter(myProcess.getStartTimestampNs() - TimeUnit.SECONDS.toNanos(TIMELINE_BUFFER));
        myTimeline.reset(myRelativeTimeConverter, runTime);

        // Attach agent for advanced profiling if JVMTI is enabled and not yet attached.
        if (myDevice.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
            myIdeServices.getFeatureConfig().isJvmtiAgentEnabled()) {
          // If an agent has been previously attached, Perfd will only re-notify the existing agent of the updated grpc target instead
          // of re-attaching an agent. See ProfilerService::AttachAgent on the Perfd side for more details.
          myClient.getProfilerClient()
            .attachAgent(AgentAttachRequest.newBuilder().setSession(getSession()).setProcessId(myProcess.getPid())
                           .setAgentLibFileName(String.format("libperfa_%s.so", myProcess.getAbiCpuArch())).build());
        }

        myIdeServices.getFeatureTracker().trackProfilingStarted();
        if (myAgentStatus == AgentStatusResponse.Status.ATTACHED) {
          getIdeServices().getFeatureTracker().trackAdvancedProfilingStarted();
        }
      }
      else {
        myTimeline.setIsPaused(true);

        if (myDevice != null && myProcess != null) {
          // The process is dead, find the previous session that was associated with the device and process.
          GetSessionsResponse sessionsResponse = myClient.getProfilerClient().getSessions(GetSessionsRequest.getDefaultInstance());
          // Sessions are sorted based on start time, reverse the traversal to find the most recent one.
          for (int i = sessionsResponse.getSessionsCount() - 1; i >= 0; i--) {
            Common.Session session = sessionsResponse.getSessions(i);
            if (session.getDeviceId() == myDevice.getDeviceId() && session.getPid() == myProcess.getPid()) {
              mySessionData = session;
              break;
            }
          }
        }
      }

      if (!onlyStateChanged) {
        if (myProcess == null) {
          setStage(new NullMonitorStage(this));
        }
        else {
          assert mySessionData != null;
          setStage(new StudioMonitorStage(this));
        }
      }

      // Profilers can query data depending on whether the agent is set. Even though we set the status above, delay until after the session
      // is properly assigned before firing this aspect change.
      changed(ProfilerAspect.AGENT);
    }
  }

  private void beginSession() {
    assert myDevice != null && myProcess != null;
    BeginSessionResponse response =
      myClient.getProfilerClient().beginSession(BeginSessionRequest.newBuilder()
                                                  .setDeviceId(myDevice.getDeviceId())
                                                  .setProcessId(myProcess.getPid())
                                                  .build());
    mySessionData = response.getSession();
    myProfilers.forEach(profiler -> profiler.startProfiling(mySessionData, myProcess));
  }

  private void endSession() {
    assert mySessionData != null;
    EndSessionResponse response = myClient.getProfilerClient().endSession(EndSessionRequest.newBuilder()
                                                                            .setDeviceId(myDevice.getDeviceId())
                                                                            .setSessionId(mySessionData.getSessionId())
                                                                            .build());
    myProfilers.forEach(profiler -> profiler.stopProfiling(response.getSession(), myProcess));
    mySessionData = null;
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
          // Only switch to the preferred process once. If the user intentionally selects something else, the profiler should not switch
          // back to the preferred process in any cases.
          myPreferredProcessName = null;
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
    if (myDevice == null || myProcess == null) {
      return AgentStatusResponse.getDefaultInstance().getStatus();
    }

    AgentStatusRequest statusRequest =
      AgentStatusRequest.newBuilder().setProcessId(myProcess.getPid()).setDeviceId(myDevice.getDeviceId()).build();
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

  public Common.Device getDevice() {
    return myDevice;
  }

  public Common.Process getProcess() {
    return myProcess;
  }

  public boolean isProcessAlive() {
    return myProcess != null && myProcess.getState() == Common.Process.State.ALIVE;
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
