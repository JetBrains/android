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

import static com.android.ddmlib.Client.CHANGE_NAME;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Commands.Command.CommandType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.ProcessData;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.ExecuteResponse;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * A proxy TransportService on host that intercepts grpc requests from transport-database to device perfd.
 * This enables us to support legacy workflows based on device's API levels.
 */
public class TransportServiceProxy extends ServiceProxy
  implements AndroidDebugBridge.IClientChangeListener, AndroidDebugBridge.IDeviceChangeListener {

  private static Logger getLog() {
    return Logger.getInstance(TransportServiceProxy.class);
  }

  private static final String EMULATOR = "Emulator";
  static final String PRE_LOLLIPOP_FAILURE_REASON = "Pre-Lollipop devices are not supported.";
  static final String Q_FAILURE_REASON = "Q devices are not yet supported";

  private final TransportServiceGrpc.TransportServiceBlockingStub myServiceStub;
  @NotNull private final IDevice myDevice;
  @NotNull private final Common.Device myProfilerDevice;
  private final Map<Client, Common.Process> myCachedProcesses = Collections.synchronizedMap(new HashMap<>());
  private final boolean myIsDeviceApiSupported;
  private final LinkedBlockingDeque<Common.Event> myEventQueue = new LinkedBlockingDeque<>();
  private Thread myEventsListenerThread;
  private final Map<CommandType, Function<Command, ExecuteResponse>> myCommandHandlers = new HashMap<>();

  /**
   * @param ddmlibDevice    the {@link IDevice} for retrieving process informatino.
   * @param transportDevice the {@link Common.Device} corresponding to the ddmlibDevice,
   *                        as generated via {@link #transportDeviceFromIDevice(IDevice)}
   * @param channel         the channel that is used for communicating with the device daemon.
   */
  public TransportServiceProxy(@NotNull IDevice ddmlibDevice, @NotNull Common.Device transportDevice, @NotNull ManagedChannel channel) {
    super(TransportServiceGrpc.getServiceDescriptor());
    myDevice = ddmlibDevice;
    myProfilerDevice = transportDevice;
    // Unsupported device are expected to have the unsupportedReason field set.
    myIsDeviceApiSupported = myProfilerDevice.getUnsupportedReason().isEmpty();
    myServiceStub = TransportServiceGrpc.newBlockingStub(channel);
    getLog().info(String.format("ProfilerDevice created: %s", myProfilerDevice));

    updateProcesses();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
  }

  public void registerCommandHandler(CommandType commandType, Function<Command, ExecuteResponse> handler) {
    myCommandHandlers.put(commandType, handler);
  }

  /**
   * Converts an {@link IDevice} object into a {@link Common.Device}.
   *
   * @param device the IDevice to retrieve information from.
   * @return
   */
  @NotNull
  public static Common.Device transportDeviceFromIDevice(@NotNull IDevice device) {
    StringBuilder bootIdBuilder = new StringBuilder();
    try {
      device.executeShellCommand("cat /proc/sys/kernel/random/boot_id", new MultiLineReceiver() {
        @Override
        public void processNewLines(@NonNull String[] lines) {
          // There should only be one-line here.
          assert (lines.length == 1);
          bootIdBuilder.append(lines[0]);
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });
    }
    catch (TimeoutException | AdbCommandRejectedException | IOException | ShellCommandUnresponsiveException e) {
      getLog().warn(String.format("Unable to retrieve boot_id from device %s", device), e);
    }

    String bootId = bootIdBuilder.toString();
    if (bootId.isEmpty()) {
      bootId = String.valueOf(device.getSerialNumber().hashCode());
    }

    long device_id;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(bootId.getBytes());
      digest.update(device.getSerialNumber().getBytes());
      device_id = ByteBuffer.wrap(digest.digest()).getLong();
    }
    catch (NoSuchAlgorithmException e) {
      getLog().info("SHA-256 is not available", e);
      // Randomly generate an id if we cannot SHA.
      device_id = new Random(System.currentTimeMillis()).nextLong();
    }

    String unsupportedReason = "";
    if (device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.LOLLIPOP) {
      unsupportedReason = PRE_LOLLIPOP_FAILURE_REASON;
    }
    else if (device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.Q) {
      // TODO b/127838161 remove after daemon no longer freezes on Q.
      unsupportedReason = Q_FAILURE_REASON;
    }

    return Common.Device.newBuilder()
      .setDeviceId(device_id)
      .setSerial(device.getSerialNumber())
      .setModel(getDeviceModel(device))
      .setVersion(StringUtil.notNullize(device.getProperty(IDevice.PROP_BUILD_VERSION)))
      .setCodename(StringUtil.notNullize(device.getVersion().getCodename()))
      .setApiLevel(device.getVersion().getApiLevel())
      .setFeatureLevel(device.getVersion().getFeatureLevel())
      .setManufacturer(getDeviceManufacturer(device))
      .setIsEmulator(device.isEmulator())
      .setState(convertState(device.getState()))
      .setUnsupportedReason(unsupportedReason)
      .build();
  }

  private static Common.Device.State convertState(@NotNull IDevice.DeviceState state) {
    switch (state) {
      case OFFLINE:
        return Common.Device.State.OFFLINE;

      case ONLINE:
        return Common.Device.State.ONLINE;

      case DISCONNECTED:
        return Common.Device.State.DISCONNECTED;

      default:
        return Common.Device.State.UNSPECIFIED;
    }
  }

  @NotNull
  public static String getDeviceModel(@NotNull IDevice device) {
    return device.isEmulator() ? StringUtil.notNullize(device.getAvdName(), "Unknown") : DevicePropertyUtil.getModel(device, "Unknown");
  }

  @NotNull
  public static String getDeviceManufacturer(@NotNull IDevice device) {
    return DevicePropertyUtil.getManufacturer(device, device.isEmulator() ? EMULATOR : "");
  }

  @Override
  public void disconnect() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
    AndroidDebugBridge.removeClientChangeListener(this);
    if (myEventsListenerThread != null) {
      myEventsListenerThread.interrupt();
      myEventsListenerThread = null;
    }
  }

  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> responseObserver) {
    GetDevicesResponse response = GetDevicesResponse.newBuilder().addDevice(myProfilerDevice).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void getEvents(GetEventsRequest request, StreamObserver<Event> responseObserver) {
    // Create a thread to receive the stream of events from perfd.
    // We push all events into an event queue, so any proxy generated events can also be added.
    new Thread(() -> {
      Iterator<Event> response = myServiceStub.getEvents(request);
      try {
        while (response.hasNext()) {
          // Blocking call to device. If the device is disconnected this call returns null.
          Event event = response.next();
          if (event != null) {
            myEventQueue.offer(event);
          }
        }
      }
      catch (StatusRuntimeException ignored) {
        // disconnect handle generally outside of the exception.
      }
      // Reaching here means that the device side getEvents stream has terminated. We need to clean up any live processes.
      removeProcesses(ImmutableSet.copyOf(myCachedProcesses.keySet()), Long.MAX_VALUE);
      if (myEventsListenerThread != null) {
        myEventsListenerThread.interrupt();
        myEventsListenerThread = null;
      }
    }).start();

    // This loop runs on a GRPC thread, it should not exit until the grpc is terminated killing the thread.
    myEventsListenerThread = new Thread(() -> {
      // The loop keeps running if the queue is not emptied, to make sure we pipe through all the existing
      // events that are already in the queue.
      while (!Thread.currentThread().isInterrupted() || !myEventQueue.isEmpty()) {
        try {
          Event event = myEventQueue.take();
          if (event != null) {
            responseObserver.onNext(event);
          }
        }
        catch (InterruptedException ignored) {
        }
      }
      responseObserver.onCompleted();
    });
    myEventsListenerThread.start();
  }

  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
    TimeResponse response;
    if (myIsDeviceApiSupported) {
      // if device API is supported, use grpc to get the current time
      try {
        response = myServiceStub.getCurrentTime(request);
      }
      catch (StatusRuntimeException e) {
        responseObserver.onError(e);
        return;
      }
    }
    else {
      // otherwise, return a default (any) instance of TimeResponse
      response = TimeResponse.getDefaultInstance();
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> responseObserver) {
    GetProcessesResponse response = GetProcessesResponse.newBuilder().addAllProcess(myCachedProcesses.values()).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
    Command command = request.getCommand();
    ExecuteResponse response;
    if (myCommandHandlers.containsKey(command.getType())) {
      response = myCommandHandlers.get(command.getType()).apply(command);
    }
    else {
      response = myServiceStub.execute(request);
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    // Don't care
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    // This event can be triggered when a device goes offline. However, updateProcesses expects
    // an online device, so just ignore the event in that case.
    if (device == myDevice && (changeMask & IDevice.CHANGE_CLIENT_LIST) != 0) {
      updateProcesses();
    }
  }

  @Override
  public void clientChanged(@NonNull Client client, int changeMask) {
    if ((changeMask & CHANGE_NAME) != 0 && client.getDevice() == myDevice && client.getClientData().getClientDescription() != null) {
      updateProcesses(Collections.singletonList(client), Collections.EMPTY_LIST);
    }
  }

  @Override
  public ServerServiceDefinition getServiceDefinition() {
    Map<MethodDescriptor, ServerCallHandler> overrides = Maps.newHashMap();
    overrides.put(TransportServiceGrpc.METHOD_GET_DEVICES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getDevices((GetDevicesRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(TransportServiceGrpc.METHOD_GET_PROCESSES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getProcesses((GetProcessesRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(TransportServiceGrpc.METHOD_GET_CURRENT_TIME,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getCurrentTime((TimeRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(TransportServiceGrpc.METHOD_GET_EVENTS,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getEvents((GetEventsRequest)request, (StreamObserver)observer);
                  }));
    overrides.put(TransportServiceGrpc.METHOD_EXECUTE,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    execute((ExecuteRequest)request, (StreamObserver)observer);
                  }));
    return generatePassThroughDefinitions(overrides, myServiceStub);
  }

  /**
   * Note: This method is called from the ddmlib thread.
   */
  private void updateProcesses() {
    if (!myIsDeviceApiSupported) {
      return; // Device not supported. Do nothing.
    }

    // Retrieve the list of Clients, but skip any that doesn't have a proper name yet.
    Set<Client> updatedClients =
      Arrays.stream(myDevice.getClients()).filter(c -> c.getClientData().getClientDescription() != null).collect(Collectors.toSet());
    Set<Client> existingClients = myCachedProcesses.keySet();
    ImmutableSet<Client> removedClients = Sets.difference(existingClients, updatedClients).immutableCopy();
    ImmutableSet<Client> addedClients = Sets.difference(updatedClients, existingClients).immutableCopy();

    // This is needed as:
    // 1) the service test calls this function without setting up a full profiler service, and
    // 2) this is potentially being called as the device is being shut down.
    updateProcesses(addedClients, removedClients);
  }

  /**
   * Note: This method is called from the ddmlib thread.
   */
  private void updateProcesses(@NotNull Collection<Client> addedClients, @NotNull Collection<Client> removedClients) {
    if (!myIsDeviceApiSupported || !myDevice.isOnline()) {
      return; // Device not supported or not online. Do nothing.
    }

    TimeResponse times;
    try {
      times = myServiceStub.getCurrentTime(TimeRequest.newBuilder().setStreamId(myProfilerDevice.getDeviceId()).build());
    }
    catch (Exception e) {
      // Most likely the destination server went down, and we're in shut down/disconnect mode.
      getLog().info(e);
      return;
    }

    for (Client client : addedClients) {
      String description = client.getClientData().getClientDescription();
      if (description == null) {
        continue; // Process is still starting up and not ready yet
      }

      // Parse cpu arch from client abi info, for example, "arm64" from "64-bit (arm64)". Abi string indicates whether application is
      // 64-bit or 32-bit and its cpu arch. Old devices of 32-bit do not have the application data, fall back to device's abi cpu arch.
      // TODO: Remove when moving process discovery.
      String abi = client.getClientData().getAbi();
      String abiCpuArch;
      if (abi != null && abi.contains(")")) {
        abiCpuArch = abi.substring(abi.indexOf('(') + 1, abi.indexOf(')'));
      }
      else {
        abiCpuArch = Abi.getEnum(myDevice.getAbis().get(0)).getCpuArch();
      }

      // TODO: Set this to the applications actual start time.
      Common.Process process = Common.Process.newBuilder()
        .setName(client.getClientData().getClientDescription())
        .setPid(client.getClientData().getPid())
        .setDeviceId(myProfilerDevice.getDeviceId())
        .setState(Common.Process.State.ALIVE)
        .setStartTimestampNs(times.getTimestampNs())
        .setAbiCpuArch(abiCpuArch)
        .build();
      myCachedProcesses.put(client, process);
      // New pipeline event - create a ProcessStarted event for each process.
      myEventQueue.offer(Event.newBuilder()
                           .setGroupId(process.getPid())
                           .setKind(Event.Kind.PROCESS)
                           .setProcess(ProcessData.newBuilder()
                                         .setProcessStarted(ProcessData.ProcessStarted.newBuilder()
                                                              .setProcess(process)))
                           .setTimestamp(times.getTimestampNs())
                           .build());
    }

    removeProcesses(removedClients, times.getTimestampNs());
  }

  private void removeProcesses(@NotNull Collection<Client> removedClients, long timestampNs) {
    for (Client client : removedClients) {
      Common.Process process = myCachedProcesses.remove(client);
      // New data pipeline event.
      myEventQueue.offer(Common.Event.newBuilder()
                           .setGroupId(process.getPid())
                           .setKind(Event.Kind.PROCESS)
                           .setIsEnded(true)
                           .setTimestamp(timestampNs)
                           .build());
    }
  }

  @TestOnly
  @NotNull
  Map<Client, Common.Process> getCachedProcesses() {
    return myCachedProcesses;
  }
}
