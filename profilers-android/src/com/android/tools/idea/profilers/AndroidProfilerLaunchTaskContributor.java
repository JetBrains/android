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
package com.android.tools.idea.profilers;

import static com.android.tools.idea.profilers.AndroidProfilerToolWindow.LAST_RUN_APP_INFO;
import static com.android.tools.profilers.StudioProfilers.DAEMON_DEVICE_DIR_PATH;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilerConfigConverter;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.run.AndroidLaunchTaskContributor;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.run.tasks.LaunchContext;
import com.android.tools.idea.run.tasks.LaunchResult;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link AndroidLaunchTaskContributor} specific to profiler. For example, this contributor provides "--attach-agent $agentArgs"
 * extra option to "am start ..." command.
 */
public final class AndroidProfilerLaunchTaskContributor implements AndroidLaunchTaskContributor {
  private static final String STARTUP_AGENT_CONFIG_NAME = "startupagent.config";

  private static Logger getLogger() {
    return Logger.getInstance(AndroidProfilerLaunchTaskContributor.class);
  }

  @NotNull
  @Override
  public LaunchTask getTask(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions) {
    return new AndroidProfilerToolWindowLaunchTask(module.getProject(), launchOptions, AndroidProfilerToolWindow.getModuleName(module));
  }

  @NotNull
  public static String getAmStartOptions(@NotNull Project project, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                         @NotNull IDevice device) {
    if (!isProfilerLaunch(launchOptions)) {
      // Not a profile action
      return "";
    }

    ProfilerState profilerState = getProfilerStateFromCurrentRun(launchOptions, project);
    if (profilerState == null) {
      // Profiler settings not present
      return "";
    }

    TransportService transportService = TransportService.getInstance();
    if (transportService == null) {
      // Profiler cannot be run.
      return "";
    }

    ProfilerClient client = new ProfilerClient(TransportService.CHANNEL_NAME);
    Common.Device profilerDevice;
    try {
      profilerDevice = waitForDaemon(device, client);
    }
    catch (InterruptedException | TimeoutException e) {
      getLogger().debug(e);
      // Don't attach JVMTI agent for now, there is a chance that it will be attached during runtime.
      return "";
    }

    TransportFileManager fileManager = new TransportFileManager(device, transportService.getMessageBus());
    pushStartupAgentConfig(fileManager, project);
    String agentArgs = fileManager.configureStartupAgent(applicationId, STARTUP_AGENT_CONFIG_NAME);
    String startupProfilingResult = startStartupProfiling(profilerState, applicationId, project, client, device, profilerDevice);
    return String.format("%s %s", agentArgs, startupProfilingResult);
  }

  @NotNull
  @Override
  public String getAmStartOptions(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                  @NotNull IDevice device) {
    return getAmStartOptions(module.getProject(), applicationId, launchOptions, device);
  }

  private static void pushStartupAgentConfig(@NotNull TransportFileManager fileManager, @NotNull Project project) {
    // Memory live allocation setting may change in the run config so push a new one
    try {
      fileManager.pushAgentConfig(STARTUP_AGENT_CONFIG_NAME, getSelectedRunConfiguration(project));
    }
    catch (TimeoutException | ShellCommandUnresponsiveException | SyncException e) {
      throw new RuntimeException(e);
    }
    catch (AdbCommandRejectedException | IOException e) {
      // AdbCommandRejectedException and IOException happen when unplugging the device shortly after plugging it in.
      // We don't want to crash in this case.
      getLogger().warn("Error when trying to push AgentConfig:", e);
    }
  }

  /**
   * Starts startup profiling by RPC call to perfd.
   *
   * @return arguments used with --start-profiler flag, i.e "--start-profiler $filePath --sampling 100 --streaming",
   * the result is an empty string, when either startup CPU profiling is not enabled
   * or the selected CPU configuration is not an ART profiling.
   */
  @NotNull
  private static String startStartupProfiling(@NotNull ProfilerState profilerState,
                                              @NotNull String appPackageName,
                                              @NotNull Project project,
                                              @NotNull ProfilerClient client,
                                              @NotNull IDevice device,
                                              @NotNull Common.Device profilerDevice) {
    if (profilerState.isCpuStartupProfilingEnabled()) {
      return triggerCpuStartupProfilingAndReturnArtParams(profilerState, appPackageName, project, client, device, profilerDevice);
    }
    if (profilerState.isNativeMemoryStartupProfilingEnabled()) {
      triggerMemoryStartupProfiling(profilerState, appPackageName, project, client, device, profilerDevice);
    }
    return "";
  }

