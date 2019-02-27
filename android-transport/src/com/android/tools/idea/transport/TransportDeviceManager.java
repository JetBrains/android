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

import static com.android.ddmlib.IDevice.CHANGE_STATE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Common;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.PerfdCrashInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.net.NetUtils;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import com.android.tools.analytics.UsageTracker;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn Transport pipeline clients.
 * On device connection it will spawn the performance daemon on device, and will notify the pipeline system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
public final class TransportDeviceManager implements AndroidDebugBridge.IDebugBridgeChangeListener,
                                                     AndroidDebugBridge.IDeviceChangeListener,
                                                     IdeSdks.IdeSdkChangeListener, Disposable {
  public static final Topic<TransportDeviceManagerListener> TOPIC = new Topic<>("TransportDevice", TransportDeviceManagerListener.class);

  private static Logger getLogger() {
    return Logger.getInstance(TransportDeviceManager.class);
  }

  private static final String BOOT_COMPLETE_PROPERTY = "dev.bootcomplete";
  private static final String BOOT_COMPLETE_MESSAGE = "1";

  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private static final int DEVICE_PORT = 12389;
  // On-device daemon uses Unix abstract socket for O and future devices.
  public static final String DEVICE_SOCKET_NAME = "AndroidStudioTransport";

  @NotNull private final DataStoreService myDataStoreService;
  @NotNull private final MessageBus myMessageBus;
  private boolean isAdbInitialized;

  /**
   * We rely on the concurrency guarantees of the {@link ConcurrentHashMap} to synchronize our {@link DeviceContext} accesses.
   * All accesses to the {@link DeviceContext} must be through its synchronization methods.
   */
  private final Map<String, DeviceContext> mySerialToDeviceContextMap = new ConcurrentHashMap<>();

  public TransportDeviceManager(@NotNull DataStoreService dataStoreService, @NotNull MessageBus messageBus) {
    myDataStoreService = dataStoreService;
    myMessageBus = messageBus;
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
        public void onSuccess(@Nullable AndroidDebugBridge result) {
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
      // Transport daemon must be spawned through ADB. When |bridge| is null, it means the ADB that was available earlier
      // becomes invalid and every running Transport it had spawned is being killed. As a result, we should kill the
      // corresponding proxies, too.
      disconnectProxies();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    mySerialToDeviceContextMap.computeIfAbsent(device.getSerialNumber(), serial -> new DeviceContext());

    if (device.isOnline()) {
      spawnTransportThread(device);
    }
  }

  /**
   * Whether the device is running O or higher APIs
   */
  private static boolean isAtLeastO(IDevice device) {
    return device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.O;
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    disconnectProxy(device);
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    if ((changeMask & CHANGE_STATE) != 0) {
      if (device.isOnline()) {
        spawnTransportThread(device);
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
      TransportProxy proxy = context.myLastKnownTransportProxy;
      if (proxy != null) {
        proxy.disconnect();
      }
      context.myLastKnownTransportProxy = null;
      return context;
    });
  }

  private void disconnectProxy(@NonNull IDevice device) {
    mySerialToDeviceContextMap.compute(device.getSerialNumber(), (serial, context) -> {
      assert context != null;
      if (context.myLastKnownTransportThreadFuture != null) {
        context.myLastKnownTransportThreadFuture.cancel(true);
        context.myLastKnownTransportThreadFuture = null;
      }
      context.myExecutor.execute(getDisconnectRunnable(serial));
      return context;
    });
  }

  private void disconnectProxies() {
    mySerialToDeviceContextMap.forEach((serial, context) -> {
      assert context != null;
      if (context.myLastKnownTransportThreadFuture != null) {
        context.myLastKnownTransportThreadFuture.cancel(true);
        context.myLastKnownTransportThreadFuture = null;
      }
      context.myExecutor.execute(getDisconnectRunnable(serial));
    });
  }

  private void spawnTransportThread(@NonNull IDevice device) {
    TransportThread transportThread = new TransportThread(device, myDataStoreService, myMessageBus, mySerialToDeviceContextMap);
    mySerialToDeviceContextMap.compute(device.getSerialNumber(), (serial, context) -> {
      assert context != null && (context.myLastKnownTransportProxy == null || context.myLastKnownTransportProxy.getDevice() != device);
      context.myLastKnownTransportThreadFuture = context.myExecutor.submit(transportThread);
      return context;
    });
  }

  private static final class TransportThread implements Runnable {
    @NotNull private final DataStoreService myDataStore;
    @NotNull private final IDevice myDevice;
    @NotNull private final MessageBus myMessageBus;
    private volatile TransportProxy myTransportProxy;
    private final Map<String, DeviceContext> mySerialToDeviceContextMap;

    private TransportThread(@NotNull IDevice device, @NotNull DataStoreService datastore, @NotNull MessageBus messageBus,
                            @NotNull Map<String, DeviceContext> serialToDeviceContextMap) {
      myDataStore = datastore;
      myMessageBus = messageBus;
      myDevice = device;
      mySerialToDeviceContextMap = serialToDeviceContextMap;
    }

    @Override
    public void run() {
      Common.Device transportDevice = Common.Device.getDefaultInstance();
      try {
        // Waits to make sure the device has completed boot sequence.
        if (!waitForBootComplete()) {
          throw new TimeoutException("Timed out waiting for device to be ready.");
        }

        transportDevice = TransportServiceProxy.transportDeviceFromIDevice(myDevice);
        myMessageBus.syncPublisher(TOPIC).onPreTransportDaemonStart(transportDevice);
        TransportFileManager fileManager = new TransportFileManager(myDevice, myMessageBus);
        fileManager.copyFilesToDevice();
        startTransportDaemon(transportDevice);
        getLogger().info("Terminating Transport thread");
      }
      catch (TimeoutException | ShellCommandUnresponsiveException | InterruptedException | SyncException e) {
        myMessageBus.syncPublisher(TOPIC).onStartTransportDaemonFail(transportDevice, e);
        throw new RuntimeException(e);
      }
      catch (AdbCommandRejectedException | IOException e) {
        // AdbCommandRejectedException and IOException happen when unplugging the device shortly after plugging it in.
        // We don't want to crash in this case.
        getLogger().warn("Error when trying to spawn Transport", e);
        myMessageBus.syncPublisher(TOPIC).onStartTransportDaemonFail(transportDevice, e);
      }
    }

    /**
     * Executes shell command on device to start the Transport daemon
     *
     * @throws TimeoutException
     * @throws AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException
     * @throws IOException
     */
    private void startTransportDaemon(@NotNull Common.Device transportDevice)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
      String command = TransportFileManager.getTransportExecutablePath() + " -config_file=" + TransportFileManager.getAgentConfigPath();
      getLogger().info("[Transport]: Executing " + command);
      myDevice.executeShellCommand(command, new IShellOutputReceiver() {
        @Override
        public void addOutput(byte[] data, int offset, int length) {
          String s = new String(data, offset, length, Charsets.UTF_8);
          if (s.contains("Perfd Segmentation Fault:")) {
            reportTransportSegmentationFault(s);
          }
          getLogger().info("[Transport]: " + s);

          // On supported API levels (Lollipop+), we should only start the proxy once Transport has successfully launched the grpc server.
          // This is indicated by a "Server listening on ADDRESS" printout from Transport (ADDRESS can vary depending on pre-O vs JVMTI).
          // The reason for this check is because we get linker warnings when starting Transport on pre-M devices (an issue which would not
          // be fixed by now), and we need to avoid starting the proxy in those cases.
          if (myDevice.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.LOLLIPOP &&
              !s.startsWith("Server listening on")) {
            return;
          }

          boolean[] alreadyExists = new boolean[]{false};
          mySerialToDeviceContextMap.compute(myDevice.getSerialNumber(), (serial, context) -> {
            assert context != null;
            if (context.myLastKnownTransportProxy != null) {
              getLogger().info(String.format("TransportProxy was already created for device: %s", myDevice));
              alreadyExists[0] = true;
            }
            return context;
          });
          if (alreadyExists[0]) {
            return;
          }

          try {
            createTransportProxy(transportDevice);
            getLogger().info(String.format("TransportProxy successfully created for device: %s", myDevice));
          }
          catch (AdbCommandRejectedException | IOException | TimeoutException e) {
            getLogger().warn(String.format("TransportProxy failed for device: %s", myDevice), e);
            myMessageBus.syncPublisher(TOPIC).onTransportProxyCreationFail(transportDevice, e);
          }
        }

        @Override
        public void flush() {
          // flush does not always get called. So we need to perform the proxy server/channel clean up after the Transport process has died.
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
    }

    /**
     * Creates TransportProxy for the device
     *
     * @throws TimeoutException
     * @throws AdbCommandRejectedException
     * @throws IOException
     */
    private void createTransportProxy(@NotNull Common.Device transportDevice)
      throws TimeoutException, AdbCommandRejectedException, IOException {
      int localPort = NetUtils.findAvailableSocketPort();
      if (localPort < 0) {
        throw new RuntimeException("Unable to find available socket port");
      }

      if (isAtLeastO(myDevice)) {
        myDevice.createForward(localPort, DEVICE_SOCKET_NAME, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      }
      else {
        myDevice.createForward(localPort, DEVICE_PORT);
      }
      getLogger().info(String.format("Port forwarding created for port: %d", localPort));

      /*
        Creates the channel that is used to connect to the device transport daemon.

        TODO: investigate why ant build fails to find the ManagedChannel-related classes
        The temporary fix is to stash the currently set context class loader,
        so ManagedChannelProvider can find an appropriate implementation.
       */
      ClassLoader stashedContextClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(NettyChannelBuilder.class.getClassLoader());
      ManagedChannel transportChannel = NettyChannelBuilder
        .forAddress("localhost", localPort)
        .usePlaintext(true)
        .maxMessageSize(MAX_MESSAGE_SIZE)
        .build();
      Thread.currentThread().setContextClassLoader(stashedContextClassLoader);

      // Creates a proxy server that the datastore connects to.
      String channelName = myDevice.getSerialNumber();
      myTransportProxy = new TransportProxy(myDevice, transportDevice, transportChannel);
      myMessageBus.syncPublisher(TOPIC).customizeProxyService(myTransportProxy);
      myTransportProxy.initializeProxyServer(channelName);
      try {
        myTransportProxy.connect();
      }
      catch (IOException exception) {
        myTransportProxy.disconnect();
        throw exception;
      }

      mySerialToDeviceContextMap.compute(myDevice.getSerialNumber(), (serial, context) -> {
        assert context != null;
        context.myLastKnownTransportProxy = myTransportProxy;
        return context;
      });

      // TODO using directexecutor for this channel freezes up grpc calls that are redirected to the device (e.g. GetTimes)
      // We should otherwise do it for performance reasons, so we should investigate why.
      ManagedChannel proxyChannel = InProcessChannelBuilder.forName(channelName).build();
      myDataStore.connect(Common.Stream.newBuilder()
                            .setStreamId(transportDevice.getDeviceId())
                            .setType(Common.Stream.Type.DEVICE)
                            .setDevice(transportDevice)
                            .build(),
                          proxyChannel);
    }

    /**
     * A helper method to check whether the device has completed the boot sequence.
     * In emulator userdebug builds, the device can appear online before boot has finished, and pushing and running Transport on device at
     * that point would result in a failure. Therefore we poll a device property (dev.bootcomplete) at regular intervals to make sure the
     * device is ready for Transport. Whe problem only seems to manifest in emulators but not real devices. Here we check the property in
     * both cases to be sure, as this is only called once when the device comes online.
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

    /**
     * Helper function that parses the segmentation fault string sent by transport. The parsed string is then sent to
     * @param crashString the string passed from transport when a segmentation fault happens. This is expected to be
     *                    in the format of "Perfd Segmentation Fault: 1234,1234,1234,1234," where each number represents
     *                    an address in the callstack.
     */
    private void reportTransportSegmentationFault(String crashString) {
      PerfdCrashInfo.Builder crashInfo = PerfdCrashInfo.newBuilder();
      String[] stack = crashString.split("[:,]+");
      // The first value is the detection string.
      for (int i = 1; i < stack.length; i++) {
        crashInfo.addBackstackAddressList(Long.parseLong(stack[i].trim()));
      }

      // Create metrics event to report callstack.
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
        .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(myDevice))
        .setAndroidProfilerEvent(AndroidProfilerEvent.newBuilder()
                                   .setType(AndroidProfilerEvent.Type.PERFD_CRASHED)
                                   .setPerfdCrashInfo(crashInfo));
      UsageTracker.log(event);
    }
  }

  private static class DeviceContext {
    @NotNull public final ExecutorService myExecutor = new ThreadPoolExecutor(0, 1, 1L,
                                                                              TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    @Nullable public TransportProxy myLastKnownTransportProxy;
    @Nullable public Future<?> myLastKnownTransportThreadFuture;
  }

  public interface TransportDeviceManagerListener {
    /**
     * Callback for when before the device manager starts the daemon on the specified device.
     */
    void onPreTransportDaemonStart(@NotNull Common.Device device);

    /**
     * Callback for when the transport daemon fails to start.
     */
    void onStartTransportDaemonFail(@NotNull Common.Device device, @NotNull Exception exception);

    /**
     * Callback for when the device manager fails to initialize the proxy layer that connects between the datastore and the daemon.
     */
    void onTransportProxyCreationFail(@NotNull Common.Device device, @NotNull Exception exception);

    /**
     * void onTransportThreadStarts(@NotNull IDevice device, @NotNull Common.Device transportDevice);
     * <p>
     * /**
     * Allows for subscribers to customize the Transport pipeline's ServiceProxy before it is fully initialized.
     */
    void customizeProxyService(@NotNull TransportProxy proxy);

    /**
     * Allows for subscribers to customize the agent config before it is being pushed to the device, which is then used to initialized
     * the transport daemon and app agent.
     *
     * @param configBuilder the AgentConifg.Builder to customize. Note that it is up to the subscriber to not override fields that are set
     *                      in {@link TransportFileManager#pushAgentConfig(AndroidRunConfigurationBase)} which are primarily used for
     *                      establishing connection to the transport daemon and app agent.
     * @param runConfig     the run config associated with the current app launch.
     */
    void customizeAgentConfig(@NotNull Agent.AgentConfig.Builder configBuilder, @Nullable AndroidRunConfigurationBase runConfig);
  }
}