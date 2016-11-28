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
package com.android.tools.idea.monitor.tool;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.*;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.LegacyAllocationConverter.CallStack;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.datastore.LegacyAllocationConverter;
import com.android.tools.datastore.LegacyAllocationTracker;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.profiler.proto.Profiler;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.HashSet;
import com.intellij.util.net.NetUtils;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
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

  private static final int DEVICE_PORT = 12389;
  private static final String DATASTORE_NAME = "DataStoreService";
  private final ProfilerClient myClient;
  private final DataStoreService myDataStoreService;
  private AndroidDebugBridge myBridge;

  public StudioProfilerDeviceManager(@NotNull Project project) throws IOException {
    final File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      throw new IllegalStateException("No adb found");
    }

    //TODO: Spawn the datastore in the right place (service)?
    myDataStoreService = new DataStoreService(DATASTORE_NAME);

    // The client is referenced in the update devices callback. As such the client needs to be set before we register
    // ourself as a listener for this callback. Otherwise we may get the callback before we are fully constructed
    myClient = new ProfilerClient(DATASTORE_NAME);

    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
    Futures.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        myBridge = bridge;
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
      }
    }, EdtExecutor.INSTANCE);

    AndroidDebugBridge.addClientChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addDebugBridgeChangeListener(this);
  }

  private static Logger getLogger() {
    return Logger.getInstance(StudioProfilerDeviceManager.class);
  }

  public void updateDevices() {
    if (myBridge != null) {
      Profiler.SetProcessesRequest.Builder builder = Profiler.SetProcessesRequest.newBuilder();
      for (IDevice device : myBridge.getDevices()) {
        if (device.isOnline()) {
          Profiler.Device profilerDevice = Profiler.Device.newBuilder()
            .setSerial(device.getSerialNumber())
            .setModel(device.getName())
            .build();
          Profiler.DeviceProcesses.Builder deviceProcesses = Profiler.DeviceProcesses.newBuilder();
          deviceProcesses.setDevice(profilerDevice);
          for (Client client : device.getClients()) {
            String description = client.getClientData().getClientDescription();
            deviceProcesses.addProcess(Profiler.Process.newBuilder()
                                         .setName(description == null ? "[UNKNOWN]" : description)
                                         .setPid(client.getClientData().getPid())
                                         .build());
          }
          builder.addDeviceProcesses(deviceProcesses.build());
          myDataStoreService.setLegacyAllocationTracker(new StudioLegacyAllocationTracker(device));
        }
      }
      myClient.getProfilerClient().setProcesses(builder.build());
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
    PerfdThread thread = new PerfdThread(device, myClient);
    thread.start();
  }

  private static class NullReceiver implements IShellOutputReceiver {

    @Override
    public void addOutput(byte[] data, int offset, int length) {
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }

  private static class PerfdThread extends Thread {
    private final IDevice myDevice;
    private final ProfilerClient myClient;
    private int myLocalPort;

    public PerfdThread(IDevice device, ProfilerClient client) {
      super("Perfd Thread: " + device.getSerialNumber());
      myDevice = device;
      myClient = client;
      myLocalPort = 0;
    }

    @Override
    public void run() {
      try {
        // TODO: Add support for non-development perfd locations.
        String dir = "../../out/studio/native/out/release";
        File perfd = null;
        for (String abi : myDevice.getAbis()) {
          File candidate = new File(PathManager.getHomePath(), dir + "/" + abi + "/perfd");
          if (candidate.exists()) {
            perfd = candidate;
          }
        }
        // TODO: Handle the case where we don't have perfd for this platform.
        assert perfd != null;
        // TODO: Add debug support for development
        String devicePath = "/data/local/tmp/perfd/";
        myDevice.executeShellCommand("mkdir -p " + devicePath, new NullReceiver());
        myDevice.pushFile(perfd.getAbsolutePath(), devicePath + "/perfd");
        myDevice.executeShellCommand("chmod +x " + devicePath + "perfd", new NullReceiver());
        myDevice.executeShellCommand(devicePath + "perfd", new IShellOutputReceiver() {
          @Override
          public void addOutput(byte[] data, int offset, int length) {
            String s = new String(data, Charsets.UTF_8);
            getLogger().info("[perfd]: " + s);
            if (s.contains("Server listening")) {
              try {
                myLocalPort = NetUtils.findAvailableSocketPort();
                myDevice.createForward(myLocalPort, DEVICE_PORT);
                myClient.getProfilerClient().connect(Profiler.ConnectRequest.newBuilder().setPort(myLocalPort).build());
              }
              catch (TimeoutException | AdbCommandRejectedException | IOException e) {
                throw new RuntimeException(e);
              }
            }
          }

          @Override
          public void flush() {
          }

          @Override
          public boolean isCancelled() {
            return false;
          }
        }, 0, null);
        if (myLocalPort > 0) {
          myClient.getProfilerClient().disconnect(Profiler.DisconnectRequest.newBuilder().setPort(myLocalPort).build());
          getLogger().info("Terminating perfd thread");
        }
      }
      catch (TimeoutException | AdbCommandRejectedException | SyncException | ShellCommandUnresponsiveException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class StudioLegacyAllocationTracker implements LegacyAllocationTracker {
    private final IDevice myDevice;

    public StudioLegacyAllocationTracker(@NotNull IDevice device) {
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
      // TODO fix allocation file overflow bug
      AllocationInfo[] rawInfos = AllocationsParser.parse(ByteBuffer.wrap(dumpData));
      LegacyAllocationConverter converter = new LegacyAllocationConverter();
      Map<List<StackTraceElement>, CallStack> fastUniqueStacks = new HashMap<>(); // because Sets don't have get()
      for (AllocationInfo info : rawInfos) {
        List<StackTraceElement> stackTraceElements = Arrays.asList(info.getStackTrace());
        CallStack callStack;
        if (!fastUniqueStacks.containsKey(stackTraceElements)) {
          callStack = converter.addCallStack(new CallStack(stackTraceElements));
          fastUniqueStacks.put(stackTraceElements, callStack);
        }
        else {
          callStack = fastUniqueStacks.get(stackTraceElements);
        }
        int classId = converter.addClassName(info.getAllocatedClass());
        converter.addAllocation(new LegacyAllocationConverter.Allocation(classId, info.getSize(), info.getThreadId(), callStack.getId()));
      }
      return converter;
    }

    @Nullable
    private Client getClient(@NotNull IDevice device, int processId) {
      if (myBridge == null) {
        return null;
      }

      return device.getClient(device.getClientName(processId));
    }
  }
}