  private static void triggerMemoryStartupProfiling(@NotNull ProfilerState profilerState,
                                                    @NotNull String appPackageName,
                                                    @NotNull Project project,
                                                    @NotNull ProfilerClient client,
                                                    @NotNull IDevice device,
                                                    @NotNull Common.Device profilerDevice) {
    if (!isAtLeast(device, AndroidVersion.VersionCodes.Q)) {
      AndroidNotification.getInstance(project).showBalloon("Startup Native Memory Profiling",
                                                           "Starting a native memory sampling trace recording on startup is only " +
                                                           "supported on devices with API levels 28 and higher.",
                                                           NotificationType.WARNING);
      return;
    }
    if (device.getAbis().isEmpty()) {
      AndroidNotification.getInstance(project).showBalloon("Startup Native Memory Profiling",
                                                           "Unable to detect device abi for startup memory profiling.",
                                                           NotificationType.WARNING);
      return;
    }

    if (device.getAbis().contains("x86") || device.getAbis().contains("x86_64")) {
      AndroidNotification.getInstance(project).showBalloon("Startup Native Memory Profiling",
                                                           "Native memory profiling not supported on x86 and x86_64 devices.",
                                                           NotificationType.WARNING);
      return;
    }
    String abi = device.getAbis().get(0);
    StudioFeatureTracker featureTracker = new StudioFeatureTracker(project);
    featureTracker.trackRecordAllocations();
    String traceFilePath = String.format(Locale.US, "%s/%s.trace", DAEMON_DEVICE_DIR_PATH, appPackageName);
    Commands.Command sampleCommand = Commands.Command.newBuilder()
      .setStreamId(profilerDevice.getDeviceId())
      .setType(Commands.Command.CommandType.START_NATIVE_HEAP_SAMPLE)
      .setStartNativeSample(Memory.StartNativeSample.newBuilder()
                              .setSamplingIntervalBytes(profilerState.NATIVE_MEMORY_SAMPLE_RATE_BYTES)
                              .setSharedMemoryBufferBytes(64 * 1024 * 1024)
                              .setAbiCpuArch(abi)
                              .setTempPath(traceFilePath)
                              .setAppName(appPackageName))
      .build();
    Transport.ExecuteResponse response =
      client.getTransportClient().execute(Transport.ExecuteRequest.newBuilder().setCommand(sampleCommand).build());
  }

