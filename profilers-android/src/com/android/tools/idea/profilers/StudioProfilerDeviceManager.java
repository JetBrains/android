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
package com.android.tools.idea.profilers;

import static com.android.ddmlib.IDevice.CHANGE_STATE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.service.ProfilerService;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.perfd.PerfdProxy;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.profiler.CpuProfilerConfig;
import com.android.tools.idea.run.profiler.CpuProfilerConfigsState;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.NetUtils;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn ProfilerClient's.
 * On device connection it will spawn the performance daemon on device, and will notify the profiler system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
class StudioProfilerDeviceManager implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener,
                                             IdeSdks.IdeSdkChangeListener, Disposable {

  private static Logger getLogger() {
    return Logger.getInstance(StudioProfilerDeviceManager.class);
  }

  private static int LIVE_ALLOCATION_STACK_DEPTH = Integer.getInteger("profiler.alloc.stack.depth", 50);

  private static final String BOOT_COMPLETE_PROPERTY = "dev.bootcomplete";
  private static final String BOOT_COMPLETE_MESSAGE = "1";

  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private static final int DEVICE_PORT = 12389;
  // On-device daemon uses Unix abstract socket for O and future devices.
  private static final String DEVICE_SOCKET_NAME = "AndroidStudioProfiler";
  private static final String AGENT_CONFIG_FILE = "agent.config";
  private static final String DEVICE_DIR = "/data/local/tmp/perfd/";

  @NotNull
  private final DataStoreService myDataStoreService;
  private boolean isAdbInitialized;

  /**
   * We rely on the concurrency guarantees of the {@link ConcurrentHashMap} to synchronize our {@link DeviceContext} accesses.
   * All accesses to the {@link DeviceContext} must be through its synchronization methods.
   */
  private final Map<String, DeviceContext> mySerialToDeviceContextMap = new ConcurrentHashMap<>();

  public StudioProfilerDeviceManager(@NotNull DataStoreService dataStoreService) {
    myDataStoreService = dataStoreService;
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
    // TODO: Once adb API doesn't require a project, move initialization to constructor and remove this flag.
    isAdbInitialized = false;
  }

  @Override
  public void sdkPathChanged(@NotNull File newSdkPath) {
    isAdbInitialized = false;
  }

  public void initialize(@NotNull Project project) {
    if (isAdbInitialized) {
      return;
    }

    final File adb = AndroidSdkUtils.getAdb(project);
    if (adb != null) {
      Futures.addCallback(AdbService.getInstance().getDebugBridge(adb), new FutureCallback<AndroidDebugBridge>() {
        @Override
        public void onSuccess(AndroidDebugBridge result) {
          isAdbInitialized = true;
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          getLogger().warn(String.format("getDebugBridge %s failed", adb.getAbsolutePath()));
        }
      }, EdtExecutor.INSTANCE);
    }
    else {
      getLogger().warn("No adb available");
    }
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
    disconnectProxies();
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    if (bridge != null) {
      for (IDevice device : bridge.getDevices()) {
        deviceConnected(device);
      }
    }
    else {
      // Perfd must be spawned through ADB. When |bridge| is null, it means the ADB that was available earlier
      // becomes invalid and every running perfd it had spawned is being killed. As a result, we should kill the
      // corresponding proxies, too.
      disconnectProxies();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    mySerialToDeviceContextMap.computeIfAbsent(device.getSerialNumber(), serial -> new DeviceContext());

    if (device.isOnline()) {
      spawnPerfd(device);
    }
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    disconnectProxy(device);
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    if ((changeMask & CHANGE_STATE) != 0) {
      if (device.isOnline()) {
        spawnPerfd(device);
      }
      else {
        disconnectProxy(device);
      }
    }
  }

  @NotNull
  private Runnable getDisconnectRunnable(@NotNull String serialNumber) {
    return () -> mySerialToDeviceContextMap.compute(serialNumber, (unused, context) -> {
      assert context != null;
      PerfdProxy proxy = context.myLastKnownPerfdProxy;
      if (proxy != null) {
        proxy.disconnect();
      }
      context.myLastKnownPerfdProxy = null;
      return context;
    });
  }

  private void disconnectProxy(@NonNull IDevice device) {
    mySerialToDeviceContextMap.compute(device.getSerialNumber(), (serial, context) -> {
      assert context != null;
      if (context.myLastKnowPerfdThreadfuture != null) {
        context.myLastKnowPerfdThreadfuture.cancel(true);
        context.myLastKnowPerfdThreadfuture = null;
      }
      context.myExecutor.execute(getDisconnectRunnable(serial));
      return context;
    });
  }

  private void disconnectProxies() {
    mySerialToDeviceContextMap.forEach((serial, context) -> {
      assert context != null;
      if (context.myLastKnowPerfdThreadfuture != null) {
        context.myLastKnowPerfdThreadfuture.cancel(true);
        context.myLastKnowPerfdThreadfuture = null;
      }
      context.myExecutor.execute(getDisconnectRunnable(serial));
    });
  }

  private void spawnPerfd(@NonNull IDevice device) {
    mySerialToDeviceContextMap.compute(device.getSerialNumber(), (serial, context) -> {
      assert context != null && (context.myLastKnownPerfdProxy == null || context.myLastKnownPerfdProxy.getDevice() != device);
      context.myLastKnowPerfdThreadfuture = context.myExecutor.submit(new PerfdThread(device, myDataStoreService));
      return context;
    });
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
                       .build();

    File configFile = FileUtil.createTempFile(AGENT_CONFIG_FILE, null, true);
    OutputStream oStream = new FileOutputStream(configFile);
    agentConfig.writeTo(oStream);
    device.executeShellCommand("rm -f " + DEVICE_DIR + AGENT_CONFIG_FILE, new NullOutputReceiver());
    device.pushFile(configFile.getAbsolutePath(), DEVICE_DIR + AGENT_CONFIG_FILE);
  }

  private class PerfdThread implements Runnable {
    @NotNull private final DataStoreService myDataStore;
    @NotNull private final IDevice myDevice;
    private int myLocalPort;
    private volatile PerfdProxy myPerfdProxy;

    public PerfdThread(@NotNull IDevice device, @NotNull DataStoreService datastore) {
      myDataStore = datastore;
      myDevice = device;
      myLocalPort = 0;
    }

    @Override
    public void run() {
      try {
        // Waits to make sure the device has completed boot sequence.
        if (!waitForBootComplete()) {
          throw new TimeoutException("Timed out waiting for device to be ready.");
        }

        // Copy resources into device directory, all resources need to be included in profiler-artifacts target to build and
        // in AndroidStudioProperties.groovy to package in release.
        copyFileToDevice("perfd", "plugins/android/resources/perfd", "../../bazel-bin/tools/base/profiler/native/perfd/android", DEVICE_DIR,
                         true);
        if (isAtLeastO(myDevice)) {
          String productionRoot = "plugins/android/resources";
          String devRoot = "../../bazel-genfiles/tools/base/profiler/app";
          copyFileToDevice("perfa.jar", productionRoot, devRoot, DEVICE_DIR, false);
          copyFileToDevice("perfa_okhttp.dex", productionRoot, devRoot, DEVICE_DIR, false);
          pushJvmtiAgentNativeLibraries(DEVICE_DIR);
          // Simpleperf can be used by CPU profiler for method tracing, if it is supported by target device.
          pushSimpleperf(DEVICE_DIR);
        }
        pushAgentConfig(myDevice, true);

        myDevice.executeShellCommand(DEVICE_DIR + "perfd -config_file=" + DEVICE_DIR + AGENT_CONFIG_FILE, new IShellOutputReceiver() {
          @Override
          public void addOutput(byte[] data, int offset, int length) {
            String s = new String(data, offset, length, Charsets.UTF_8);
            getLogger().info("[perfd]: " + s);

            // On supported API levels (Lollipop+), we should only start the proxy once perfd has successfully launched the grpc server.
            // This is indicated by a "Server listening on ADDRESS" printout from perfd (ADDRESS can vary depending on pre-O vs JVMTI).
            // The reason for this check is because we get linker warnings when starting perfd on pre-M devices (an issue which would not
            // be fixed by now), and we need to avoid starting the proxy in those cases.
            if (myDevice.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.LOLLIPOP && !s.startsWith("Server listening on")) {
              return;
            }

            boolean[] alreadyExists = new boolean[]{false};
            mySerialToDeviceContextMap.compute(myDevice.getSerialNumber(), (serial, context) -> {
              assert context != null;
              if (context.myLastKnownPerfdProxy != null) {
                getLogger().info(String.format("PerfdProxy was already created for device: %s", myDevice));
                alreadyExists[0] = true;
              }
              return context;
            });
            if (alreadyExists[0]) {
              return;
            }

            try {
              createPerfdProxy();
              getLogger().info(String.format("PerfdProxy successfully created for device: %s", myDevice));
            }
            catch (AdbCommandRejectedException | IOException | TimeoutException e) {
              getLogger().warn(String.format("PerfdProxy failed for device: %s", myDevice), e);
            }
          }

          @Override
          public void flush() {
            // flush does not always get called. So we need to perform the proxy server/channel clean up after the perfd process has died.
          }

          @Override
          public boolean isCancelled() {
            if (Thread.interrupted()) {
              Thread.currentThread().interrupt();
              return true;
            }
            return false;
          }
        }, 0, null);

        getLogger().info("Terminating perfd thread");
      }
      catch (TimeoutException | ShellCommandUnresponsiveException | InterruptedException | SyncException e) {
        throw new RuntimeException(e);
      }
      catch (AdbCommandRejectedException | IOException e) {
        // AdbCommandRejectedException and IOException happen when unplugging the device shortly after plugging it in.
        // We don't want to crash in this case.
        getLogger().warn("Error when trying to spawn perfd:");
        getLogger().warn(e);
      }
    }

    /**
     * Copies a file from host (where Studio is running) to the device.
     * If executable, then the abi is taken into account.
     */
    private void copyFileToDevice(String fileName, String hostReleaseDir, String hostDevDir, String deviceDir, boolean executable)
      throws AdbCommandRejectedException, IOException {
      File dir = new File(PathManager.getHomePath(), hostReleaseDir);
      if (!dir.exists()) {
        // Development mode
        dir = new File(PathManager.getHomePath(), hostDevDir);
      }

      File file = null;
      if (executable) {
        for (String abi : myDevice.getAbis()) {
          File candidate = new File(dir, abi + "/" + fileName);
          if (candidate.exists()) {
            file = candidate;
            break;
          }
        }
      }
      else {
        File candidate = new File(dir, fileName);
        if (candidate.exists()) {
          file = candidate;
        }
      }
      pushFileToDevice(file, fileName, deviceDir, executable);
    }

    private void pushFileToDevice(File file, String fileName, String deviceDir, boolean executable)
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
        getLogger().info(String.format("Pushing %s to %s...", fileName, deviceDir));
        myDevice.executeShellCommand("rm -f " + deviceDir + fileName, new NullOutputReceiver());
        myDevice.executeShellCommand("mkdir -p " + deviceDir, new NullOutputReceiver());
        myDevice.pushFile(file.getAbsolutePath(), deviceDir + fileName);

        if (executable) {
          /*
           * In older devices, chmod letter usage isn't fully supported but CTS tests have been added for it since.
           * Hence we first try the letter scheme which is guaranteed in newer devices, and fall back to the octal scheme only if necessary.
           */
          ChmodOutputListener chmodListener = new ChmodOutputListener();
          myDevice.executeShellCommand("chmod +x " + deviceDir + fileName, chmodListener);
          if (chmodListener.hasErrors()) {
            myDevice.executeShellCommand("chmod 777 " + deviceDir + fileName, new NullOutputReceiver());
          }
        }
        getLogger().info(String.format("Successfully pushed %s to %s.", fileName, deviceDir));
      }
      catch (TimeoutException | SyncException | ShellCommandUnresponsiveException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Push JVMTI agent binary to device. The native library is to be attached to app's thread. It needs to be consistent with app's abi,
     * so we use {@link #pushAbiDependentBinaryFiles}.
     */
    private void pushJvmtiAgentNativeLibraries(String devicePath) throws AdbCommandRejectedException, IOException {
      String jvmtiResourcesReleasePath = "plugins/android/resources/perfa";
      String jvmtiResourcesDevPath = "../../bazel-bin/tools/base/profiler/native/perfa/android";
      String libperfaFilename = "libperfa.so";
      String libperfaDeviceFilenameFormat = "libperfa_%s.so"; // e.g. libperfa_arm64.so

      pushAbiDependentBinaryFiles(devicePath, jvmtiResourcesReleasePath, jvmtiResourcesDevPath, libperfaFilename,
                                  libperfaDeviceFilenameFormat);
    }

    /**
     * Push one binary for each supported ABI CPU architecture, i.e. CPU family. ABI of same CPU family can share the same binary,
     * like "armeabi" and "armeabi-v7a", which share the "arm".
     *
     * @param devicePath           Device path where the binaries should be pushed to.
     * @param hostReleaseDir       Host release path containing the binaries to be pushed to device.
     * @param hostDevDir           Host development path containing the binaries to be pushed to device.
     * @param hostFilename         Filename of the original binaries on the host.
     * @param deviceFilenameFormat Format of the binaries filename on device. The binaries have the same name in the host because they're
     *                             usually placed on different folders. On the device, however, the binaries are all placed inside
     *                             {@code devicePath}, so they need different names, each one identifying the ABI corresponding to the
     *                             binary. For instance, the format "libperfa_%s.so" can generate binaries named "libperfa_arm.so",
     *                             "libperfa_x86_64.so", etc.
     * @throws AdbCommandRejectedException
     * @throws IOException
     */
    private void pushAbiDependentBinaryFiles(String devicePath, String hostReleaseDir, String hostDevDir, String hostFilename,
                                             String deviceFilenameFormat) throws AdbCommandRejectedException, IOException {
      File dir = new File(PathManager.getHomePath(), hostReleaseDir);
      if (!dir.exists()) {
        dir = new File(PathManager.getHomePath(), hostDevDir);
      }
      // Multiple abis of same cpu arch need only one binary to push, for example, "armeabi" and "armeabi-v7a" abis' cpu arch is "arm".
      Set<String> cpuArchSet = new HashSet<>();
      for (String abi : myDevice.getAbis()) {
        File candidate = new File(dir, abi + "/" + hostFilename);
        if (candidate.exists()) {
          String abiCpuArch = Abi.getEnum(abi).getCpuArch();
          if (!cpuArchSet.contains(abiCpuArch)) {
            pushFileToDevice(candidate, String.format(deviceFilenameFormat, abiCpuArch), devicePath, true);
            cpuArchSet.add(abiCpuArch);
          }
        }
      }
    }

    /**
     * Pushes simpleperf binaries to device. It needs to be consistent with app's abi, so we use {@link #pushAbiDependentBinaryFiles}.
     */
    private void pushSimpleperf(String devicePath) throws AdbCommandRejectedException, IOException {
      String simpleperfBinariesReleasePath = "plugins/android/resources/simpleperf";
      String simpleperfBinariesDevPath = "../../prebuilts/tools/common/simpleperf";
      String simpleperfFilename = "simpleperf";
      String simpleperfDeviceFilenameFormat = "simpleperf_%s"; // e.g. simpleperf_arm64

      pushAbiDependentBinaryFiles(devicePath, simpleperfBinariesReleasePath, simpleperfBinariesDevPath, simpleperfFilename,
                                  simpleperfDeviceFilenameFormat);
    }

    private void createPerfdProxy() throws TimeoutException, AdbCommandRejectedException, IOException {
      try {
        myLocalPort = NetUtils.findAvailableSocketPort();
        if (myLocalPort < 0) {
          throw new RuntimeException("Unable to find available socket port");
        }

        if (isAtLeastO(myDevice)) {
          myDevice.createForward(myLocalPort, DEVICE_SOCKET_NAME, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
        }
        else {
          myDevice.createForward(myLocalPort, DEVICE_PORT);
        }
        getLogger().info(String.format("Port forwarding created for port: %d", myLocalPort));

        /*
          Creates the channel that is used to connect to the device perfd.

          TODO: investigate why ant build fails to find the ManagedChannel-related classes
          The temporary fix is to stash the currently set context class loader,
          so ManagedChannelProvider can find an appropriate implementation.
         */
        ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(NettyChannelBuilder.class.getClassLoader());
        ManagedChannel perfdChannel = NettyChannelBuilder
          .forAddress("localhost", myLocalPort)
          .usePlaintext(true)
          .maxMessageSize(MAX_MESSAGE_SIZE)
          .build();
        Thread.currentThread().setContextClassLoader(stashedContextClassLoader);

        // Creates a proxy server that the datastore connects to.
        String channelName = myDevice.getSerialNumber();
        myPerfdProxy = new PerfdProxy(myDevice, perfdChannel, channelName);
        myPerfdProxy.connect();

        mySerialToDeviceContextMap.compute(myDevice.getSerialNumber(), (serial, context) -> {
          assert context != null;
          context.myLastKnownPerfdProxy = myPerfdProxy;
          return context;
        });

        // TODO using directexecutor for this channel freezes up grpc calls that are redirected to the device (e.g. GetTimes)
        // We should otherwise do it for performance reasons, so we should investigate why.
        ManagedChannel proxyChannel = InProcessChannelBuilder.forName(channelName).build();
        if (StudioFlags.PROFILER_UNIFIED_PIPELINE.get()) {
          myDataStore.connect(Profiler.Stream.newBuilder()
                                             .setStreamId(myDataStore.getUniqueStreamId())
                                             .setType(Profiler.Stream.Type.DEVICE)
                                             .setDevice(ProfilerServiceProxy.profilerDeviceFromIDevice(myDevice))
                                             .build(),
                              proxyChannel);
        }
        else {
          myDataStore.connect(proxyChannel);
        }
      }
      catch (TimeoutException | AdbCommandRejectedException | IOException e) {
        // If some error happened after PerfdProxy was created, make sure to disconnect it
        if (myPerfdProxy != null) {
          myPerfdProxy.disconnect();
        }
        throw e;
      }
    }

    /**
     * A helper method to check whether the device has completed the boot sequence.
     * In emulator userdebug builds, the device can appear online before boot has finished, and pushing and running perfd on device at that
     * point would result in a failure. Therefore we poll a device property (dev.bootcomplete) at regular intervals to make sure the device
     * is ready for perfd. Whe problem only seems to manifest in emulators but not real devices. Here we check the property in both cases to
     * be sure, as this is only called once when the device comes online.
     */
    private boolean waitForBootComplete() throws InterruptedException {
      // This checks the flag for a minute before giving up.
      // TODO: move ProfilerServiceProxy to support user-triggered retries, in cases where 1m isn't enough for the emulator to boot.
      for (int i = 0; i < 60; i++) {
        String state = myDevice.getProperty(BOOT_COMPLETE_PROPERTY);
        if (BOOT_COMPLETE_MESSAGE.equals(state)) {
          return true;
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }

      return false;
    }
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

  private static class DeviceContext {
    @NotNull private final ExecutorService myExecutor = new ThreadPoolExecutor(0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    @Nullable private PerfdProxy myLastKnownPerfdProxy;
    @Nullable private Future<?> myLastKnowPerfdThreadfuture;
  }
}