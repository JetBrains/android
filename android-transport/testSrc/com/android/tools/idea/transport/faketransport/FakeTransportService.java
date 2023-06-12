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
package com.android.tools.idea.transport.faketransport;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.GuardedBy;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.EventStreamServer;
import com.android.tools.idea.transport.faketransport.commands.BeginSession;
import com.android.tools.idea.transport.faketransport.commands.CommandHandler;
import com.android.tools.idea.transport.faketransport.commands.DiscoverProfileable;
import com.android.tools.idea.transport.faketransport.commands.EndSession;
import com.android.tools.idea.transport.faketransport.commands.GetCpuCoreConfig;
import com.android.tools.idea.transport.faketransport.commands.HeapDump;
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocSampling;
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking;
import com.android.tools.idea.transport.faketransport.commands.StartTrace;
import com.android.tools.idea.transport.faketransport.commands.StopTrace;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.intellij.util.containers.MultiMap;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * This class is thread-safe, allowing {@link CommandHandler} to publish new events on one thread (usually test) and
 * {@link com.android.tools.idea.transport.poller.TransportEventPoller} to poll on a thread from its own thread-pool
 */
@AnyThread
public class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {
  public static final String VERSION = "3141592";
  public static final long FAKE_DEVICE_ID = 1234;
  public static final String FAKE_DEVICE_NAME = "FakeDevice";
  public static final String FAKE_PROCESS_NAME = "FakeProcess";
  // This value is the value used when we get a GRPC request but an int / long field has not been set.
  private static final int EMPTY_REQUEST_VALUE = 0;
  public static final Common.Device FAKE_DEVICE = Common.Device.newBuilder()
    .setDeviceId(FAKE_DEVICE_ID)
    .setSerial(FAKE_DEVICE_NAME)
    .setApiLevel(AndroidVersion.VersionCodes.O)
    .setFeatureLevel(AndroidVersion.VersionCodes.O)
    .setModel(FAKE_DEVICE_NAME)
    .setCpuAbi("arm64-v8a")
    .setState(Common.Device.State.ONLINE)
    .build();
  //Setting PID to be 1 since there is a process with pid being 1 in test input atrace_processid_1
  public static final Common.Process FAKE_PROCESS = Common.Process.newBuilder()
    .setPid(1)
    .setDeviceId(FAKE_DEVICE_ID)
    .setState(Common.Process.State.ALIVE)
    .setName(FAKE_PROCESS_NAME)
    .setExposureLevel(Common.Process.ExposureLevel.DEBUGGABLE)
    .build();
  public static final Common.Process FAKE_PROFILEABLE_PROCESS = FAKE_PROCESS.toBuilder()
    .setPid(2)
    .setExposureLevel(Common.Process.ExposureLevel.PROFILEABLE)
    .build();
  public static final Common.Device FAKE_OFFLINE_DEVICE = FAKE_DEVICE.toBuilder().setState(Common.Device.State.OFFLINE).build();
  public static final Common.Process FAKE_OFFLINE_PROCESS = FAKE_PROCESS.toBuilder().setState(Common.Process.State.DEAD).build();

  private final Map<Long, Common.Device> myDevices;
  private final MultiMap<Common.Device, Common.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  @GuardedBy("myStreamEvents")
  private final Map<Long, List<Common.Event>> myStreamEvents;
  private final Map<Command.CommandType, CommandHandler> myCommandHandlers;
  private final Map<Long, Integer> myEventPositionMarkMap;
  private final Map<Long, EventStreamServer> myStreamServerMap;
  private final FakeTimer myTimer;
  private boolean myThrowErrorOnGetDevices;
  private Common.AgentData myAgentStatus;
  private final AtomicInteger myNextCommandId = new AtomicInteger();

  public FakeTransportService(@NotNull FakeTimer timer) {
    this(timer, true);
  }

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeTransportService(@NotNull FakeTimer timer, boolean connected) {
    this(timer, connected, AndroidVersion.VersionCodes.O, Common.Process.ExposureLevel.DEBUGGABLE);
  }

  public FakeTransportService(@NotNull FakeTimer timer, boolean connected, int featureLevel) {
    this(timer, connected, featureLevel, Common.Process.ExposureLevel.DEBUGGABLE);
  }

