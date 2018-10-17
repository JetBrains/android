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
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.google.common.base.Charsets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class ProfilerDeviceFileManager {
  private static class HostFiles {
    @NotNull static final ProfilerHostFile PERFD = new ProfilerHostFile.Builder("perfd")
      .setReleaseDir("plugins/android/resources/perfd")
      .setDevDir("../../bazel-bin/tools/base/profiler/native/perfd/android")
      .setExecutable(true)
      .build();

    @NotNull static final ProfilerHostFile PERFA = new ProfilerHostFile.Builder("perfa.jar").build();

    @NotNull static final ProfilerHostFile PERFA_OKHTTP = new ProfilerHostFile.Builder("perfa_okhttp.dex").build();

    @NotNull static final ProfilerHostFile JVMTI_AGENT = new ProfilerHostFile.Builder("libperfa.so")
      .setReleaseDir("plugins/android/resources/perfa")
      .setDevDir("../../bazel-bin/tools/base/profiler/native/perfa/android")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("libperfa_%s.so") // e.g. libperfa_arm64.so
      .build();

    @NotNull static final ProfilerHostFile SIMPLEPERF = new ProfilerHostFile.Builder("simpleperf")
      .setReleaseDir("plugins/android/resources/simpleperf")
      .setDevDir("../../prebuilts/tools/common/simpleperf")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s") // e.g simpleperf_arm64
      .build();
  }

  static final String DEVICE_DIR = "/data/local/tmp/perfd/";

  private static int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);

  private static final String DEVICE_SOCKET_NAME = "AndroidStudioProfiler";
  private static final String AGENT_CONFIG_FILE = "agent.config";
  private static final int DEVICE_PORT = 12389;

  @NotNull private final IDevice myDevice;

  public ProfilerDeviceFileManager(@NotNull IDevice device) {
    myDevice = device;
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
      copyFileToDevice(HostFiles.SIMPLEPERF);
    }
    pushAgentConfig(myDevice, true);
  }

  @NotNull
  public static String getPerfdPath() {
    return DEVICE_DIR + "perfd";
  }

  @NotNull
  public static String getAgentConfigPath() {
    return DEVICE_DIR + AGENT_CONFIG_FILE;
  }

  private static Logger getLogger() {
    return Logger.getInstance(ProfilerDeviceFileManager.class);
  }

  /**
   * Copies a file from host (where Studio is running) to the device.
   * If executable, then the abi is taken into account.
   */
  @VisibleForTesting
  void copyFileToDevice(@NotNull ProfilerHostFile hostFile)
    throws AdbCommandRejectedException, IOException {
    final File dir = hostFile.getDir();

    if (!hostFile.isExecutable()) {
      File file = new File(dir, hostFile.getFileName());
      pushFileToDevice(file, hostFile.getFileName(), hostFile.isExecutable());
      return;
    }

    if (!hostFile.isAbiDependent()) {
      Abi abi = getBestAbi(hostFile);
      File file = new File(dir, abi + "/" + hostFile.getFileName());
      pushFileToDevice(file, hostFile.getFileName(), true);
    }
    else {
      String format = hostFile.getOnDeviceAbiFileNameFormat();
      assert format != null;
      for (Abi abi : getBestAbis(hostFile)) {
        File file = new File(dir, abi + "/" + hostFile.getFileName());
        pushFileToDevice(file, String.format(format, abi.getCpuArch()), true);
      }
    }
  }

  private void pushFileToDevice(File file, String fileName, boolean executable)
    throws AdbCommandRejectedException, IOException {
    try {
      // TODO: Handle the case where we don't have file for this platform.
      // TODO: In case of simpleperf, remember the device doesn't support it, so we don't try to use it to profile the device.
      if (file == null) {
        throw new RuntimeException(String.format("File %s could not be found for device: %s", fileName, myDevice));
      }
      /*
       * If copying the agent fails, we will attach the previous version of the agent
       * Hence we first delete old agent before copying new one
       */
      getLogger().info(String.format("Pushing %s to %s...", fileName, DEVICE_DIR));
      myDevice.executeShellCommand("rm -f " + DEVICE_DIR + fileName, new NullOutputReceiver());
      myDevice.executeShellCommand("mkdir -p " + DEVICE_DIR, new NullOutputReceiver());
      myDevice.pushFile(file.getAbsolutePath(), DEVICE_DIR + fileName);

      if (executable) {
        /*
         * In older devices, chmod letter usage isn't fully supported but CTS tests have been added for it since.
         * Hence we first try the letter scheme which is guaranteed in newer devices, and fall back to the octal scheme only if necessary.
         */
        ChmodOutputListener chmodListener = new ChmodOutputListener();
        myDevice.executeShellCommand("chmod +x " + DEVICE_DIR + fileName, chmodListener);
        if (chmodListener.hasErrors()) {
          myDevice.executeShellCommand("chmod 777 " + DEVICE_DIR + fileName, new NullOutputReceiver());
        }
      }
      getLogger().info(String.format("Successfully pushed %s to %s.", fileName, DEVICE_DIR));
    }
    catch (TimeoutException | SyncException | ShellCommandUnresponsiveException e) {
      throw new RuntimeException(e);
    }
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
                         Agent.AgentConfig.CpuConfig.newBuilder().setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC))
                       .setUnifiedPipeline(StudioFlags.PROFILER_UNIFIED_PIPELINE.get())
                       .build();

    File configFile = FileUtil.createTempFile(AGENT_CONFIG_FILE, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    agentConfig.writeTo(oStream);
    device.executeShellCommand("rm -f " + DEVICE_DIR + AGENT_CONFIG_FILE, new NullOutputReceiver());
    device.pushFile(configFile.getAbsolutePath(), DEVICE_DIR + AGENT_CONFIG_FILE);
  }

  @NotNull
  private Abi getBestAbi(@NotNull ProfilerHostFile hostFile) {
    return getBestAbis(hostFile).get(0);
  }

  @NotNull
  private List<Abi> getBestAbis(@NotNull ProfilerHostFile hostFile) {
    final File dir = hostFile.getDir();
    List<Abi> supportedAbis = myDevice.getAbis()
                                      .stream()
                                      .map(abi -> Abi.getEnum(abi))
                                      .filter(abi -> new File(dir, abi + "/" + hostFile.getFileName()).exists())
                                      .collect(Collectors.toList());

    List<Abi> bestAbis = new ArrayList<>();
    Set<String> seenCpuArch = new HashSet<>();
    for (Abi abi : supportedAbis) {
      if (!seenCpuArch.contains(abi.getCpuArch())) {
        seenCpuArch.add(abi.getCpuArch());
        bestAbis.add(abi);
      }
    }
    return bestAbis;
  }

  private static class ChmodOutputListener implements IShellOutputReceiver {
    /**
     * When chmod fails to modify permissions, the following "Bad mode" error string is output.
     * This listener checks if the string is present to validate if chmod was successful.
     */
    private static final String BAD_MODE = "Bad mode";

    private boolean myHasErrors;

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      String s = new String(data, Charsets.UTF_8);
      myHasErrors = s.contains(BAD_MODE);
    }

    @Override
    public void flush() {

    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    private boolean hasErrors() {
      return myHasErrors;
    }
  }
}
