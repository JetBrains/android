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
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.profilers.perfd.PerfdProxy;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.profiler.proto.Profiler;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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

  private static final String BOOT_COMPLETE_PROPERTY = "dev.bootcomplete";
  private static final String BOOT_COMPLETE_MESSAGE = "1";

  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private static final int DEVICE_PORT = 12389;
  // On-device daemon uses Unix abstract socket for O and future devices.
  private static final String DEVICE_SOCKET_NAME = "AndroidStudioProfiler";

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
        public void onSuccess(@NotNull AndroidDebugBridge result) {
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
        ProfilerDeviceFileManager deviceFileManager = new ProfilerDeviceFileManager(myDevice);
        deviceFileManager.copyProfilerFilesToDevice();
        String command = ProfilerDeviceFileManager.getPerfdPath() + " -config_file=" + ProfilerDeviceFileManager.getAgentConfigPath();
        myDevice.executeShellCommand(command, new IShellOutputReceiver() {
          @Override
          public void addOutput(byte[] data, int offset, int length) {
            String s = new String(data, offset, length, Charsets.UTF_8);
            getLogger().info("[perfd]: " + s);

            // On supported API levels (Lollipop+), we should only start the proxy once perfd has successfully launched the grpc server.
            // This is indicated by a "Server listening on ADDRESS" printout from perfd (ADDRESS can vary depending on pre-O vs JVMTI).
            // The reason for this check is because we get linker warnings when starting perfd on pre-M devices (an issue which would not
            // be fixed by now), and we need to avoid starting the proxy in those cases.
            if (myDevice.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.LOLLIPOP &&
                !s.startsWith("Server listening on")) {
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

  private static class DeviceContext {
    @NotNull private final ExecutorService myExecutor = new ThreadPoolExecutor(0, 1, 1L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    @Nullable private PerfdProxy myLastKnownPerfdProxy;
    @Nullable private Future<?> myLastKnowPerfdThreadfuture;
  }
}