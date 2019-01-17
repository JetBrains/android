/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.runtimePipeline.DeployableFile;
import com.android.tools.idea.runtimePipeline.DeployableFileManager;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

public final class ProfilerDeviceFileManager extends DeployableFileManager {
  private static class HostFiles {
    @NotNull static final DeployableFile PERFD = new ProfilerHostFileBuilder("perfd")
      .setReleaseDir("plugins/android/resources/perfd")
      .setDevDir("../../bazel-bin/tools/base/profiler/native/perfd/android")
      .setExecutable(true)
      .build();

    @NotNull static final DeployableFile PERFA = new ProfilerHostFileBuilder("perfa.jar").build();

    @NotNull static final DeployableFile PERFA_OKHTTP = new ProfilerHostFileBuilder("perfa_okhttp.dex").build();

    @NotNull static final DeployableFile JVMTI_AGENT = new ProfilerHostFileBuilder("libperfa.so")
      .setReleaseDir("plugins/android/resources/perfa")
      .setDevDir("../../bazel-bin/tools/base/profiler/native/perfa/android")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("libperfa_%s.so") // e.g. libperfa_arm64.so
      .build();

    @NotNull static final DeployableFile SIMPLEPERF = new ProfilerHostFileBuilder("simpleperf")
      .setReleaseDir("plugins/android/resources/simpleperf")
      .setDevDir("../../prebuilts/tools/common/simpleperf")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s") // e.g simpleperf_arm64
      .build();
  }

  private static final String DEVICE_SUB_DIR = "perfd/";

  private static int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);

  private static final String DEVICE_SOCKET_NAME = "AndroidStudioProfiler";
  private static final String AGENT_CONFIG_FILE = "agent.config";
  private static final int DEVICE_PORT = 12389;

  public ProfilerDeviceFileManager(@NotNull IDevice device) {
    super(device);
  }

  @Override
  @NotNull
  protected String getDeviceSubDir() {
    return DEVICE_SUB_DIR;
  }

  public void copyProfilerFilesToDevice()
    throws AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException, SyncException, TimeoutException {
    // Copy resources into device directory, all resources need to be included in profiler-artifacts target to build and
    // in AndroidStudioProperties.groovy to package in release.
    copyFileToDevice(HostFiles.PERFD);
    if (isAtLeastO(myDevice)) {
      copyFileToDevice(HostFiles.PERFA);
      copyFileToDevice(HostFiles.PERFA_OKHTTP);
      copyFileToDevice(HostFiles.JVMTI_AGENT);
      // Simpleperf can be used by CPU profiler for method tracing, if it is supported by target device.
      // TODO: In case of simpleperf, remember the device doesn't support it, so we don't try to use it to profile the device.
      copyFileToDevice(HostFiles.SIMPLEPERF);
    }
    pushAgentConfig(myDevice, true);
  }

  /**
   * Exposes superclass method for ProfilerDeviceFileManagerTest, to keep the superclass method protected
   */
  @VisibleForTesting
  void copyHostFileToDevice(@NotNull DeployableFile hostFile) throws AdbCommandRejectedException, IOException {
    copyFileToDevice(hostFile);
  }

  @NotNull
  public static String getPerfdPath() {
    return DEVICE_BASE_DIR + DEVICE_SUB_DIR + "perfd";
  }

  @NotNull
  public static String getAgentConfigPath() {
    return DEVICE_BASE_DIR + DEVICE_SUB_DIR + AGENT_CONFIG_FILE;
  }

  /**
   * Whether the device is running O or higher APIs
   */
  private static boolean isAtLeastO(IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  /**
   * Creates and pushes a config file that lives in perfd but is shared between both perfd + perfa
   */
  static void pushAgentConfig(IDevice device, boolean isMemoryLiveAllocationEnabledAtStartup)
    throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
    int liveAllocationSamplingRate;
    if (StudioFlags.PROFILER_SAMPLE_LIVE_ALLOCATIONS.get()) {
      // If memory live allocation is enabled, read sampling rate from preferences. Otherwise suspend live allocation.
      if (isMemoryLiveAllocationEnabledAtStartup) {
        liveAllocationSamplingRate = PropertiesComponent.getInstance().getInt(
          IntellijProfilerPreferences.getProfilerPropertyName(
            MemoryProfilerStage.LIVE_ALLOCATION_SAMPLING_PREF),
          MemoryProfilerStage.DEFAULT_LIVE_ALLOCATION_SAMPLING_MODE.getValue());
      }
      else {
        liveAllocationSamplingRate = MemoryProfilerStage.LiveAllocationSamplingMode.NONE.getValue();
      }
    }
    else {
      // Sampling feature is disabled, use full mode.
      liveAllocationSamplingRate = MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue();
    }
    Agent.SocketType socketType = isAtLeastO(device) ? Agent.SocketType.ABSTRACT_SOCKET : Agent.SocketType.UNSPECIFIED_SOCKET;
    Agent.AgentConfig agentConfig =
      Agent.AgentConfig.newBuilder()
                       .setMemConfig(
                         Agent.AgentConfig.MemoryConfig
                           .newBuilder()
                           .setUseLiveAlloc(StudioFlags.PROFILER_USE_LIVE_ALLOCATIONS.get())
                           .setMaxStackDepth(LIVE_ALLOCATION_STACK_DEPTH)
                           .setTrackGlobalJniRefs(StudioFlags.PROFILER_TRACK_JNI_REFS.get())
                           .setSamplingRate(
                             MemoryProfiler.AllocationSamplingRate.newBuilder().setSamplingNumInterval(liveAllocationSamplingRate).build())
                           .build())
                       .setSocketType(socketType).setServiceAddress("127.0.0.1:" + DEVICE_PORT)
                       // Using "@" to indicate an abstract socket in unix.
                       .setServiceSocketName("@" + DEVICE_SOCKET_NAME)
                       .setEnergyProfilerEnabled(StudioFlags.PROFILER_ENERGY_PROFILER_ENABLED.get())
                       .setCpuApiTracingEnabled(StudioFlags.PROFILER_CPU_API_TRACING.get())
                       .setAndroidFeatureLevel(device.getVersion().getFeatureLevel())
                       .setCpuConfig(
                         Agent.AgentConfig.CpuConfig.newBuilder()
                           .setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC)
                           .setSimpleperfHost(StudioFlags.PROFILER_SIMPLEPERF_HOST.get()))
                       .setUnifiedPipeline(StudioFlags.PROFILER_UNIFIED_PIPELINE.get())
                       .build();

    File configFile = FileUtil.createTempFile(AGENT_CONFIG_FILE, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    agentConfig.writeTo(oStream);
    device.executeShellCommand("rm -f " + DEVICE_BASE_DIR + DEVICE_SUB_DIR + AGENT_CONFIG_FILE, new NullOutputReceiver());
    device.pushFile(configFile.getAbsolutePath(), DEVICE_BASE_DIR + DEVICE_SUB_DIR + AGENT_CONFIG_FILE);
  }
}