  private static String triggerCpuStartupProfilingAndReturnArtParams(@NotNull ProfilerState profilerState,
                                                                     @NotNull String appPackageName,
                                                                     @NotNull Project project,
                                                                     @NotNull ProfilerClient client,
                                                                     @NotNull IDevice device,
                                                                     @NotNull Common.Device profilerDevice) {
    String configName = profilerState.STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
    if (configName == null) {
      return "";
    }

    CpuProfilerConfig startupConfig = CpuProfilerConfigsState.getInstance(project).getConfigByName(configName);
    if (startupConfig == null) {
      return "";
    }
    if (!isAtLeast(device, AndroidVersion.VersionCodes.O)) {
      AndroidNotification.getInstance(project).showBalloon("Startup CPU Profiling",
                                                           "Starting a method trace recording on startup is only " +
                                                           "supported on devices with API levels 26 and higher.",
                                                           NotificationType.WARNING);
      return "";
    }
    String cpuAbi = "";
    switch (startupConfig.getTechnology()) {
      case SAMPLED_NATIVE:
        cpuAbi = getAbiDependentLibraryName("simpleperf", "simpleperf", device);
        break;
      case SYSTEM_TRACE:
        cpuAbi = getAbiDependentLibraryName("perfetto", "perfetto", device);
        break;
      default:
        break;
    }

    // TODO b/133321803 switch back to having daemon generates and provides the path.
    String traceFilePath = String.format(Locale.US, "%s/%s-%d.trace", DAEMON_DEVICE_DIR_PATH, appPackageName, System.nanoTime());
    Cpu.CpuTraceConfiguration.UserOptions traceOptions =
      CpuProfilerConfigConverter.toProto(startupConfig, device.getVersion().getFeatureLevel());
    Cpu.CpuTraceConfiguration configuration = Cpu.CpuTraceConfiguration.newBuilder()
      .setAppName(appPackageName)
      .setInitiationType(Cpu.TraceInitiationType.INITIATED_BY_STARTUP)
      .setAbiCpuArch(cpuAbi)
      .setTempPath(traceFilePath)
      .setUserOptions(traceOptions)
      .build();
    try {
      if (StudioFlags.PROFILER_UNIFIED_PIPELINE.get()) {
        Commands.Command startCommand = Commands.Command.newBuilder()
          .setStreamId(profilerDevice.getDeviceId())
          .setType(Commands.Command.CommandType.START_CPU_TRACE)
          .setStartCpuTrace(Cpu.StartCpuTrace.newBuilder().setConfiguration(configuration).build())
          .build();
        // TODO handle async error statuses.
        // TODO(b/150503095)
        Transport.ExecuteResponse response = client.getTransportClient().execute(Transport.ExecuteRequest.newBuilder()
                                                                                   .setCommand(startCommand)
                                                                                   .build());
      }
      else {
        CpuProfiler.StartupProfilingRequest.Builder requestBuilder = CpuProfiler.StartupProfilingRequest.newBuilder()
          .setDeviceId(profilerDevice.getDeviceId())
          .setConfiguration(configuration);
        // TODO(b/150503095)
        CpuProfiler.StartupProfilingResponse response = client.getCpuClient().startStartupProfiling(requestBuilder.build());
      }
    }
    catch (StatusRuntimeException exception) {
      getLogger().error(exception);
    }

    StudioFeatureTracker featureTracker = new StudioFeatureTracker(project);
    featureTracker.trackCpuStartupProfiling(profilerDevice, ProfilingConfiguration.fromProto(traceOptions));

    if (traceOptions.getTraceType() != Cpu.CpuTraceType.ART) {
      return "";
    }

    StringBuilder argsBuilder = new StringBuilder("--start-profiler ").append(traceFilePath);
    if (startupConfig.getTechnology() == CpuProfilerConfig.Technology.SAMPLED_JAVA) {
      argsBuilder.append(" --sampling ").append(startupConfig.getSamplingIntervalUs());
    }

    argsBuilder.append(" --streaming");
    return argsBuilder.toString();
  }

  private static boolean isAtLeast(@NotNull IDevice device, int version) {
    return device.getVersion().getFeatureLevel() >= version;
  }

  /**
   * Returns {@link ProfilerState} of the current run session.
   * <p>
   * Profiler state can come from two sources. For android gradle run configurations, {@link ProfilerState} is attached to the selected
   * run configuration.  For non-gradle projects, {@link ProfilerState} can be passed using {@link LaunchOptions#getExtraOption(String)}.
   * This method first checks if the configuration is an android gradle run configuration and falls back to extra launch options.
   */
  @Nullable
  private static ProfilerState getProfilerStateFromCurrentRun(@NotNull LaunchOptions launchOptions, Project project) {
    AndroidRunConfigurationBase runConfig = getSelectedRunConfiguration(project);
    if (runConfig != null) {
      return runConfig.getProfilerState();
    }

    Object stateFromExtraOptions = launchOptions.getExtraOption(ProfilerState.ANDROID_PROFILER_STATE_ID);
    if (stateFromExtraOptions instanceof ProfilerState) {
      return (ProfilerState)stateFromExtraOptions;
    }
    return null;
  }