  public FakeTransportService(@NotNull FakeTimer timer, boolean connected, int featureLevel, Common.Process.ExposureLevel exposureLevel) {
    myDevices = new HashMap<>();
    myProcesses = MultiMap.create();
    myCache = new HashMap<>();
    myStreamEvents = new HashMap<>();
    myCommandHandlers = new HashMap<>();
    myEventPositionMarkMap = new HashMap<>();
    myStreamServerMap = new HashMap<>();
    myTimer = timer;
    Common.Device device = featureLevel == FAKE_DEVICE.getFeatureLevel()
                           ? FAKE_DEVICE
                           : FAKE_DEVICE.toBuilder().setFeatureLevel(featureLevel).build();
    Common.Process process = exposureLevel == FAKE_PROCESS.getExposureLevel()
                             ? FAKE_PROCESS
                             : FAKE_PROCESS.toBuilder().setExposureLevel(exposureLevel).build();
    if (connected) {
      addDevice(device);
      addProcess(device, process);
    }
    initializeCommandHandlers();
  }

  /**
   * This method creates any command handlers needed by test, for more information see {@link CommandHandler}.
   */
  private void initializeCommandHandlers() {
    setCommandHandler(Command.CommandType.BEGIN_SESSION, new BeginSession(myTimer));
    setCommandHandler(Command.CommandType.END_SESSION, new EndSession(myTimer));
    setCommandHandler(Command.CommandType.DISCOVER_PROFILEABLE, new DiscoverProfileable(myTimer));
    setCommandHandler(Command.CommandType.START_TRACE, new StartTrace(myTimer));
    setCommandHandler(Command.CommandType.STOP_TRACE, new StopTrace(myTimer));
    MemoryAllocTracking allocTrackingHandler = new MemoryAllocTracking(myTimer);
    setCommandHandler(Command.CommandType.START_ALLOC_TRACKING, allocTrackingHandler);
    setCommandHandler(Command.CommandType.STOP_ALLOC_TRACKING, allocTrackingHandler);
    setCommandHandler(Command.CommandType.MEMORY_ALLOC_SAMPLING, new MemoryAllocSampling(myTimer));
    setCommandHandler(Command.CommandType.HEAP_DUMP, new HeapDump(myTimer));
    setCommandHandler(Command.CommandType.GET_CPU_CORE_CONFIG, new GetCpuCoreConfig(myTimer));
  }

  /**
   * Allow test to overload specific command handles if they need to generate customized data.
   */
  public void setCommandHandler(Command.CommandType type, CommandHandler handler) {
    myCommandHandlers.put(type, handler);
  }

