/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

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
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.profiler.proto.Agent;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.messages.MessageBus;
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
import org.jetbrains.annotations.Nullable;

public final class TransportFileManager {
  private static class HostFiles {
    @NotNull static final DeployableFile TRANSPORT = new DeployableFile.Builder("transport")
      .setReleaseDir("plugins/android/resources/transport")
      .setDevDir("../../bazel-bin/tools/base/transport/android")
      .setExecutable(true)
      .build();

    @NotNull static final DeployableFile PERFA = new DeployableFile.Builder("perfa.jar").build();

    @NotNull static final DeployableFile PERFA_OKHTTP = new DeployableFile.Builder("perfa_okhttp.dex").build();

    @NotNull static final DeployableFile JVMTI_AGENT = new DeployableFile.Builder("libperfa.so")
      .setReleaseDir("plugins/android/resources/perfa")
      .setDevDir("../../bazel-bin/tools/base/profiler/native/perfa/android")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("libperfa_%s.so") // e.g. libperfa_arm64.so
      .build();

    @NotNull static final DeployableFile SIMPLEPERF = new DeployableFile.Builder("simpleperf")
      .setReleaseDir("plugins/android/resources/simpleperf")
      .setDevDir("../../prebuilts/tools/common/simpleperf")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s") // e.g simpleperf_arm64
      .build();
  }

  private static Logger getLogger() {
    return Logger.getInstance(TransportFileManager.class);
  }

  static final String DEVICE_DIR = "/data/local/tmp/perfd/";
  static final String AGENT_CONFIG_FILE = "agent.config";
  private static final int DEVICE_PORT = 12389;
  @NotNull private final IDevice myDevice;
  @NotNull private final MessageBus myMessageBus;

  public TransportFileManager(@NotNull IDevice device, @NotNull MessageBus messageBus) {
    myDevice = device;
    myMessageBus = messageBus;
  }

  public void copyFilesToDevice()
    throws AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException, SyncException, TimeoutException {
    // Copy resources into device directory, all resources need to be included in profiler-artifacts target to build and
    // in AndroidStudioProperties.groovy to package in release.
    copyFileToDevice(HostFiles.TRANSPORT);
    if (isAtLeastO(myDevice)) {
      copyFileToDevice(HostFiles.PERFA);
      copyFileToDevice(HostFiles.PERFA_OKHTTP);
      copyFileToDevice(HostFiles.JVMTI_AGENT);
      // Simpleperf can be used by CPU profiler for method tracing, if it is supported by target device.
      // TODO: In case of simpleperf, remember the device doesn't support it, so we don't try to use it to profile the device.
      copyFileToDevice(HostFiles.SIMPLEPERF);
    }
    pushAgentConfig(null);
  }

  @NotNull
  static String getTransportExecutablePath() {
    return DEVICE_DIR + HostFiles.TRANSPORT.getFileName();
  }

  @NotNull
  static String getAgentConfigPath() {
    return DEVICE_DIR + AGENT_CONFIG_FILE;
  }

  /**
   * Exposes superclass method for ProfilerDeviceFileManagerTest, to keep the superclass method protected
   */
  @VisibleForTesting
  void copyHostFileToDevice(@NotNull DeployableFile hostFile) throws AdbCommandRejectedException, IOException {
    copyFileToDevice(hostFile);
  }

  /**
   * Whether the device is running O or higher APIs
   */
  private static boolean isAtLeastO(IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  /**
   * Creates and pushes a config file that lives in transport but is shared between both transport daemon and app agent.
   */
  public void pushAgentConfig(@Nullable AndroidRunConfigurationBase runConfig)
    throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
    Agent.SocketType socketType = isAtLeastO(myDevice) ? Agent.SocketType.ABSTRACT_SOCKET : Agent.SocketType.UNSPECIFIED_SOCKET;
    Agent.AgentConfig.Builder agentConfigBuilder =
      Agent.AgentConfig.newBuilder()
        .setSocketType(socketType).setServiceAddress("127.0.0.1:" + DEVICE_PORT)
        // Using "@" to indicate an abstract socket in unix.
        .setServiceSocketName("@" + TransportDeviceManager.DEVICE_SOCKET_NAME)
        .setAndroidFeatureLevel(myDevice.getVersion().getFeatureLevel());
    myMessageBus.syncPublisher(TransportDeviceManager.TOPIC).customizeAgentConfig(agentConfigBuilder, runConfig);

    File configFile = FileUtil.createTempFile(AGENT_CONFIG_FILE, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    agentConfigBuilder.build().writeTo(oStream);
    myDevice.executeShellCommand("rm -f " + DEVICE_DIR + AGENT_CONFIG_FILE, new NullOutputReceiver());
    myDevice.pushFile(configFile.getAbsolutePath(), DEVICE_DIR + AGENT_CONFIG_FILE);
  }

  /**
   * Copies a file from host (where Studio is running) to the device.
   * If executable, then the abi is taken into account.
   */
  protected void copyFileToDevice(@NotNull DeployableFile hostFile)
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
      if (file == null) {
        throw new RuntimeException(String.format("File %s could not be found for device: %s", fileName, myDevice));
      }
      /*
       * If copying the agent fails, we will attach the previous version of the agent
       * Hence we first delete old agent before copying new one
       */
      String deviceFilePath = DEVICE_DIR + fileName;
      getLogger().info(String.format("Pushing %s to %s...", fileName, DEVICE_DIR));
      myDevice.executeShellCommand("rm -f " + deviceFilePath, new NullOutputReceiver());
      myDevice.executeShellCommand("mkdir -p " + DEVICE_DIR, new NullOutputReceiver());
      myDevice.pushFile(file.getAbsolutePath(), deviceFilePath);

      if (executable) {
        /*
         * In older devices, chmod letter usage isn't fully supported but CTS tests have been added for it since.
         * Hence we first try the letter scheme which is guaranteed in newer devices, and fall back to the octal scheme only if necessary.
         */
        ChmodOutputListener chmodListener = new ChmodOutputListener();
        myDevice.executeShellCommand("chmod +x " + deviceFilePath, chmodListener);
        if (chmodListener.hasErrors()) {
          myDevice.executeShellCommand("chmod 777 " + deviceFilePath, new NullOutputReceiver());
        }
      }
      getLogger().info(String.format("Successfully pushed %s to %s.", fileName, DEVICE_DIR));
    }
    catch (TimeoutException | SyncException | ShellCommandUnresponsiveException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private Abi getBestAbi(@NotNull DeployableFile hostFile) {
    return getBestAbis(hostFile).get(0);
  }

  @NotNull
  private List<Abi> getBestAbis(@NotNull DeployableFile hostFile) {
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