  @Nullable
  private static AndroidRunConfigurationBase getSelectedRunConfiguration(@NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null && settings.getConfiguration() instanceof AndroidRunConfigurationBase) {
      return (AndroidRunConfigurationBase)settings.getConfiguration();
    }
    return null;
  }

  /**
   * Waits for daemon to come online for maximum 1 minute.
   *
   * @return the connected {@link Common.Device}
   */
  @NotNull
  private static Common.Device waitForDaemon(@NotNull IDevice device, @NotNull ProfilerClient client)
    throws InterruptedException, TimeoutException {
    for (int i = 0; i < 60; ++i) {
      Common.Device profilerDevice = getProfilerDevice(device, client);
      if (!Common.Device.getDefaultInstance().equals(profilerDevice)) {
        return profilerDevice;
      }
      //noinspection BusyWait
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }
    throw new TimeoutException("Timeout waiting for daemon");
  }

  /**
   * @return the {@link Common.Device} representation of the input {@link IDevice} if one exists.
   * {@link Common.Device#getDefaultInstance()} otherwise.
   */
  @NotNull
  private static Common.Device getProfilerDevice(@NotNull IDevice device, @NotNull ProfilerClient client) {
    List<Common.Device> devices = StudioProfilers.getUpToDateDevices(
      StudioFlags.PROFILER_UNIFIED_PIPELINE.get(), client, null, null);
    for (Common.Device profilerDevice : devices) {
      if (profilerDevice.getSerial().equals(device.getSerialNumber()) && profilerDevice.getState() == Common.Device.State.ONLINE) {
        return profilerDevice;
      }
    }

    return Common.Device.getDefaultInstance();
  }

  @NotNull
  private static String getAbiDependentLibraryName(String dir, String fileName, IDevice device) {
    return getBestAbiCpuArch(device,
                             "plugins/android/resources/" + dir,
                             "../../prebuilts/tools/common/" + dir,
                             fileName);
  }

  /**
   * @return the most preferred CPU arch according to {@link IDevice#getAbis()} for which
   * {@param fileName} exists in {@param releaseDir} or {@param devDir}.
   * For example, if the preferred Abi according to {@link IDevice#getAbis()} is {@link Abi#ARMEABI} or {@link Abi#ARMEABI_V7A} and
   * the {@param fileName} exists under it then it returns "arm".
   */
  @NotNull
  private static String getBestAbiCpuArch(@NotNull IDevice device,
                                          @NotNull String releaseDir,
                                          @NotNull String devDir,
                                          @NotNull String fileName) {
    File dir = new File(PathManager.getHomePath(), releaseDir);
    if (!dir.exists()) {
      dir = new File(PathManager.getHomePath(), devDir);
    }
    for (String abi : device.getAbis()) {
      File candidate = new File(dir, abi + "/" + fileName);
      if (candidate.exists()) {
        return Abi.getEnum(abi).getCpuArch();
      }
    }
    return "";
  }

  /**
   * @return true if the launch is initiated by the {@link ProfileRunExecutor}. False otherwise.
   */
  public static boolean isProfilerLaunch(@NotNull LaunchOptions options) {
    Object launchValue = options.getExtraOption(ProfileRunExecutor.PROFILER_LAUNCH_OPTION_KEY);
    return launchValue instanceof Boolean && (Boolean)launchValue;
  }

  public static final class AndroidProfilerToolWindowLaunchTask implements LaunchTask {
    private static final String ID = "PROFILER_TOOLWINDOW";
    @NotNull private final Project myProject;
    @NotNull private final LaunchOptions myLaunchOptions;
    @Nullable private final String myTargetProcessName;

    public AndroidProfilerToolWindowLaunchTask(@NotNull Project project,
                                               @NotNull LaunchOptions launchOptions,
                                               @Nullable String targetProcessName) {
      myProject = project;
      myLaunchOptions = launchOptions;
      myTargetProcessName = targetProcessName;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Launching the Profiler Tool Window";
    }

    @Override
    public int getDuration() {
      return LaunchTaskDurations.LAUNCH_ACTIVITY;
    }

    @Override
    public LaunchResult run(@NotNull LaunchContext launchContext) {
      IDevice device = launchContext.getDevice();

      // Get the current device time so that the profiler knows to not profile existing processes that were spawned before that time.
      // Otherwise, the profiler can start profiling for a brief moment, then the new process launches and the profiler switches
      // immediately to the new process, leaving a short-lived session behind. We do this only if the user launches explicit with the
      // profile option, as we need to not impact the run/debug workflow.
      long currentDeviceTimeNs = isProfilerLaunch(myLaunchOptions) ? getCurrentDeviceTime(device) : Long.MIN_VALUE;

      // There are two scenarios here:
      // 1. If the profiler window is opened, we only profile the process that is launched and detected by the profilers after the current
      // device time. This is to avoid profiling the previous application instance in case it is still running.
      // 2. If the profiler window is closed, we cache the device+module info so the profilers can auto-start if the user opens the window
      // manually at a later time.
      ApplicationManager.getApplication().invokeLater(
        () -> {
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          if (window != null) {
            window.setShowStripeButton(true);

            String deviceName = AndroidProfilerToolWindow.getDeviceDisplayName(device);
            PreferredProcessInfo preferredProcessInfo =
              new PreferredProcessInfo(deviceName, myTargetProcessName, p -> p.getStartTimestampNs() >= currentDeviceTimeNs);
            // If the window is currently not shown, either if the users click on Run/Debug or if they manually collapse/hide the window,
            // then we shouldn't start profiling the launched app.
            boolean profileStarted = false;
            if (window.isVisible()) {
              AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(myProject);
              if (profilerToolWindow != null) {
                profilerToolWindow.profile(preferredProcessInfo);
                profileStarted = true;
              }
            }
            // Caching the device+process info in case auto-profiling should kick in at a later time.
            if (!profileStarted) {
              myProject.putUserData(LAST_RUN_APP_INFO, preferredProcessInfo);
            }
          }
        });

      // When Studio detects that the process is terminated, remove the LAST_RUN_APP_INFO cache to prevent the profilers from waiting
      // to auto-profiling a process that has already been killed.
      LaunchStatus launchStatus = launchContext.getLaunchStatus();
      if (launchStatus instanceof ProcessHandlerLaunchStatus) {
        ProcessHandler processHandler = ((ProcessHandlerLaunchStatus)launchStatus).getProcessHandler();
        ProcessAdapter adapter = new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            myProject.putUserData(LAST_RUN_APP_INFO, null);
            // Removes the listener as soon as we receive the event, to avoid the ProcessHandler holding to it any longer than needed.
            processHandler.removeProcessListener(this);
          }
        };
        processHandler.addProcessListener(adapter);
      }

      return LaunchResult.success();
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }

    /**
     * Attempt to get the current time of the device.
     */
    private long getCurrentDeviceTime(@NotNull IDevice device) {
      assert isProfilerLaunch(myLaunchOptions);

      long startTimeNs = Long.MIN_VALUE;
      TransportService transportService = TransportService.getInstance();
      if (transportService == null) {
        return startTimeNs;
      }
      ProfilerClient client = new ProfilerClient(TransportService.CHANNEL_NAME);

      // If we are launching from the "Profile" action, wait for daemon to start properly to get the time.
      // Note: daemon should have started already from AndroidProfilerLaunchTaskContributor#getAmStartOptions already. This wait might be
      // redundant but harmless.
      long deviceId = -1;
      try {
        deviceId = waitForDaemon(device, client).getDeviceId();
      }
      catch (InterruptedException | TimeoutException e) {
        getLogger().debug(e);
      }

      try {
        TimeResponse timeResponse = client.getTransportClient().getCurrentTime(TimeRequest.newBuilder().setStreamId(deviceId).build());
        if (!TimeResponse.getDefaultInstance().equals(timeResponse)) {
          // Found a valid time response, sets that as the time for detecting when the process is next launched.
          startTimeNs = timeResponse.getTimestampNs();
        }
      }
      catch (StatusRuntimeException exception) {
        getLogger().error(exception);
      }

      return startTimeNs;
    }
  }
}