  public void addProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device.getDeviceId());
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.putValue(myDevices.get(device.getDeviceId()), process);
    // The event pipeline expects process started / ended events. As such depending on the process state when passed in we add such events.
    if (process.getState() == Common.Process.State.ALIVE) {
      addEventToStream(device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(process.getPid())
        .setPid(process.getPid())
        .setProcess(Common.ProcessData.newBuilder()
                      .setProcessStarted(Common.ProcessData.ProcessStarted.newBuilder()
                                           .setProcess(process)))
        .build());
    }
    if (process.getState() == Common.Process.State.DEAD) {
      addEventToStream(device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(process.getPid())
        .setPid(process.getPid())
        .setIsEnded(true)
        .build());
    }
  }

  public void removeProcess(Common.Device device, Common.Process process) {
    if (!myDevices.containsKey(device.getDeviceId())) {
      throw new IllegalArgumentException("Invalid device: " + device);
    }
    assert device.getDeviceId() == process.getDeviceId();
    myProcesses.remove(myDevices.get(device.getDeviceId()), process);
    // The event pipeline doesn't delete data so this fucntion is a no-op.
  }

  public void stopProcess(Common.Device device, Common.Process process) {
    removeProcess(device, process);
    // Despite the confusing name, this triggers a process end event.
    addProcess(device, process.toBuilder().setState(Common.Process.State.DEAD).build());
  }

  public void addDevice(Common.Device device) {
    addDevice(device, myTimer::getCurrentTimeNs);
  }

  public void addDevice(Common.Device device, long timestamp) {
    addDevice(device, () -> timestamp);
  }

  private void addDevice(Common.Device device, Supplier<Long> timestampSupplier) {
    myDevices.put(device.getDeviceId(), device);
    // The event pipeline expects devices are connected via streams. So when a new devices is added we create a stream connected event.
    // likewise when a device is taken offline we create a stream disconnected event.
    if (device.getState() == Common.Device.State.ONLINE) {
      addEventToStream(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Common.Event.newBuilder()
        .setTimestamp(timestampSupplier.get())
        .setKind(Common.Event.Kind.STREAM)
        .setGroupId(device.getDeviceId())
        .setStream(Common.StreamData.newBuilder().setStreamConnected(Common.StreamData.StreamConnected.newBuilder().setStream(
          Common.Stream.newBuilder()
            .setType(Common.Stream.Type.DEVICE)
            .setStreamId(device.getDeviceId())
            .setDevice(device))))
        .build());
    }
    if (device.getState() == Common.Device.State.OFFLINE || device.getState() == Common.Device.State.DISCONNECTED) {
      addEventToStream(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Common.Event.newBuilder()
        .setTimestamp(timestampSupplier.get())
        .setGroupId(device.getDeviceId())
        .setKind(Common.Event.Kind.STREAM)
        .setIsEnded(true)
        .build());
    }
  }

  public void updateDevice(Common.Device oldDevice, Common.Device newDevice) {
    // Move processes from old to new device
    myProcesses.putValues(newDevice, myProcesses.get(oldDevice));
    // Remove old device from processes map.
    myProcesses.remove(oldDevice);
    // Update device on devices map
    myDevices.remove(oldDevice.getDeviceId());
    // Update device simply kills the old device and swaps it with a new device. As such we kill the old device by creating a
    // stream disconnected event for the events pipeline.
    addEventToStream(oldDevice.getDeviceId(), Common.Event.newBuilder()
      .setTimestamp(myTimer.getCurrentTimeNs())
      .setKind(Common.Event.Kind.STREAM)
      .setGroupId(oldDevice.getDeviceId())
      .setIsEnded(true)
      .build());
    addDevice(newDevice);
  }

  /**
   * This is a helper function for test running the new data pipeline. The input session data are converted to generic Event proto messages
   * and are available via the GetEventGroups API.
   */
  public void addSession(Common.Session session, Common.SessionMetaData metadata) {
    addEventToStream(session.getStreamId(), Common.Event.newBuilder()
      .setGroupId(session.getSessionId())
      .setPid(session.getPid())
      .setKind(Common.Event.Kind.SESSION)
      .setTimestamp(session.getStartTimestamp())
      .setSession(
        Common.SessionData.newBuilder()
          .setSessionStarted(
            Common.SessionData.SessionStarted.newBuilder()
              .setSessionId(session.getSessionId())
              .setPid(session.getPid())
              .setStartTimestampEpochMs(metadata.getStartTimestampEpochMs())
              .setJvmtiEnabled(metadata.getJvmtiEnabled())
              .setSessionName(metadata.getSessionName())
              .setType(Common.SessionData.SessionStarted.SessionType.FULL)))
      .build());
    if (session.getEndTimestamp() != Long.MAX_VALUE) {
      addEventToStream(session.getStreamId(), Common.Event.newBuilder()
        .setGroupId(session.getSessionId())
        .setPid(session.getPid())
        .setKind(Common.Event.Kind.SESSION)
        .setIsEnded(true)
        .setTimestamp(session.getEndTimestamp())
        .build());
    }
  }

  public void addFile(String id, ByteString contents) {
    myCache.put(id, contents);
  }

  public void connectToStreamServer(Common.Stream stream, EventStreamServer streamServer) {
    myStreamServerMap.put(stream.getStreamId(), streamServer);
  }

  public void disconnectFromStreamServer(long streamId) {
    myStreamServerMap.remove(streamId);
  }

  @Override
  public void getVersion(Transport.VersionRequest request, StreamObserver<Transport.VersionResponse> responseObserver) {
    responseObserver.onNext(Transport.VersionResponse.newBuilder().setVersion(VERSION).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getDevices(Transport.GetDevicesRequest request, StreamObserver<Transport.GetDevicesResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    Transport.GetDevicesResponse.Builder response = Transport.GetDevicesResponse.newBuilder();
    for (Common.Device device : myDevices.values()) {
      response.addDevice(device);
    }

    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getProcesses(Transport.GetProcessesRequest request, StreamObserver<Transport.GetProcessesResponse> responseObserver) {
    Transport.GetProcessesResponse.Builder response = Transport.GetProcessesResponse.newBuilder();
    Common.Device device = myDevices.get(request.getDeviceId());
    if (device != null) {
      for (Common.Process process : myProcesses.get(device)) {
        response.addProcess(process);
      }
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getCurrentTime(Transport.TimeRequest request, StreamObserver<Transport.TimeResponse> responseObserver) {
    Transport.TimeResponse.Builder response = Transport.TimeResponse.newBuilder();
    response.setTimestampNs(myTimer.getCurrentTimeNs());
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBytes(Transport.BytesRequest request, StreamObserver<Transport.BytesResponse> responseObserver) {
    Transport.BytesResponse.Builder builder = Transport.BytesResponse.newBuilder();
    ByteString bytes;
    if (myStreamServerMap.containsKey(request.getStreamId())) {
      bytes = myStreamServerMap.get(request.getStreamId()).getByteCacheMap().get(request.getId());
    }
    else {
      bytes = myCache.get(request.getId());
    }
    if (bytes != null) {
      builder.setContents(bytes);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getAgentStatus(Transport.AgentStatusRequest request, StreamObserver<Common.AgentData> responseObserver) {
    responseObserver.onNext(myAgentStatus != null ? myAgentStatus : Common.AgentData.getDefaultInstance());
    responseObserver.onCompleted();
  }

  public void setAgentStatus(@NotNull Common.AgentData status) {
    myAgentStatus = status;
  }

  public void setThrowErrorOnGetDevices(boolean throwErrorOnGetDevices) {
    myThrowErrorOnGetDevices = throwErrorOnGetDevices;
  }

  public boolean getAgentAttachCalled() {
    return ((BeginSession)myCommandHandlers.get(Command.CommandType.BEGIN_SESSION)).getAgentAttachCalled();
  }

  public List<Long> getDiscoveringProfileableStreamIds() {
    return ((DiscoverProfileable)myCommandHandlers.get(Command.CommandType.DISCOVER_PROFILEABLE)).getCommandCalledStreamIds();
  }

  public CommandHandler getRegisteredCommand(Command.CommandType commandType) {
    return myCommandHandlers.get(commandType);
  }

  /**
   * Helper method for appending to the event list of a stream.
   */
  public void addEventToStream(long streamId, Common.Event event) {
    // getListForStream - is guarded by lock, but we add to resulting array  => should be behind lock too
    synchronized (myStreamEvents) {
      getListForStream(streamId).add(event);
    }
  }

  /**
   * Remember a position in the event list for the specified stream.
   */
  public void saveEventPositionMark(long streamId) {
    synchronized (myStreamEvents) {
      myEventPositionMarkMap.put(streamId, getListForStream(streamId).size());
    }
  }

  /**
   * Remove all events added added after the previously saved mark in the events for the specified stream.
   */
  public void revertToEventPositionMark(long streamId) {
    synchronized (myStreamEvents) {
      int mark = myEventPositionMarkMap.getOrDefault(streamId, 0);
      List<Common.Event> events = getListForStream(streamId);
      events.subList(mark, events.size()).clear();
    }
  }

  /**
   * Helper method for creating a list of events for a stream if it does not exist, otherwise returning the event list.
   */
  public List<Common.Event> getListForStream(long streamId) {
    synchronized (myStreamEvents) {
      return myStreamEvents.computeIfAbsent(streamId, id -> new ArrayList<>());
    }
  }

  @Override
  public void execute(Transport.ExecuteRequest request, StreamObserver<Transport.ExecuteResponse> responseObserver) {
    assertThat(myCommandHandlers.containsKey(request.getCommand().getType()))
      .named("Missing command handler for: %s", request.getCommand().getType().toString()).isTrue();
    Command command = request.getCommand().toBuilder().setCommandId(myNextCommandId.incrementAndGet()).build();
    // getListForStream - is guarded by lock, but handleCommand modifies resulting array  => should be behind lock too
    // TODO(b/142524939): improve CommandHandler API
    synchronized (myStreamEvents) {
      myCommandHandlers.get(command.getType()).handleCommand(command, getListForStream(command.getStreamId()));
    }
    responseObserver.onNext(Transport.ExecuteResponse.newBuilder().setCommandId(command.getCommandId()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getEventGroups(Transport.GetEventGroupsRequest request, StreamObserver<Transport.GetEventGroupsResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }

    // Drain all stream servers' event queue and insert the events as if they were inserted to the datastore.
    myStreamServerMap.forEach((streamId, streamServer) -> {
      while (!streamServer.getEventDeque().isEmpty()) {
        Common.Event event = streamServer.getEventDeque().poll();
        addEventToStream(streamId, event);
      }
    });

    // This logic mirrors that logic of transport-database. We do proper filtering of all events here so our test, behave as close to
    // runtime as possible.
    HashMap<Long, Transport.EventGroup.Builder> eventGroups = new HashMap<>();
    synchronized (myStreamEvents) {
      for (long streamId : myStreamEvents.keySet()) {
        if (request.getStreamId() != EMPTY_REQUEST_VALUE && streamId != request.getStreamId()) {
          continue;
        }

        // Map the list of events by kind then by group. Group Ids are uniquely within kind and this prevents different-kind events
        // with conflicting groupids to be matched and inserted into the resulting EventGroups out of order.
        Map<Common.Event.Kind, List<Common.Event>> kindEventsMap =
          myStreamEvents.get(streamId).stream().collect(Collectors.groupingBy(Common.Event::getKind));
        // We always expect the kind filter to be set from the request in our production code.
        if (kindEventsMap.containsKey(request.getKind())) {
          Map<Long, List<Common.Event>> groupEventMap = kindEventsMap.get(request.getKind()).stream()
            .collect(Collectors.groupingBy(Common.Event::getGroupId));
          for (Long groupId : groupEventMap.keySet()) {
            if (request.getGroupId() != EMPTY_REQUEST_VALUE && request.getGroupId() != groupId) {
              continue;
            }

            List<Common.Event> events = groupEventMap.get(groupId);
            ListIterator<Common.Event> it = events.listIterator();
            while (it.hasNext()) {
              Common.Event event = it.next();
              if (request.getPid() != EMPTY_REQUEST_VALUE && request.getPid() != event.getPid()) {
                continue;
              }
              if (request.getFromTimestamp() != EMPTY_REQUEST_VALUE && request.getFromTimestamp() > event.getTimestamp()) {
                // Event occurs before from_timestamp but it may still be included.
                if (event.getIsEnded()) {
                  // No more events in this group. Exclude this event.
                  continue;
                }
                if (it.hasNext() && events.get(it.nextIndex()).getTimestamp() < request.getFromTimestamp()) {
                  // Next event occurs before from_timestamp as well. Exclude this event.
                  continue;
                }
                // Otherwise this event crosses from_timestamp boundary and should be included.
              }
              if (request.getToTimestamp() != EMPTY_REQUEST_VALUE && request.getToTimestamp() < event.getTimestamp()) {
                // Event occurs after to_timestamp but it may be included.
                // previousIndex() returns the element we just iterated, so -1 to get the previous element.
                int previousIndex = it.previousIndex() - 1;
                if (previousIndex < 0) {
                  // No previous event in this group. Exclude this event.
                  continue;
                }
                if (events.get(previousIndex).getTimestamp() > request.getToTimestamp()) {
                  // Previous event occurs before to_timestamp. Exclude this event.
                  continue;
                }
                // Otherwise this event crosses to_timestamp boundary and should be included.
              }

              eventGroups.computeIfAbsent(event.getGroupId(), id -> Transport.EventGroup.newBuilder().setGroupId(id)).addEvents(event);
            }
          }
        }
      }
    }

    Transport.GetEventGroupsResponse.Builder builder = Transport.GetEventGroupsResponse.newBuilder();
    for (Transport.EventGroup.Builder eventGroup : eventGroups.values()) {
      builder.addGroups(eventGroup);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteEvents(Transport.DeleteEventsRequest request, StreamObserver<Transport.DeleteEventsResponse> responseObserver) {
    synchronized (myStreamEvents) {
      List<Common.Event> events = myStreamEvents.get(request.getStreamId());
      Iterator<Common.Event> itr = events.iterator();
      while (itr.hasNext()) {
        Common.Event event = itr.next();
        if (event.getPid() == request.getPid() &&
            event.getGroupId() == request.getGroupId() &&
            event.getKind() == request.getKind() &&
            event.getTimestamp() >= request.getFromTimestamp() &&
            event.getTimestamp() <= request.getToTimestamp()) {
          itr.remove();
        }
      }
    }
    responseObserver.onNext(Transport.DeleteEventsResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}