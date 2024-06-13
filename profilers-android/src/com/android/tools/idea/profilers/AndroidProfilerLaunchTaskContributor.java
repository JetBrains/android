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

import static com.android.tools.profilers.StudioProfilers.DAEMON_DEVICE_DIR_PATH;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.android.tools.idea.profilers.analytics.StudioFeatureTracker;
import com.android.tools.idea.profilers.profilingconfig.CpuProfilerConfigConverter;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.run.AndroidLaunchTaskContributor;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.transport.TransportFileManager;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.idea.util.StudioPathManager;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.AdditionalOptions;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.perfetto.config.PerfettoTraceConfigBuilders;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  @Override
  public @NotNull String getAmStartOptions(@NotNull String applicationId,
                                           @NotNull AndroidRunConfigurationBase configuration,
                                           @NotNull IDevice device,
                                           @NotNull Executor executor) {
    return AndroidProfilerLaunchTaskContributor.getAmStartOptions(configuration.getProject(), applicationId,
                                                                  configuration.getProfilerState(), device, executor);
  }

  // Used only for Bazel. We need to write better mechanism of reusing AndroidLaunchTaskContributor for Blaze.
  public static String getAmStartOptions(@NotNull Project project, @NotNull String applicationId, @Nullable ProfilerState profilerState,
                                         @NotNull IDevice device, @NotNull Executor executor) {
    if (!isProfilerLaunch(executor)) {
      // Not a profile action
      return "";
    }

    if (profilerState == null) {
      // Profiler settings not present
      return "";
    }

    TransportService transportService = TransportService.getInstance();
    ProfilerClient client = new ProfilerClient(TransportService.getChannelName());
    Common.Device profilerDevice;
    try {
      profilerDevice = waitForDaemon(device, client);
    }
    catch (InterruptedException | TimeoutException e) {
      client.shutdownChannel();
      getLogger().debug(e);
      // Don't attach JVMTI agent for now, there is a chance that it will be attached during runtime.
      return "";
    }

    TransportFileManager fileManager = new TransportFileManager(device, transportService.getMessageBus());
    pushStartupAgentConfig(fileManager, project);
    String agentArgs = fileManager.configureStartupAgent(applicationId, STARTUP_AGENT_CONFIG_NAME, executor.getId());
    String startupProfilingResult = startStartupProfiling(profilerState, applicationId, project, client, device, profilerDevice);

    // In the Task-Based UX, a startup configuration should only be used once after explicitly starting a task via the profiler UI. Thus,
    // after consumption of the startup configuration, the config is reset to avoid re-use. If not reset and the user sets a startup
    // configuration by starting a startup task then invokes one of the Profile with low overhead/complete data actions from the main
    // toolbar, a startup task will begin again.
    if (StudioFlags.PROFILER_TASK_BASED_UX.get()) {
      profilerState.disableStartupProfiling();
    }

    client.shutdownChannel();
    return String.format("%s %s", agentArgs, startupProfilingResult);
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

    Trace.TraceConfiguration configuration = Trace.TraceConfiguration.newBuilder()
      .setAppName(appPackageName)
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_STARTUP)
      .setAbiCpuArch(abi)
      .setPerfettoOptions(
        PerfettoTraceConfigBuilders.INSTANCE.getMemoryTraceConfig(appPackageName, profilerState.NATIVE_MEMORY_SAMPLE_RATE_BYTES))
      .setTempPath(traceFilePath)
      .build();

    Commands.Command sampleCommand = Commands.Command.newBuilder()
      .setStreamId(profilerDevice.getDeviceId())
      .setType(Commands.Command.CommandType.START_TRACE)
      .setStartTrace(Trace.StartTrace.newBuilder()
                       .setProfilerType(Trace.ProfilerType.MEMORY)
                       .setConfiguration(configuration))
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

    ProfilingConfiguration profilingConfiguration =
      CpuProfilerConfigConverter.toProfilingConfiguration(startupConfig, device.getVersion().getFeatureLevel());

    Trace.TraceConfiguration.Builder configurationBuilder = Trace.TraceConfiguration.newBuilder()
      .setAppName(appPackageName)
      .setInitiationType(Trace.TraceInitiationType.INITIATED_BY_STARTUP)
      .setAbiCpuArch(cpuAbi)
      .setTempPath(traceFilePath);

    // Set the options field of the TraceConfiguration with the respective profiling configuration.
    profilingConfiguration.addOptions(configurationBuilder, Map.of(AdditionalOptions.APP_PKG_NAME, appPackageName));
    Trace.TraceConfiguration configuration = configurationBuilder.build();

    try {
      Commands.Command startCommand = Commands.Command.newBuilder()
        .setStreamId(profilerDevice.getDeviceId())
        .setType(Commands.Command.CommandType.START_TRACE)
        .setStartTrace(Trace.StartTrace.newBuilder()
                         .setProfilerType(Trace.ProfilerType.CPU)
                         .setConfiguration(configuration)
                         .build())
        .build();
      // TODO handle async error statuses.
      // TODO(b/150503095)
      Transport.ExecuteResponse response = client.getTransportClient().execute(Transport.ExecuteRequest.newBuilder()
                                                                                 .setCommand(startCommand)
                                                                                 .build());
    }
    catch (StatusRuntimeException exception) {
      getLogger().error(exception);
    }

    StudioFeatureTracker featureTracker = new StudioFeatureTracker(project);
    featureTracker.trackCpuStartupProfiling(profilerDevice, ProfilingConfiguration.fromProto(configuration, StudioFlags.PROFILER_TRACEBOX.get()));

    if (profilingConfiguration.getTraceType() != TraceType.ART) {
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
    List<Common.Device> devices = StudioProfilers.getUpToDateDevices(client, null, null);
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
                             "prebuilts/tools/common/" + dir,
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
    Path dir;
    if (StudioPathManager.isRunningFromSources()) {
      dir = StudioPathManager.resolvePathFromSourcesRoot(devDir);
    }
    else {
      dir = Paths.get(PathManager.getHomePath(), releaseDir);
    }
    for (String abi : device.getAbis()) {
      Path candidate = dir.resolve(abi).resolve(fileName);
      if (Files.exists(candidate)) {
        return Abi.getEnum(abi).getCpuArch();
      }
    }
    return "";
  }

  /**
   * @return true if the launch is initiated by the {@link ProfileRunExecutor}. False otherwise.
   */
  public static boolean isProfilerLaunch(@NotNull Executor executor) {
    return ProfileRunExecutor.EXECUTOR_ID.equals(executor.getId()) || // Legacy Profile executor
           AbstractProfilerExecutorGroup.Companion.getExecutorSetting(executor.getId()) != null; // Profileable Builds executor group
  }
}
