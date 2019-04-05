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

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilerConfigConverter;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.run.AndroidLaunchTaskContributor;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
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
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
    return new AndroidProfilerToolWindowLaunchTask(module, launchOptions);
  }

  @NotNull
  @Override
  public String getAmStartOptions(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                  @NotNull IDevice device) {
    if (!isProfilerLaunch(launchOptions)) {
      // Not a profile action
      return "";
    }

    Project project = module.getProject();
    TransportService transportService = TransportService.getInstance();
    if (transportService == null) {
      // Profiler cannot be run.
      return "";
    }

    ProfilerClient client = new ProfilerClient(transportService.getChannelName());
    long deviceId;
    try {
      deviceId = waitForPerfd(device, client);
    }
    catch (InterruptedException | TimeoutException e) {
      getLogger().debug(e);
      // Don't attach JVMTI agent for now, there is a chance that it will be attached during runtime.
      return "";
    }

    TransportFileManager fileManager = new TransportFileManager(device, transportService.getMessageBus());
    pushStartupAgentConfig(fileManager, project);
    String agentArgs = fileManager.configureStartupAgent(applicationId, STARTUP_AGENT_CONFIG_NAME);
    String startupProfilingResult = startStartupProfiling(applicationId, project, client, device, deviceId);
    return String.format("%s %s", agentArgs, startupProfilingResult);
  }

  private void pushStartupAgentConfig(@NotNull TransportFileManager fileManager, @NotNull Project project) {
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
  private static String startStartupProfiling(@NotNull String appPackageName,
                                              @NotNull Project project,
                                              @NotNull ProfilerClient client,
                                              @NotNull IDevice device,
                                              long deviceId) {
    if (!StudioFlags.PROFILER_STARTUP_CPU_PROFILING.get()) {
      return "";
    }

    AndroidRunConfigurationBase runConfig = getSelectedRunConfiguration(project);
    if (runConfig == null || !runConfig.getProfilerState().STARTUP_CPU_PROFILING_ENABLED) {
      return "";
    }

    String configName = runConfig.getProfilerState().STARTUP_CPU_PROFILING_CONFIGURATION_NAME;
    CpuProfilerConfig startupConfig = CpuProfilerConfigsState.getInstance(project).getConfigByName(configName);
    if (startupConfig == null) {
      return "";
    }

    if (!isAtLeastO(device)) {
      AndroidNotification.getInstance(project).showBalloon("Startup CPU Profiling",
                                                           "Starting a method trace recording on startup is only " +
                                                           "supported on devices with API levels 26 and higher.",
                                                           NotificationType.WARNING);
      return "";
    }

    CpuProfiler.StartupProfilingRequest.Builder requestBuilder = CpuProfiler.StartupProfilingRequest
      .newBuilder()
      .setAppPackage(appPackageName)
      .setDeviceId(deviceId)
      .setConfiguration(CpuProfilerConfigConverter.toProto(startupConfig));

    if (requestBuilder.getConfiguration().getProfilerType() == CpuProfiler.CpuProfilerType.SIMPLEPERF) {
      requestBuilder.setAbiCpuArch(getAbiDependentCommonLibName("simpleperf", "simpleperf", device));
    }
    else if (requestBuilder.getConfiguration().getProfilerType() == CpuProfiler.CpuProfilerType.ATRACE) {
      requestBuilder.setAbiCpuArch(getAbiDependentCommonLibName("perfetto", "perfetto", device));
    }

    CpuProfiler.StartupProfilingResponse response = client.getCpuClient().startStartupProfiling(requestBuilder.build());

    if (response.getFilePath().isEmpty() || requestBuilder.getConfiguration().getProfilerType() != CpuProfiler.CpuProfilerType.ART) {
      return "";
    }

    StringBuilder argsBuilder = new StringBuilder("--start-profiler ").append(response.getFilePath());
    if (startupConfig.getTechnology() == CpuProfilerConfig.Technology.SAMPLED_JAVA) {
      argsBuilder.append(" --sampling ").append(startupConfig.getSamplingIntervalUs());
    }

    argsBuilder.append(" --streaming");
    return argsBuilder.toString();
  }

  private static boolean isAtLeastO(@NotNull IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
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
   * Waits for perfd to come online for maximum 1 minute.
   *
   * @return ID of device, i.e {@link Common.Device#getDeviceId()}
   */
  private static long waitForPerfd(@NotNull IDevice device, @NotNull ProfilerClient client)
    throws InterruptedException, TimeoutException {
    // Wait for perfd to come online for 1 minute.
    for (int i = 0; i < 60; ++i) {
      Common.Device profilerDevice = getProfilerDevice(device, client);
      if (!Common.Device.getDefaultInstance().equals(profilerDevice)) {
        return profilerDevice.getDeviceId();
      }
      //noinspection BusyWait
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }
    throw new TimeoutException("Timeout waiting for perfd");
  }

  /**
   * @return the {@link Common.Device} representation of the input {@link IDevice} if one exists.
   * {@link Common.Device#getDefaultInstance()} otherwise.
   */
  @NotNull
  private static Common.Device getProfilerDevice(@NotNull IDevice device, @NotNull ProfilerClient client) {
    List<Common.Device> devices = StudioProfilers.getUpToDateDevices(StudioFlags.PROFILER_UNIFIED_PIPELINE.get(), client, null);
    for (Common.Device profilerDevice : devices) {
      if (profilerDevice.getSerial().equals(device.getSerialNumber()) && profilerDevice.getState() == Common.Device.State.ONLINE) {
        return profilerDevice;
      }
    }

    return Common.Device.getDefaultInstance();
  }

  @NotNull
  private static String getAbiDependentCommonLibName(String dir, String fileName, IDevice device) {
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
  private static boolean isProfilerLaunch(@NotNull LaunchOptions options) {
    Object launchValue = options.getExtraOption(ProfileRunExecutor.PROFILER_LAUNCH_OPTION_KEY);
    return launchValue instanceof Boolean && (Boolean)launchValue;
  }

  public static final class AndroidProfilerToolWindowLaunchTask implements LaunchTask {
    private static final String ID = "PROFILER_TOOLWINDOW";
    @NotNull private final Module myModule;
    @NotNull private final LaunchOptions myLaunchOptions;

    public AndroidProfilerToolWindowLaunchTask(@NotNull Module module, @NotNull LaunchOptions launchOptions) {
      myModule = module;
      myLaunchOptions = launchOptions;
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
    public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      // There are two scenarios here:
      // 1. If the profiler window is opened, we only profile the process that is launched and detected by the profilers after the current
      // device time. This is to avoid profiling the previous application instance in case it is still running.
      // 2. If the profiler window is closed, we cache the device+module info so the profilers can auto-start if the user opens the window
      // manually at a later time.
      long currentDeviceTimeNs = getCurrentDeviceTime(device);
      ApplicationManager.getApplication().invokeLater(
        () -> {
          Project project = myModule.getProject();
          ToolWindow window = ToolWindowManagerEx.getInstanceEx(project).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          if (window != null) {
            window.setShowStripeButton(true);

            String deviceName = AndroidProfilerToolWindow.getDeviceDisplayName(device);
            String processName = AndroidProfilerToolWindow.getModuleName(myModule);
            AndroidProfilerToolWindow.PreferredProcessInfo preferredProcessInfo =
              new AndroidProfilerToolWindow.PreferredProcessInfo(deviceName, processName,
                                                                 p -> p.getStartTimestampNs() >= currentDeviceTimeNs);
            // If the window is currently not shown, either if the users click on Run/Debug or if they manually collapse/hide the window,
            // then we shouldn't start profiling the launched app.
            boolean profileStarted = false;
            if (window.isVisible()) {
              AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerToolWindow(project);
              if (profilerToolWindow != null) {
                profilerToolWindow.profile(preferredProcessInfo);
                profileStarted = true;
              }
            }
            // Caching the device+process info in case auto-profiling should kick in at a later time.
            if (!profileStarted) {
              project.putUserData(LAST_RUN_APP_INFO, preferredProcessInfo);
            }
          }
        });

      // When Studio detects that the process is terminated, remove the LAST_RUN_APP_INFO cache to prevent the profilers from waiting
      // to auto-profiling a process that has already been killed.
      if (launchStatus instanceof ProcessHandlerLaunchStatus) {
        ProcessHandler processHandler = ((ProcessHandlerLaunchStatus)launchStatus).getProcessHandler();
        ProcessAdapter adapter = new ProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            myModule.getProject().putUserData(LAST_RUN_APP_INFO, null);
            // Removes the listener as soon as we receive the event, to avoid the ProcessHandler holding to it any longer than needed.
            processHandler.removeProcessListener(this);
          }
        };
        processHandler.addProcessListener(adapter);
      }

      return true;
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
      long startTimeNs = Long.MIN_VALUE;

      // If it's not a profile launch avoid initializing the service as it is an expensive call.
      if (!isProfilerLaunch(myLaunchOptions) && !TransportService.isServiceInitialized()) {
        return startTimeNs;
      }

      TransportService transportService = TransportService.getInstance();
      if (transportService == null) {
        return startTimeNs;
      }
      ProfilerClient client = new ProfilerClient(transportService.getChannelName());

      // If we are launching from the "Profile" action, wait for perfd to start properly to get the time.
      // Note: perfd should have started already from AndroidProfilerLaunchTaskContributor#getAmStartOptions already. This wait might be
      // redundant but harmless.
      long deviceId = -1;
      if (isProfilerLaunch(myLaunchOptions)) {
        try {
          deviceId = waitForPerfd(device, client);
        }
        catch (InterruptedException | TimeoutException e) {
          getLogger().debug(e);
        }
      }
      else {
        // If we are launching from Run/Debug, do not bother waiting for perfd start, but try to get the time anyway in case the profiler
        // is already running.
        deviceId = getProfilerDevice(device, client).getDeviceId();
      }

      TimeResponse timeResponse = client.getTransportClient().getCurrentTime(TimeRequest.newBuilder().setStreamId(deviceId).build());
      if (!TimeResponse.getDefaultInstance().equals(timeResponse)) {
        // Found a valid time response, sets that as the time for detecting when the process is next launched.
        startTimeNs = timeResponse.getTimestampNs();
      }

      return startTimeNs;
    }
  }
}
