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


import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.profiler.AbstractProfilerExecutorGroup;
import com.android.tools.idea.run.profiler.ProfilingMode;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Common.CommonConfig;
import com.android.tools.profiler.proto.Transport;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.messages.MessageBus;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransportFileManager implements TransportFileCopier {

  private static class HostFiles {
    @NotNull static final DeployableFile TRANSPORT = new DeployableFile.Builder("transport")
      .setReleaseDir(Constants.TRANSPORT_RELEASE_DIR)
      .setDevDir(Constants.TRANSPORT_DEV_DIR)
      .setExecutable(true)
      .build();

    @NotNull static final DeployableFile PERFA = new DeployableFile.Builder("perfa.jar").build();

    @NotNull static final DeployableFile JVMTI_AGENT = new DeployableFile.Builder("libjvmtiagent.so")
      .setReleaseDir(Constants.JVMTI_AGENT_RELEASE_DIR)
      .setDevDir(Constants.JVMTI_AGENT_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("libjvmtiagent_%s.so") // e.g. libjvmtiagent_arm64.so
      .build();

    @NotNull static final DeployableFile SIMPLEPERF = new DeployableFile.Builder("simpleperf")
      .setReleaseDir(Constants.SIMPLEPERF_RELEASE_DIR)
      .setDevDir(Constants.SIMPLEPERF_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s") // e.g simpleperf_arm64
      .build();

    @NotNull static final DeployableFile PERFETTO = new DeployableFile.Builder("perfetto")
      .setReleaseDir(Constants.PERFETTO_RELEASE_DIR)
      .setDevDir(Constants.PERFETTO_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("perfetto_%s") // e.g perfetto_arm64
      .build();

    @NotNull static final DeployableFile PERFETTO_SO = new DeployableFile.Builder("libperfetto.so")
      .setReleaseDir(Constants.PERFETTO_RELEASE_DIR)
      .setDevDir(Constants.PERFETTO_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("%s/libperfetto.so") // e.g arm64/libperfetto.so
      .build();

    @NotNull static final DeployableFile TRACED = new DeployableFile.Builder("traced")
      .setReleaseDir(Constants.PERFETTO_RELEASE_DIR)
      .setDevDir(Constants.PERFETTO_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("traced_%s") // e.g traced_arm64
      .build();

    @NotNull static final DeployableFile TRACED_PROBE = new DeployableFile.Builder("traced_probes")
      .setReleaseDir(Constants.PERFETTO_RELEASE_DIR)
      .setDevDir(Constants.PERFETTO_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("traced_probes_%s") // e.g traced_probe_arm64
      .build();

    @NotNull static final DeployableFile TRACEBOX = new DeployableFile.Builder("tracebox")
      .setReleaseDir(Constants.TRACEBOX_RELEASE_DIR)
      .setDevDir(Constants.TRACEBOX_DEV_DIR)
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("tracebox_%s") // e.g tracebox_arm64
      .build();
  }

  private static Logger getLogger() {
    return Logger.getInstance(TransportFileManager.class);
  }

  static final String DEVICE_DIR = "/data/local/tmp/perfd/";
  private static final String CODE_CACHE_DIR = "code_cache";
  private static final String DAEMON_CONFIG_FILE = "daemon.config";
  private static final String AGENT_CONFIG_FILE = "agent.config";
  private static final int DEVICE_PORT = 12389;
  @NotNull private final IDevice myDevice;
  @NotNull private final MessageBus myMessageBus;

  public TransportFileManager(@NotNull IDevice device, @NotNull MessageBus messageBus) {
    myDevice = device;
    myMessageBus = messageBus;
  }

  public void copyFilesToDevice()
    throws AdbCommandRejectedException, IOException, ShellCommandUnresponsiveException, SyncException, TimeoutException {
    if (!StudioFlags.TRANSPORT_CONSERVATIVE_COPY.get()) {
      myDevice.executeShellCommand("rm -rf " + DEVICE_DIR, new NullOutputReceiver());
    }
    // Copy resources into device directory, all resources need to be included in profiler-artifacts target to build and
    // in AndroidStudioProperties.groovy to package in release.
    copyFileToDevice(HostFiles.TRANSPORT);
    if (isAtLeastO(myDevice)) {
      copyFileToDevice(HostFiles.PERFA);
      copyFileToDevice(HostFiles.JVMTI_AGENT);
      // Simpleperf can be used by CPU profiler for method tracing, if it is supported by target device.
      // TODO: In case of simpleperf, remember the device doesn't support it, so we don't try to use it to profile the device.
      copyFileToDevice(HostFiles.SIMPLEPERF);
    }
    if (StudioFlags.PROFILER_TRACEBOX.get()) {
      if(isBetweenMAndP(myDevice)) {
        copyFileToDevice(HostFiles.TRACEBOX);
      }
    }
    else if (myDevice.getVersion().getFeatureLevel() == AndroidVersion.VersionCodes.P) {
      // Profiler is side-loading Perfetto on P. On Q+, the system image's Perfetto is used.
      copyFileToDevice(HostFiles.PERFETTO);
      copyFileToDevice(HostFiles.PERFETTO_SO);
      copyFileToDevice(HostFiles.TRACED);
      copyFileToDevice(HostFiles.TRACED_PROBE);
    }

    pushDaemonConfig();
    pushAgentConfig(AGENT_CONFIG_FILE, null);
  }

  @NotNull
  static String getTransportExecutablePath() {
    return DEVICE_DIR + HostFiles.TRANSPORT.getFileName();
  }

  @NotNull
  public static String getDaemonConfigPath() {
    return DEVICE_DIR + DAEMON_CONFIG_FILE;
  }

  @NotNull
  public static String getAgentConfigFile() {
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
   * Whether the device is running version between M and P with both ends (M and P) inclusive
   */
  private static boolean isBetweenMAndP(IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.M &&
           device.getVersion().getFeatureLevel() <= AndroidVersion.VersionCodes.P;
  }

  /**
   * Creates and pushes a config file for configuring the daemon.
   */
  private void pushDaemonConfig()
    throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
    Transport.DaemonConfig.Builder configBuilder = Transport.DaemonConfig.newBuilder().setCommon(buildCommonConfig());
    for (TransportConfigContributor extension : TransportConfigContributor.EP_NAME.getExtensions()) {
      extension.customizeDaemonConfig(configBuilder);
    }

    File configFile = FileUtil.createTempFile(DAEMON_CONFIG_FILE, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    configBuilder.build().writeTo(oStream);
    myDevice.executeShellCommand("rm -f " + DEVICE_DIR + DAEMON_CONFIG_FILE, new NullOutputReceiver());
    myDevice.pushFile(configFile.getAbsolutePath(), DEVICE_DIR + DAEMON_CONFIG_FILE);
    myDevice.executeShellCommand("chown shell:shell " + DEVICE_DIR + DAEMON_CONFIG_FILE, new NullOutputReceiver());
  }

  /**
   * Creates and pushes a config file used for configuring the agent.
   */
  public void pushAgentConfig(@NotNull String configName, @Nullable AndroidRunConfigurationBase runConfig)
    throws AdbCommandRejectedException, IOException, TimeoutException, SyncException, ShellCommandUnresponsiveException {
    Agent.AgentConfig.Builder agentConfigBuilder = Agent.AgentConfig.newBuilder().setCommon(buildCommonConfig());
    for (TransportConfigContributor extension : TransportConfigContributor.EP_NAME.getExtensions()) {
      extension.customizeAgentConfig(agentConfigBuilder, runConfig);
    }

    File configFile = FileUtil.createTempFile(configName, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    agentConfigBuilder.build().writeTo(oStream);
    myDevice.executeShellCommand("rm -f " + DEVICE_DIR + configName, new NullOutputReceiver());
    myDevice.pushFile(configFile.getAbsolutePath(), DEVICE_DIR + configName);
    myDevice.executeShellCommand("chown shell:shell " + DEVICE_DIR + configName, new NullOutputReceiver());
  }

  @NotNull
  private CommonConfig.Builder buildCommonConfig() {
    CommonConfig.SocketType socketType =
      isAtLeastO(myDevice) ? CommonConfig.SocketType.ABSTRACT_SOCKET : CommonConfig.SocketType.UNSPECIFIED_SOCKET;
    return CommonConfig.newBuilder()
      .setSocketType(socketType)
      .setServiceAddress("127.0.0.1:" + DEVICE_PORT)
      // Using "@" to indicate an abstract socket in unix.
      .setServiceSocketName("@" + TransportDeviceManager.DEVICE_SOCKET_NAME);
  }


  /**
   * Copies a file from host (where Studio is running) to the device.
   * If executable, then the abi is taken into account, which may result in multiple files copied.
   * <p>
   * Returns a list of the on-device paths of copied files.
   */
  @Override
  public List<String> copyFileToDevice(@NotNull DeployableFile hostFile)
    throws AdbCommandRejectedException, IOException {
    final Path dirPath = hostFile.getDir().toPath();
    List<String> paths = new ArrayList<>();

    if (!hostFile.isExecutable()) {
      Path path = dirPath.resolve(hostFile.getFileName());
      paths.add(pushFileToDevice(path, hostFile.getFileName(), hostFile.isExecutable()));
      return paths;
    }

    if (!hostFile.isAbiDependent()) {
      Abi abi = getBestAbi(hostFile);
      Path path = dirPath.resolve(abi + "/" + hostFile.getFileName());
      paths.add(pushFileToDevice(path, hostFile.getFileName(), true));
    }
    else {
      String format = hostFile.getOnDeviceAbiFileNameFormat();
      assert format != null;
      for (Abi abi : getBestAbis(hostFile)) {
        Path path = dirPath.resolve(abi + "/" + hostFile.getFileName());
        paths.add(pushFileToDevice(path, String.format(format, abi.getCpuArch()), true));
      }
    }
    return paths;
  }

  private String pushFileToDevice(Path localPath, String fileName, boolean executable)
    throws AdbCommandRejectedException, IOException {
    // Refrain from using platform independent utility to concatenate path (ex: Paths.get) because this file path is intended for Android
    // file system which uses UNIX fashioned path whereas the host (the machine that executes this code) may be a Windows machine.
    String deviceFilePath = DEVICE_DIR + fileName;
    try {
      // TODO: Handle the case where we don't have file for this platform.
      if (!Files.exists(localPath)) {
        throw new TransportNonExistingFileException(String.format("File %s could not be found for device: %s", localPath, myDevice),
                                                    localPath.toString());
      }

      String fileHash = "";
      String hashFilePath = "";

      if (StudioFlags.TRANSPORT_CONSERVATIVE_COPY.get()) {
        // Calculate the hash for the file we are trying to push.
        fileHash = generateHash(localPath.toFile());
        // The path of the file containing the hash.
        hashFilePath = deviceFilePath + "_hash";
        boolean hasSameHash = compareOnDeviceTextFile(hashFilePath, fileHash);

        boolean isFileOnDevice = isFileOnDevice(deviceFilePath);

        if (hasSameHash && isFileOnDevice) {
          getLogger().info(String.format("Identical copy of %s is already on the device, no need to push it", deviceFilePath));
          // The hash stored on the device is the same as the hash of the new file - we don't need to push it again.
          return deviceFilePath;
        }
      }

      /*
       * If copying the agent fails, we will attach the previous version of the agent
       * Hence we first delete old agent before copying new one
       */
      getLogger().info(String.format("Pushing %s to %s...", fileName, DEVICE_DIR));
      // Delete both binary and hash files. It's harmless to include the hash file in the command even if it doesn't exist.
      myDevice.executeShellCommand("rm -f " + deviceFilePath + " " + deviceFilePath + "_hash", new NullOutputReceiver());
      // Make the directory not writable for the group or the world. Otherwise, any unprivileged app running on device can replace the
      // content of file in this directory and archive escalation of privileges when Android Studio will decide to launch the
      // corresponding functionality.
      // "chown shell:shell" ensures proper ownership of DEVICE_DIR if adb is rooted.
      String folder = deviceFilePath.substring(0, deviceFilePath.lastIndexOf('/'));
      myDevice.executeShellCommand("mkdir -p -m 755 " + folder + "; chown shell:shell " + folder, new NullOutputReceiver());
      myDevice.pushFile(localPath.toString(), deviceFilePath);
      myDevice.executeShellCommand("chown shell:shell " + deviceFilePath, new NullOutputReceiver());

      if (StudioFlags.TRANSPORT_CONSERVATIVE_COPY.get()) {
        // Create the hash file on the device, do this after pushing the file,
        // to make sure we don't end up in situations where we have the hash file but not the file itself.

        // Throughout the codebase there are assumptions on how the transport files are called, which means we can't easily
        // append the hash to the name of a file. This is why a separate hash file is used.
        myDevice.executeShellCommand("echo " + fileHash + " > " + hashFilePath, new NullOutputReceiver());
      }

      if (executable) {
        /*
         * Use chmod octal scheme to ensure the executable is not writable for the group or the world. Otherwise, any unprivileged app
         * running on device can replace the content of file and archive escalation of privileges when Android Studio will decide to
         * launch the corresponding functionality.
         * We could use "chmod -w" and "chmod +x". However, in older devices, chmod letter usage isn't fully supported although CTS tests
         * have been added for it since.
         */
        String cmd = "chmod 755 " + deviceFilePath;
        myDevice.executeShellCommand(cmd, new NullOutputReceiver());
      }
      else {
        /*
         * Starting with API 34 there is an additional check that a dex cannot be writable (see dalvik_system_DexFile.cc).
         */
        if (fileName.endsWith(".jar")) {
          String cmd = "chmod 444 " + deviceFilePath;
          myDevice.executeShellCommand(cmd, new NullOutputReceiver());
        }
      }
      getLogger().info(String.format("Successfully pushed %s to %s.", fileName, DEVICE_DIR));
    }
    catch (TimeoutException | SyncException | ShellCommandUnresponsiveException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return deviceFilePath;
  }

  /**
   * Returns true if the file exists on the device.
   * @param filePath the on device path to check for the file.
   */
  private boolean isFileOnDevice(String filePath)
    throws ShellCommandUnresponsiveException, AdbCommandRejectedException, IOException, TimeoutException {
    final boolean[] fileFound = {false};
    myDevice.executeShellCommand("ls " + filePath, new MultiLineReceiver() {
      @Override
      public void processNewLines(@NotNull String[] lines) {
        for (String line : lines) {
          if (line.equals(filePath)) {
            fileFound[0] = true;
            break;
          }
        }
      }

      @Override
      public boolean isCancelled() {
        return false;
      }
    });

    return fileFound[0];
  }

  /**
   * Reads the content of the file provided as input and compares it with the provided expected content.
   * @param filePath the path of the on-device file that we want to read.
   * @param expectedContent content we expect to read from the file.
   * @return true if the actual content of the file is identical to the expected content.
   */
  private boolean compareOnDeviceTextFile(String filePath, String expectedContent)
    throws IOException, NoSuchAlgorithmException, ShellCommandUnresponsiveException, AdbCommandRejectedException, TimeoutException {

    StringBuilder fileContent = new StringBuilder();
    myDevice.executeShellCommand("cat " + filePath, new MultiLineReceiver() {
      @Override
      public void processNewLines(@NotNull String[] lines) {
        for (String line : lines) {
          fileContent.append(line);
        }
      }

      @Override
      public boolean isCancelled() {
        return false;
      }
    });

    return fileContent.toString().trim().equals(expectedContent);
  }

  private static String generateHash(File file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] bytes = Files.readAllBytes(file.toPath());
    byte[] hashBytes = digest.digest(bytes);
    StringBuilder hashString = new StringBuilder();
    for (byte b : hashBytes) {
      // Append each byte as a two-character hexadecimal string.
      hashString.append(String.format("%02x", b));
    }
    return hashString.toString();
  }

  /**
   * Pushes the necessary filers into the package's folder for supporting attaching agent on startup.
   *
   * @param packageName The package to launch agent with.
   * @param configName  The agent config file name that should be passed along into the agent. This assumes it already existing under
   *                    {@link #DEVICE_DIR}, which can be done via {@link #pushAgentConfig(String, AndroidRunConfigurationBase)}.
   * @param executorId  The executor ID is useful for determining whether the build is profileable.
   * @return the parameter needed to for the 'am start' command to launch an app with the startup agent, if the package's data folder is
   * accessible, empty string otherwise.
   */
  public String configureStartupAgent(@NotNull String packageName, @NotNull String configName, @NotNull String executorId) {
    // Startup agent feature was introduced from android API level 27.
    if (myDevice.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O_MR1) {
      return "";
    }

    // JVMTI agents are unsupported in profileable builds.
    AbstractProfilerExecutorGroup.AbstractProfilerSetting setting =
      AbstractProfilerExecutorGroup.Companion.getExecutorSetting(executorId);
    if (setting != null && setting.getProfilingMode() == ProfilingMode.PROFILEABLE) {
      return "";
    }

    String packageDataPath = getPackageDataPath(packageName);
    if (packageDataPath.isEmpty()) {
      return "";
    }

    String agentName = String.format(HostFiles.JVMTI_AGENT.getOnDeviceAbiFileNameFormat(), getBestAbi(HostFiles.JVMTI_AGENT).getCpuArch());
    String[] requiredAgentFiles = {agentName, HostFiles.PERFA.getFileName()};
    try {
      for (String agentFile : requiredAgentFiles) {
        // First remove the file if it already exists in the package folder.
        // If old file exists and this fails to copy the new one, the app would attach using the old files and some weird bugs may occur.
        myDevice.executeShellCommand(buildRunAsCommand(packageName, String.format("rm -rf ./%s/%s", CODE_CACHE_DIR, agentFile)),
                                     new NullOutputReceiver());
        myDevice.executeShellCommand(buildRunAsCommand(packageName, String.format("cp %s ./%s/", DEVICE_DIR + agentFile, CODE_CACHE_DIR)),
                                     new NullOutputReceiver());
      }
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException ignored) {
      return "";
    }

    // Example: --attach-agent /data/data/package_name/code_cache/libjvmtiagent_x86.so=/data/local/tmp/perfd/startupagent.config
    return String.format("--attach-agent %s/%s/%s=%s", packageDataPath, CODE_CACHE_DIR, agentName, DEVICE_DIR + configName);
  }

  /**
   * @return the on-device package's data path if it is available, empty string otherwise.
   */
  @NotNull
  private String getPackageDataPath(@NotNull String packageName) {
    String[] result = new String[1];
    try {
      myDevice.executeShellCommand(buildRunAsCommand(packageName, "pwd"), new MultiLineReceiver() {
        @Override
        public void processNewLines(@NonNull String[] lines) {
          if (result[0] == null) {
            result[0] = lines[0];
          }
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException ignored) {
    }

    // If the command returns "run-as: ...", the package cannot be found or run-as.
    if (result[0] == null || result[0].startsWith("run-as: ")) {
      return "";
    }
    else {
      return result[0];
    }
  }

  @NotNull
  private String buildRunAsCommand(@NotNull String packageName, @NotNull String command) {
    return String.format("run-as %s sh -c '%s'", packageName, command);
  }

  @NotNull
  private Abi getBestAbi(@NotNull DeployableFile hostFile) {
    List<Abi> abis = getBestAbis(hostFile);
    if (abis.isEmpty()){
      throw new RuntimeException("Could not find ABI file for: " + hostFile.getFileName());
    } else {
      return abis.get(0);
    }
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

  /**
   * A wrapper to convert long ABI name to a short one, e.g., from "arm64-v8a" to "arm64".
   *
   * It's designed for modules that cannot directly depend on com.android.sdklib.devices.Abi.
   */
  @NotNull
  public static String getShortAbiName(@NotNull String longAbi) {
    Abi abi = Abi.getEnum(longAbi);
    if (abi == null) {
      return "invalid_abi";
    }
    return abi.getCpuArch();
  }
}
