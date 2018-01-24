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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.*;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.Profiler;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * A {@link AndroidLaunchTaskContributor} specific to profiler. For example, this contributor provides "--attach-agent $agentArgs"
 * extra option to "am start ..." command.
 */
public final class AndroidProfilerLaunchTaskContributor implements AndroidLaunchTaskContributor {
  private static Logger getLogger() {
    return Logger.getInstance(AndroidProfilerLaunchTaskContributor.class);
  }

  @NotNull
  @Override
  public LaunchTask getTask(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions) {
    return new AndroidProfilerToolWindowLaunchTask(module.getProject());
  }

  @NotNull
  @Override
  public String getAmStartOptions(@NotNull Module module, @NotNull String applicationId, @NotNull LaunchOptions launchOptions,
                                  @NotNull IDevice device) {
    Object launchValue = launchOptions.getExtraOption(ProfileRunExecutor.PROFILER_LAUNCH_OPTION_KEY);
    if (!(launchValue instanceof Boolean && (Boolean)launchValue)) {
      // Not a profile action
      return "";
    }

    ProfilerService profilerService = ProfilerService.getInstance(module.getProject());
    long deviceId;
    try {
      deviceId = waitForPerfd(device, profilerService);
    }
    catch (InterruptedException|TimeoutException e) {
      getLogger().debug(e);
      // Don't attach JVMTI agent for now, there is a chance that it will be attached during runtime.
      return "";
    }

    StringBuilder args = new StringBuilder(getAttachAgentArgs(applicationId, profilerService, device, deviceId));
    args.append(" ").append(startStartupProfiling(applicationId, module, profilerService, device, deviceId));
    return args.toString();
  }

  @NotNull
  private static String getAttachAgentArgs(@NotNull String appPackageName,
                                           @NotNull ProfilerService profilerService,
                                           @NotNull IDevice device,
                                           long deviceId) {
    Profiler.ConfigureStartupAgentResponse response = profilerService.getProfilerClient().getProfilerClient()
      .configureStartupAgent(Profiler.ConfigureStartupAgentRequest.newBuilder().setDeviceId(deviceId)
                            // TODO: Find a way of finding the correct ABI
                            .setAgentLibFileName(getAbiDependentLibPerfaName(device))
                            .setAppPackageName(appPackageName).build());
    return response.getAgentArgs().isEmpty() ? "" : "--attach-agent " + response.getAgentArgs();
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
                                              @NotNull Module module,
                                              @NotNull ProfilerService profilerService,
                                              @NotNull IDevice device,
                                              long deviceId) {
    if (!StudioFlags.PROFILER_STARTUP_CPU_PROFILING.get()) {
      return "";
    }

    AndroidRunConfigurationBase runConfig = getSelectedRunConfiguration(module.getProject());
    if (runConfig == null || !runConfig.getProfilerState().STARTUP_CPU_PROFILING_ENABLED) {
      return "";
    }

    StartupCpuProfilingConfiguration startupConfig = runConfig.getProfilerState().getStartupCpuProfilingConfiguration();
    CpuProfiler.StartupProfilingResponse response = profilerService
      .getProfilerClient().getCpuClient()
      .startStartupProfiling(CpuProfiler.StartupProfilingRequest
                               .newBuilder()
                               .setAppPackage(appPackageName)
                               .setDeviceId(deviceId)
                               .setConfiguration(getCpuProfilerConfiguration(startupConfig, device))
                               .build());
    if (response.getFilePath().isEmpty()) {
      return "";
    }
    StringBuilder argsBuilder = new StringBuilder("--start-profiler ").append(response.getFilePath());
    if (startupConfig.getTechnology() == StartupCpuProfilingConfiguration.Technology.SAMPLED_JAVA) {
      argsBuilder.append(" --sampling ").append(startupConfig.getSamplingInterval());
    }

    if (device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O) {
      argsBuilder.append(" --streaming");
    }
    return argsBuilder.toString();
  }

  @NotNull
  private static CpuProfiler.CpuProfilerConfiguration getCpuProfilerConfiguration(@NotNull StartupCpuProfilingConfiguration configuration,
                                                                               @NotNull IDevice device) {
    CpuProfiler.CpuProfilerConfiguration.Builder request = CpuProfiler.CpuProfilerConfiguration.newBuilder();
    switch (configuration.getTechnology()) {
      case SAMPLED_JAVA:
        request.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        request.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
        break;
      case INSTRUMENTED_JAVA:
        request.setProfilerType(CpuProfiler.CpuProfilerType.ART);
        request.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.INSTRUMENTED);
        break;
      case SAMPLED_NATIVE:
        request.setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF);
        request.setMode(CpuProfiler.CpuProfilerConfiguration.Mode.SAMPLED);
        break;
    }
    request.setSamplingIntervalUs(configuration.getSamplingInterval());
    return request.build();
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
   * @return ID of device, i.e {@link Common.Device#getDeviceId()}
   */
  private static long waitForPerfd(@NotNull IDevice device, @NotNull ProfilerService profilerService)
    throws InterruptedException, TimeoutException {
    // Wait for perfd to come online for 1 minute.
    for (int i = 0; i < 60; ++i) {
      Profiler.GetDevicesResponse response =
        profilerService.getProfilerClient().getProfilerClient()
          .getDevices(Profiler.GetDevicesRequest.getDefaultInstance());

      for (Common.Device profilerDevice : response.getDeviceList()) {
        if (profilerDevice.getSerial().equals(device.getSerialNumber())) {
          return profilerDevice.getDeviceId();
        }
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    }
    throw new TimeoutException("Timeout waiting for perfd");
  }

  @NotNull
  private static String getAbiDependentLibPerfaName(IDevice device) {
    File dir = new File(PathManager.getHomePath(), "plugins/android/resources/perfa");
    if (!dir.exists()) {
      dir = new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/profiler/native/perfa/android");
    }
    for (String abi : device.getAbis()) {
      File candidate = new File(dir, abi + "/libperfa.so");
      if (candidate.exists()) {
        String abiCpuArch = Abi.getEnum(abi).getCpuArch();
        return String.format("libperfa_%s.so", abiCpuArch);
      }
    }
    return "";
  }

  public static final class AndroidProfilerToolWindowLaunchTask implements LaunchTask {
    @NotNull private final Project myProject;

    public AndroidProfilerToolWindowLaunchTask(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    public String getDescription() {
      return "Presents the Android Profiler Tool Window";
    }

    @Override
    public int getDuration() {
      return LaunchTaskDurations.LAUNCH_ACTIVITY;
    }

    @Override
    public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
      ApplicationManager.getApplication().invokeLater(
        () -> {
          ToolWindow window = ToolWindowManagerEx.getInstanceEx(myProject).getToolWindow(AndroidProfilerToolWindowFactory.ID);
          if (window != null) {
            window.setShowStripeButton(true);
            AndroidProfilerToolWindow profilerToolWindow = AndroidProfilerToolWindowFactory.getProfilerTooWindow(myProject);
            if (profilerToolWindow != null) {
              profilerToolWindow.profileProject(myProject);
            }
          }
        });
      return true;
    }
  }
}