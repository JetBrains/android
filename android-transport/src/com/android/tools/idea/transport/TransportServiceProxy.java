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
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Commands.Command.CommandType;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.ProcessData;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.BytesResponse;
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
import gnu.trove.TLongObjectHashMap;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  private final TransportServiceGrpc.TransportServiceBlockingStub myServiceStub;
  @NotNull private final IDevice myDevice;
  @NotNull private final Common.Device myProfilerDevice;
  private final Map<Client, Common.Process> myCachedProcesses = Collections.synchronizedMap(new HashMap<>());
  private final boolean myIsDeviceApiSupported;
  private final BlockingDeque<Common.Event> myEventQueue;
  private Thread myEventsListenerThread;
  private final Map<CommandType, TransportProxy.ProxyCommandHandler> myCommandHandlers = new HashMap<>();
  private final List<TransportEventPreprocessor> myEventPreprocessors = new ArrayList<>();
  private final List<TransportBytesPreprocessor> myDataPreprocessors = new ArrayList<>();
  @NotNull private final Map<String, ByteString> myProxyBytesCache;

  // Cache the latest event timestamp we received from the daemon, which is used for closing all still-opened event groups when
  // the proxy lost connection with the device.
  private long myLatestEventTimestampNs = Long.MIN_VALUE;
  @Nullable private CountDownLatch myEventStreamingLatch = null;

  /**
   * @param ddmlibDevice    the {@link IDevice} for retrieving process informatino.
   * @param transportDevice the {@link Common.Device} corresponding to the ddmlibDevice,
   *                        as generated via {@link #transportDeviceFromIDevice(IDevice)}
   * @param channel         the channel that is used for communicating with the device daemon.
   * @param proxyEventQueue event queue shared by the proxy layer.
   * @param proxyBytesCache byte cache shared by the proxy layer.
   */
  public TransportServiceProxy(@NotNull IDevice ddmlibDevice, @NotNull Common.Device transportDevice, @NotNull ManagedChannel channel,
                               @NotNull BlockingDeque<Common.Event> proxyEventQueue,
                               @NotNull Map<String, ByteString> proxyBytesCache) {
    super(TransportServiceGrpc.getServiceDescriptor());
    myDevice = ddmlibDevice;
    myProfilerDevice = transportDevice;
    // Unsupported device are expected to have the unsupportedReason field set.
    myIsDeviceApiSupported = myProfilerDevice.getUnsupportedReason().isEmpty();
    myServiceStub = TransportServiceGrpc.newBlockingStub(channel);
    myEventQueue = proxyEventQueue;
    myProxyBytesCache = proxyBytesCache;
    getLog().info(String.format("ProfilerDevice created: %s", myProfilerDevice));

    updateProcesses();

    AndroidDebugBridge.addDeviceChangeListener(this);
    AndroidDebugBridge.addClientChangeListener(this);
  }

  public void registerCommandHandler(CommandType commandType, TransportProxy.ProxyCommandHandler handler) {
    myCommandHandlers.put(commandType, handler);
  }

  /**
   * Registers an event preprocessor that preprocesses events in {@link #getEvents(GetEventsRequest, StreamObserver)}.
   */
  public void registerEventPreprocessor(TransportEventPreprocessor eventPreprocessor) {
    myEventPreprocessors.add(eventPreprocessor);
  }

  /**
   * Registers an event preprocessor that preprocesses events in {@link #getEvents(GetEventsRequest, StreamObserver)}.
   */
  public void registerDataPreprocessor(TransportBytesPreprocessor dataPreprocessor) {
    myDataPreprocessors.add(dataPreprocessor);
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
      .setUnsupportedReason(getDeviceUnsupportedReason(device))
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
  private static String getDeviceUnsupportedReason(@NotNull IDevice device) {
    String unsupportedReason = "";
    if (device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.LOLLIPOP) {
      unsupportedReason = PRE_LOLLIPOP_FAILURE_REASON;
    }
    return unsupportedReason;
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
    if (myEventStreamingLatch != null) {
      try {
        myEventStreamingLatch.await();
      }
      catch (InterruptedException ignored) {
      }
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

      if (myEventsListenerThread != null) {
        myEventsListenerThread.interrupt();
        myEventsListenerThread = null;
      }
    }).start();

    // This loop runs on a GRPC thread, it should not exit until the grpc is terminated killing the thread.
    myEventStreamingLatch = new CountDownLatch(1);
    myEventsListenerThread = new Thread(() -> {
      Map<Event.Kind, TLongObjectHashMap> ongoingEventGroups = new HashMap<>();
      // The loop keeps running if the queue is not emptied, to make sure we pipe through all the existing
      // events that are already in the queue.
      while (!Thread.currentThread().isInterrupted() || !myEventQueue.isEmpty()) {
        try {
          Event event = myEventQueue.take();
          myLatestEventTimestampNs = Math.max(myLatestEventTimestampNs, event.getTimestamp());

          // Run registered preprocessors.
          for (TransportEventPreprocessor preprocessor : myEventPreprocessors) {
            if (preprocessor.shouldPreprocess(event)) {
              preprocessor.preprocessEvent(event).forEach(generatedEvent -> responseObserver.onNext(generatedEvent));
            }
          }

          // Update the event cache: remove an event group if it has ended, otherwise cache the latest opened event for that group.
          if (event.getIsEnded()) {
            ongoingEventGroups.computeIfPresent(event.getKind(), (kind, map) -> {
              map.remove(event.getGroupId());
              return map.isEmpty() ? null : map;
            });
          }
          else if (event.getGroupId() != 0) {
            ongoingEventGroups.compute(event.getKind(), (kind, map) -> {
              map = Optional.ofNullable(map).orElseGet(TLongObjectHashMap::new);
              map.put(event.getGroupId(), event);
              return map;
            });
          }
          responseObserver.onNext(event);
        }
        catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
      }

      // Create a generic end event with the input kind and group id.
      // Note - We will revisit this logic if it turns out we need to insert domain-specific data with the end event.
      // For the most part, since the device stream is disconnected, we should not have to care.
      for (Event.Kind kind : ongoingEventGroups.keySet()) {
        ongoingEventGroups.get(kind).forEachValue(lastEvent -> {
          responseObserver.onNext(generateEndEvent((Event)lastEvent));
          return true;
        });
      }

      responseObserver.onCompleted();
      myEventStreamingLatch.countDown();
    });
    myEventsListenerThread.start();
  }

  public void getBytes(@NotNull BytesRequest request, StreamObserver<BytesResponse> responseObserver) {
    BytesResponse.Builder response;
    synchronized (myProxyBytesCache) {
      if (myProxyBytesCache.containsKey(request.getId())) {
        response = BytesResponse.newBuilder().setContents(myProxyBytesCache.get(request.getId()));
        // Removes cache to save memory once it has been requested/cached by the datastore.
        myProxyBytesCache.remove(request.getId());
      }
      else {
        response = myServiceStub.getBytes(request).toBuilder();
      }
      // Run registered preprocessors.
      for (TransportBytesPreprocessor preprocessor : myDataPreprocessors) {
        if (preprocessor.shouldPreprocess(request)) {
          response.setContents(preprocessor.preprocessBytes(request.getId(), response.getContents()));
        }
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    }
  }

  @NotNull
  private Common.Event generateEndEvent(@NotNull Common.Event previousEvent) {
    return Event.newBuilder()
      .setKind(previousEvent.getKind())
      .setGroupId(previousEvent.getGroupId())
      .setPid(previousEvent.getPid())
      .setTimestamp(myLatestEventTimestampNs + 1)
      .setIsEnded(true)
      .build();
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
    if (myCommandHandlers.containsKey(command.getType()) && myCommandHandlers.get(command.getType()).shouldHandle(command)) {
      response = myCommandHandlers.get(command.getType()).execute(command);
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
    overrides.put(TransportServiceGrpc.METHOD_GET_BYTES,
                  ServerCalls.asyncUnaryCall((request, observer) -> {
                    getBytes((BytesRequest)request, (StreamObserver)observer);
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
      if (process != null) {
        myEventQueue.offer(Common.Event.newBuilder()
                             .setGroupId(process.getPid())
                             .setKind(Event.Kind.PROCESS)
                             .setIsEnded(true)
                             .setTimestamp(timestampNs)
                             .build());
      }
    }
  }

  @TestOnly
  @NotNull
  Map<Client, Common.Process> getCachedProcesses() {
    return myCachedProcesses;
  }
}
