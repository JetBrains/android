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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.profiler.proto.Agent;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.google.common.base.Charsets;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.PerfdCrashInfo;
import com.google.wireless.android.sdk.stats.TransportDaemonStartedInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.net.NetUtils;
import com.android.tools.idea.io.grpc.ManagedChannel;
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder;
import com.android.tools.idea.io.grpc.netty.NettyChannelBuilder;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn Transport pipeline clients.
 * On device connection it will spawn the performance daemon on device, and will notify the pipeline system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
public final class TransportDeviceManager implements AndroidDebugBridge.IDebugBridgeChangeListener,
                                                     AndroidDebugBridge.IDeviceChangeListener, Disposable {
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

  /**
   * We rely on the concurrency guarantees of the {@link ConcurrentHashMap} to synchronize our {@link DeviceContext} accesses.
   * All accesses to the {@link DeviceContext} must be through its synchronization methods.
   */
  private final Map<String, DeviceContext> mySerialToDeviceContextMap = new ConcurrentHashMap<>();

  public TransportDeviceManager(@NotNull DataStoreService dataStoreService, @NotNull MessageBus messageBus,
                                @NotNull Disposable disposableParent) {
    Disposer.register(disposableParent, this);
    myDataStoreService = dataStoreService;
    myMessageBus = messageBus;
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
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
      disconnect(context, myDataStoreService);
      return context;
    });
  }

  /**
   * Disconnect both the proxy -> device and datastore -> proxy connections.
   *
   * This method doesn't clear myConnectedAgents and myPidToProcessMap because they should be preserved
   * when the daemon is killed while the device remains connected. These records will be used to
   * reconnect to the agents that are last known connected when the daemon restarts.
   *
   * When this method is called when the device is disconnected, there's no need to clean up these
   * records either because when the device is connected again, a new instance of DeviceContext
   * will be created.
   */
  @NotNull
  private static void disconnect(@NotNull DeviceContext context,
                                 @NotNull DataStoreService dataStoreService) {
    // Disconnect both the proxy -> device and datastore -> proxy connections.
    TransportProxy proxy = context.myLastKnownTransportProxy;
    if (proxy != null) {
      proxy.disconnect();
    }
    context.myLastKnownTransportProxy = null;

    if (context.myDevice != null) {
      dataStoreService.disconnect(context.myDevice.getDeviceId());
    }
    context.myDevice = null;
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
    @NotNull private final Map<String, DeviceContext> mySerialToDeviceContextMap;

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
        // Keep starting the daemon in case it's killed, as long as this thread is running (which should be the case
        // as long as the device is connected). The execution may exit this loop only via exceptions.
        long lastDaemonStartTime = System.currentTimeMillis();  // initialized as current time
        int attemptCounter = 0;
        for (boolean reconnectAgents = false; ; reconnectAgents = true) {
          long currentTimeMs = System.currentTimeMillis();
          reportTransportDaemonStarted(reconnectAgents, currentTimeMs - lastDaemonStartTime);
          // Start transport daemon and block until it is terminated or an exception is thrown.
          startTransportDaemon(transportDevice, reconnectAgents, attemptCounter);
          getLogger().info("Daemon stopped running; will try to restart it");
          // Disconnect the proxy and datastore before attempting to reconnect to agents.
          disconnect(mySerialToDeviceContextMap.get(myDevice.getSerialNumber()), myDataStore);
          lastDaemonStartTime = currentTimeMs;
          attemptCounter += 1;
          // wait some time between attempts
          Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }
      }
      catch (ShellCommandUnresponsiveException | SyncException e) {
        myMessageBus.syncPublisher(TOPIC).onTransportDaemonException(transportDevice, e);
        getLogger().error("Error when trying to spawn Transport daemon", e);
      }
      catch (AdbCommandRejectedException | IOException e) {
        // AdbCommandRejectedException and IOException happen when unplugging the device shortly after plugging it in.
        // We don't want to crash in this case.
        getLogger().warn("Error when trying to spawn Transport", e);
        myMessageBus.syncPublisher(TOPIC).onTransportDaemonException(transportDevice, e);
      }
      catch (TimeoutException | InterruptedException e) {
        // These happen when users unplug their devices or if studio is closed. We don't need to surface the exceptions here.
        myMessageBus.syncPublisher(TOPIC).onTransportDaemonException(transportDevice, e);
      }
      catch (FailedToStartServerException e) {
        getLogger().warn("Error when trying to spawn Transport", e);
        myMessageBus.syncPublisher(TOPIC).onStartTransportDaemonServerFail(transportDevice, e);
      }
      catch (RuntimeException e) {
        getLogger().warn("Error when trying to spawn Transport", e);
        myMessageBus.syncPublisher(TOPIC).onTransportDaemonException(transportDevice, e);
      }
    }

    /**
     * Executes shell command on device to start the Transport daemon
     *
     * @param transportDevice The device on which the daemon is going to be running.
     * @param reconnectAgents True if attempting to reconnect to the agents that are last known connected (assuming the device stays
     *                        connected).
     * @param attemptNumber the number of times the transport tried to start the daemon.
     * @throws TimeoutException
     * @throws AdbCommandRejectedException
     * @throws ShellCommandUnresponsiveException
     * @throws IOException
     */
    private void startTransportDaemon(@NotNull Common.Device transportDevice, boolean reconnectAgents, int attemptNumber)
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
      String command = TransportFileManager.getTransportExecutablePath() + " -config_file=" + TransportFileManager.getDaemonConfigPath();
      getLogger().info("[Transport]: Executing " + command);
      myDevice.executeShellCommand(command, new IShellOutputReceiver() {
        @Override
        public void addOutput(byte[] data, int offset, int length) {
          String startGrpcServerOutput = new String(data, offset, length, Charsets.UTF_8);
          if (startGrpcServerOutput.contains("Perfd Segmentation Fault:")) {
            reportTransportSegmentationFault(startGrpcServerOutput);
          }
          getLogger().info("[Transport]: " + startGrpcServerOutput);

          // We should only start the proxy once Transport has successfully launched the grpc server.
          // This is indicated by a "Server listening on ADDRESS" printout from Transport (ADDRESS can vary depending on pre-O vs JVMTI).
          // If starting the grpc server returns an output different from "Server listening on ADDRESS", we should not continue.
          // Known instances of when this happens are:
          //  1. we get linker warnings when starting Transport on pre-M devices
          //  2. we try to start the server, but another version of Studio already started it.
          //     (Note that it's ok to have multiple instances of the same version of Studio, as they are one single application.)
          if (!startGrpcServerOutput.startsWith("Server listening on")) {
            if (attemptNumber >= 3) {
              // By throwing an exception we stop the transport from keep trying to start the server forever.
              // An `onStartTransportDaemonServerFail` is published to the `TransportDeviceManager.TOPIC`.
              throw new FailedToStartServerException(startGrpcServerOutput);
            }
            else {
              // By returning the transport will try to connect again.
              return;
            }
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
            if (reconnectAgents) {
              reconnectAgents();
            }
            getLogger().info(String.format("TransportProxy successfully created for device: %s", myDevice));
          }
          catch (AdbCommandRejectedException | IOException | TimeoutException e) {
            myMessageBus.syncPublisher(TOPIC).onTransportProxyCreationFail(transportDevice, e);
            getLogger().error(String.format("TransportProxy failed for device: %s", myDevice), e);
          }
        }

        /**
         * Reconnect to the agents that were last known connected.
         */
        private void reconnectAgents() {
          TransportClient client = new TransportClient(TransportService.getChannelName());
          DeviceContext context = mySerialToDeviceContextMap.get(transportDevice.getSerial());
          assert context != null;
          for (Long pid : context.myConnectedAgents) {
            Commands.Command attachCommand = Commands.Command.newBuilder()
              .setStreamId(transportDevice.getDeviceId())
              .setPid(pid.intValue())
              .setType(Commands.Command.CommandType.ATTACH_AGENT)
              .setAttachAgent(
                Commands.AttachAgent.newBuilder()
                  .setAgentLibFileName(String.format("libjvmtiagent_%s.so", context.myPidToProcessMap.get(pid).getAbiCpuArch()))
                  .setAgentConfigPath(TransportFileManager.getAgentConfigFile())
                  .setPackageName(context.myPidToProcessMap.get(pid).getPackageName()))
              .build();
            // TODO(b/150503095)
            Transport.ExecuteResponse response =
                client.getTransportStub().execute(Transport.ExecuteRequest.newBuilder().setCommand(attachCommand).build());
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
      getLogger().info(String.format(Locale.US, "Port forwarding created for port: %d", localPort));

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
        .usePlaintext()
        .maxInboundMessageSize(MAX_MESSAGE_SIZE)
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
        context.myDevice = transportDevice;
        myTransportProxy.registerEventPreprocessor(new ConnectedAgentPreprossor(context));
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
      int maxSeconds = 60;
      for (int i = 0; i < maxSeconds; i++) {
        String state = myDevice.getProperty(BOOT_COMPLETE_PROPERTY);
        if (BOOT_COMPLETE_MESSAGE.equals(state)) {
          try {
            // In case the device is an AVD, also wait for the AvdData#getName to be ready
            myDevice.getAvdData().get(maxSeconds - i, TimeUnit.SECONDS);
          }
          catch (ExecutionException | java.util.concurrent.TimeoutException ignore) {
            // ignore
          }
          return true;
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }
      return false;
    }

    /**
     * Helper function that parses the segmentation fault string sent by transport. The parsed string is then sent to
     *
     * @param crashString the string passed from transport when a segmentation fault happens. This is expected to be
     *                    in the format of "Perfd Segmentation Fault: 1234,1234,1234,1234," where each number represents
     *                    an address in the callstack.
     */
    private void reportTransportSegmentationFault(String crashString) {
      PerfdCrashInfo.Builder crashInfo = PerfdCrashInfo.newBuilder();
      String[] stack = crashString.split("[:,]+");
      // The first value is the detection string.
      for (int i = 1; i < stack.length; i++) {
        // The string may be "\n" and will be empty after trimming.
        try {
          crashInfo.addBackstackAddressList(Long.parseLong(stack[i].trim()));
        }
        catch (NumberFormatException e) {
          // Ignore the string that's not a number.
        }
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

    private void reportTransportDaemonStarted(boolean isRestart, long millisecSinceLastStart) {
      TransportDaemonStartedInfo.Builder info = TransportDaemonStartedInfo.newBuilder().setIsRestart(isRestart);
      if (isRestart) {
        info.setMillisecSinceLastStart(millisecSinceLastStart);
      }
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
        .setAndroidProfilerEvent(AndroidProfilerEvent.newBuilder()
                                   .setType(AndroidProfilerEvent.Type.TRANSPORT_DAEMON_STARTED)
                                   .setTransportDaemonStartedInfo(info));
      UsageTracker.log(event);
    }
  }

  private static class DeviceContext {
    @NotNull public final ExecutorService myExecutor = new ThreadPoolExecutor(0, 1, 1L,
                                                                              TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    @Nullable public TransportProxy myLastKnownTransportProxy;
    @Nullable public Future<?> myLastKnownTransportThreadFuture;
    @Nullable public Common.Device myDevice;
    /**
     * The processes (PIDs) with a connected agent. Useful to recover the state of the pipeline's daemon
     * when it's killed unexpectedly, for example, by Live-LocK Daemon in Android OS.
     */
    @NotNull public final Set<Long> myConnectedAgents = new TreeSet<>();
    @NotNull public final Map<Long, Common.Process> myPidToProcessMap = new HashMap<>();
  }

  private static class ConnectedAgentPreprossor implements TransportEventPreprocessor {
    @NotNull private final DeviceContext myContext;

    public ConnectedAgentPreprossor(@NotNull DeviceContext context) {
      myContext = context;
    }

    @Override
    public boolean shouldPreprocess(Common.Event event) {
      switch (event.getKind()) {
        case AGENT:
        case PROCESS:
          return true;
        default:
          return false;
      }
    }

    @Override
    public Iterable<Common.Event> preprocessEvent(Common.Event event) {
      switch (event.getKind()) {
        case AGENT:
          if (event.getAgentData().getStatus().equals(Common.AgentData.Status.ATTACHED)) {
            long pid = event.getPid();
            myContext.myConnectedAgents.add(pid);
          }
          break;
        case PROCESS:
          long pid = event.getGroupId();
          if (event.getProcess().hasProcessStarted()) {
            myContext.myPidToProcessMap.put(pid, event.getProcess().getProcessStarted().getProcess());
          }
          else {
            // Note that the "end event" of remaining open groups generated by TransportServiceProxy.getEvents() when the
            // connection between the proxy and the device is lost do not go through event preprocessors. Therefore, those
            // "generated" PROCESS stopped event should not confuse |myConnectedAgents| here.
            myContext.myPidToProcessMap.remove(pid);
            myContext.myConnectedAgents.remove(pid);
          }
          break;
        default:
          break;
      }
      return Collections.emptyList();
    }
  }

  public interface TransportDeviceManagerListener {
    /**
     * Callback for when before the device manager starts the daemon on the specified device.
     */
    void onPreTransportDaemonStart(@NotNull Common.Device device);

    /**
     * Callback for when the transport daemon throws an exception.
     */
    void onTransportDaemonException(@NotNull Common.Device device, @NotNull Exception exception);

    /**
     * Callback for when the device manager fails to initialize the proxy layer that connects between the datastore and the daemon.
     */
    void onTransportProxyCreationFail(@NotNull Common.Device device, @NotNull Exception exception);

    /**
     * Callback for when the transport daemon fails to start the server.
     */
    void onStartTransportDaemonServerFail(@NotNull Common.Device device, @NotNull FailedToStartServerException exception);

    /**
     * void onTransportThreadStarts(@NotNull IDevice device, @NotNull Common.Device transportDevice);
     * <p>
     * /**
     * Allows for subscribers to customize the Transport pipeline's ServiceProxy before it is fully initialized.
     */
    void customizeProxyService(@NotNull TransportProxy proxy);

    /**
     * Allows for subscribers to customize the daemon config before it is being pushed to the device, which is then used to initialized
     * the transport daemon.
     *
     * @param configBuilder the DaemonConfig.Builder to customize. Note that it is up to the subscriber to not override fields that are set
     *                      in {@link TransportFileManager#pushDaemonConfig(AndroidRunConfigurationBase)} which are primarily used for
     *                      establishing connection to the transport daemon and app agent.
     */
    void customizeDaemonConfig(@NotNull Transport.DaemonConfig.Builder configBuilder);

    /**
     * Allows for subscribers to customize the agent config before it is being pushed to the device, which is then used to initialized
     * the transport app agent.
     *
     * @param configBuilder the AgentConifg.Builder to customize. Note that it is up to the subscriber to not override fields that are set
     *                      in {@link TransportFileManager#pushAgentConfig(AndroidRunConfigurationBase)} which are primarily used for
     *                      establishing connection to the transport daemon and app agent.
     * @param runConfig     the run config associated with the current app launch.
     */
    void customizeAgentConfig(@NotNull Agent.AgentConfig.Builder configBuilder, @Nullable AndroidRunConfigurationBase runConfig);
  }
}
