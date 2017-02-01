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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.*;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LegacyAllocationConverter;
import com.android.tools.datastore.LegacyAllocationConverter.CallStack;
import com.android.tools.datastore.LegacyAllocationTracker;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.profilers.perfd.PerfdProxy;
import com.android.tools.profilers.ProfilerClient;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.net.NetUtils;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.android.ddmlib.Client.CHANGE_NAME;
import static com.android.ddmlib.IDevice.CHANGE_STATE;

/**
 * Manages the interactions between DDMLIB provided devices, and what is needed to spawn ProfilerClient's.
 * On device connection it will spawn the performance daemon on device, and will notify the profiler system that
 * a new device has been connected. *ALL* interaction with IDevice is encapsulated in this class.
 */
class StudioProfilerDeviceManager implements AndroidDebugBridge.IClientChangeListener,
                                             AndroidDebugBridge.IDeviceChangeListener,
                                             AndroidDebugBridge.IDebugBridgeChangeListener {

  private static Logger getLogger() {
    return Logger.getInstance(StudioProfilerDeviceManager.class);
  }

  private static final int MAX_MESSAGE_SIZE = 512 * 1024 * 1024 - 1;
  private static final int DEVICE_PORT = 12389;
  private static final String DATASTORE_NAME = "DataStoreService";
  private static final String PROXY_PERFD_NAME = "ProxyPerfdService";

  private final ProfilerClient myClient;
  private final DataStoreService myDataStoreService;
  private final StudioLegacyAllocationTracker myLegacyAllocationTracker;
  private AndroidDebugBridge myBridge;

  public StudioProfilerDeviceManager() throws IOException {
    //TODO: Spawn the datastore in the right place (service)?
    String directory = Paths.get(System.getProperty("user.home"), ".android").toString() + File.separator;

    myDataStoreService = new DataStoreService(DATASTORE_NAME,
                                              directory + DATASTORE_NAME,
                                              r -> ApplicationManager.getApplication().executeOnPooledThread(r));

    // The client is referenced in the update devices callback. As such the client needs to be set before we register
    // ourself as a listener for this callback. Otherwise we may get the callback before we are fully constructed
    myClient = new ProfilerClient(DATASTORE_NAME);

    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);

    myLegacyAllocationTracker = new StudioLegacyAllocationTracker();
    myDataStoreService.setLegacyAllocationTracker(myLegacyAllocationTracker);
  }

  public void dispose() {
    AndroidDebugBridge.removeClientChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
  }

  public void updateDevices() {
    if (myBridge != null) {
      for (IDevice device : myBridge.getDevices()) {
        if (device.isOnline()) {
          // TODO fix this - this currently does not work for multiple devices, plus this should go to the MemoryServiceProxy
          myLegacyAllocationTracker.setDevice(device);
        }
      }
    }
  }

  @Override
  public void clientChanged(@NonNull Client client, int changeMask) {
    if ((changeMask & CHANGE_NAME) != 0) {
      updateDevices();
    }
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    updateDevices();
    if (device.isOnline()) {
      spawnPerfd(device);
    }
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    updateDevices();
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    updateDevices();
    if ((changeMask & CHANGE_STATE) != 0 && device.isOnline()) {
      spawnPerfd(device);
    }
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    myBridge = bridge;
  }

  public ProfilerClient getClient() {
    return myClient;
  }

  private void spawnPerfd(@NonNull IDevice device) {
    PerfdThread thread = new PerfdThread(device, myDataStoreService);
    thread.start();
  }

  private static class PerfdThread extends Thread {
    private final DataStoreService myDataStore;
    private final IDevice myDevice;
    private int myLocalPort;
    private PerfdProxy myPerfdProxy;

    public PerfdThread(@NotNull IDevice device, @NotNull DataStoreService datastore) {
      super("Perfd Thread: " + device.getSerialNumber());
      myDataStore = datastore;
      myDevice = device;
      myLocalPort = 0;
    }

    @Override
    public void run() {
      try {
        File dir = new File(PathManager.getHomePath(), "plugins/android/resources/perfd");
        if (!dir.exists()) {
          // Development mode
          dir = new File(PathManager.getHomePath(), "../../out/studio/native/out/release");
        }

        File perfd = null;
        for (String abi : myDevice.getAbis()) {
          File candidate = new File(dir, abi + "/perfd");
          if (candidate.exists()) {
            perfd = candidate;
          }
        }

        ManagedChannel proxyChannel = null;

        // TODO: Handle the case where we don't have perfd for this platform.
        assert perfd != null;
        // TODO: Add debug support for development
        String devicePath = "/data/local/tmp/perfd/";
        myDevice.executeShellCommand("mkdir -p " + devicePath, new NullOutputReceiver());
        myDevice.pushFile(perfd.getAbsolutePath(), devicePath + "/perfd");
        myDevice.executeShellCommand("chmod +x " + devicePath + "perfd", new NullOutputReceiver());
        myDevice.executeShellCommand(devicePath + "perfd", new IShellOutputReceiver() {
          @Override
          public void addOutput(byte[] data, int offset, int length) {
            String s = new String(data, Charsets.UTF_8);
            getLogger().info("[perfd]: " + s);
            if (s.contains("Server listening")) {
              try {
                myLocalPort = NetUtils.findAvailableSocketPort();
                myDevice.createForward(myLocalPort, DEVICE_PORT);
                if (myLocalPort < 0) {
                  return;
                }

                // Creates the channel that is used to connect to the device perfd.
                ManagedChannel perfdChannel = NettyChannelBuilder
                  .forAddress("localhost", myLocalPort)
                  .usePlaintext(true)
                  .maxMessageSize(MAX_MESSAGE_SIZE)
                  .build();

                // Creates a proxy server that the datastore connects to.
                myPerfdProxy = new PerfdProxy(myDevice, perfdChannel, PROXY_PERFD_NAME);
                myPerfdProxy.connect();
                // TODO using directexecutor for this channel freezes up grpc calls that are redirected to the device (e.g. GetTimes)
                // We should otherwise do it for performance reasons, so we should investigate why.
                ManagedChannel proxyChannel = InProcessChannelBuilder.forName(PROXY_PERFD_NAME).build();
                myDataStore.connect(proxyChannel);
              }
              catch (TimeoutException | AdbCommandRejectedException | IOException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public void flush() {
            if (myPerfdProxy != null) {
              myPerfdProxy.disconnect();
              myPerfdProxy = null;
            }
          }

          @Override
          public boolean isCancelled() {
            return false;
          }
        }, 0, null);

        if (proxyChannel != null) {
          myDataStore.disconnect(proxyChannel);
        }

        getLogger().info("Terminating perfd thread");
      }
      catch (TimeoutException | AdbCommandRejectedException | SyncException | ShellCommandUnresponsiveException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class StudioLegacyAllocationTracker implements LegacyAllocationTracker {
    private IDevice myDevice;
    private final LegacyAllocationConverter myConverter = new LegacyAllocationConverter();

    public void setDevice(IDevice device) {
      myDevice = device;
    }

    @Override
    public boolean setAllocationTrackingEnabled(int processId, boolean enabled) {
      Client client = getClient(myDevice, processId);
      if (client == null) {
        return false;
      }
      client.enableAllocationTracker(enabled);
      return true;
    }

    @Override
    public void getAllocationTrackingDump(int processId, @NotNull ExecutorService executorService, @NotNull Consumer<byte[]> consumer) {
      Client targetClient = getClient(myDevice, processId);
      if (targetClient == null) {
        return;
      }
      AndroidDebugBridge.addClientChangeListener(new AndroidDebugBridge.IClientChangeListener() {
        @Override
        public void clientChanged(@NonNull Client client, int changeMask) {
          if (targetClient == client && (changeMask & Client.CHANGE_HEAP_ALLOCATIONS) != 0) {
            final byte[] data = client.getClientData().getAllocationsData();
            executorService.submit(() -> consumer.consume(data));
            AndroidDebugBridge.removeClientChangeListener(this);
          }
        }
      });
      targetClient.requestAllocationDetails();
    }

    @NotNull
    @Override
    public LegacyAllocationConverter parseDump(@NotNull byte[] dumpData) {
      myConverter.prepare();

      // TODO fix allocation file overflow bug
      AllocationInfo[] rawInfos = AllocationsParser.parse(ByteBuffer.wrap(dumpData));

      for (AllocationInfo info : rawInfos) {
        List<StackTraceElement> stackTraceElements = Arrays.asList(info.getStackTrace());
        CallStack callStack = myConverter.addCallStack(stackTraceElements);
        int classId = myConverter.addClassName(info.getAllocatedClass());
        myConverter.addAllocation(new LegacyAllocationConverter.Allocation(classId, info.getSize(), info.getThreadId(), callStack.getId()));
      }
      return myConverter;
    }

    @Nullable
    private Client getClient(@NotNull IDevice device, int processId) {
      return device.getClient(device.getClientName(processId));
    }
  }
}
