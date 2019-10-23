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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.idea.transport.faketransport.commands.BeginSession;
import com.android.tools.idea.transport.faketransport.commands.CommandHandler;
import com.android.tools.idea.transport.faketransport.commands.EndSession;
import com.intellij.util.containers.MultiMap;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

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
    .setState(Common.Device.State.ONLINE)
    .build();
  //Setting PID to be 1 since there is a process with pid being 1 in test input atrace_processid_1
  public static final Common.Process FAKE_PROCESS = Common.Process.newBuilder()
    .setPid(1)
    .setDeviceId(FAKE_DEVICE_ID)
    .setState(Common.Process.State.ALIVE)
    .setName(FAKE_PROCESS_NAME)
    .build();

  private final Map<Long, Common.Device> myDevices;
  private final MultiMap<Common.Device, Common.Process> myProcesses;
  private final Map<String, ByteString> myCache;
  private final Map<Long, List<Transport.EventGroup.Builder>> myStreamEvents;
  private final Map<Command.CommandType, CommandHandler> myCommandHandlers;
  private final FakeTimer myTimer;
  private boolean myThrowErrorOnGetDevices;
  private Common.AgentData myAgentStatus;

  public FakeTransportService(@NotNull FakeTimer timer) {
    this(timer, true);
  }

  /**
   * Creates a fake profiler service. If connected is true there will be a device with a process already present.
   */
  public FakeTransportService(@NotNull FakeTimer timer, boolean connected) {
    myDevices = new HashMap<>();
    myProcesses = MultiMap.create();
    myCache = new HashMap<>();
    myStreamEvents = new HashMap<>();
    myCommandHandlers = new HashMap<>();
    myTimer = timer;
    if (connected) {
      addDevice(FAKE_DEVICE);
      addProcess(FAKE_DEVICE, FAKE_PROCESS);
    }
    initializeCommandHandlers();
  }

  /**
   * This method creates any command handlers needed by test, for more information see {@link CommandHandler}.
   */
  private void initializeCommandHandlers() {
    setCommandHandler(Command.CommandType.BEGIN_SESSION, new BeginSession(myTimer));
    setCommandHandler(Command.CommandType.END_SESSION, new EndSession(myTimer));
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
      addEventToEventGroup(device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(process.getPid())
        .setProcess(Common.ProcessData.newBuilder()
                      .setProcessStarted(Common.ProcessData.ProcessStarted.newBuilder()
                                           .setProcess(process)))
        .build());
    }
    if (process.getState() == Common.Process.State.DEAD) {
      addEventToEventGroup(device.getDeviceId(), Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(process.getPid())
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

  public void addDevice(Common.Device device) {
    myDevices.put(device.getDeviceId(), device);
    // The event pipeline expects devices are connected via streams. So when a new devices is added we create a stream connected event.
    // likewise when a device is taken offline we create a stream disconnected event.
    if (device.getState() == Common.Device.State.ONLINE) {
      addEventToEventGroup(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
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
      addEventToEventGroup(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Common.Event.newBuilder()
        .setTimestamp(myTimer.getCurrentTimeNs())
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
    addEventToEventGroup(oldDevice.getDeviceId(), Common.Event.newBuilder()
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
    addEventToEventGroup(session.getStreamId(), Common.Event.newBuilder()
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
      addEventToEventGroup(session.getStreamId(), Common.Event.newBuilder()
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
    ByteString bytes = myCache.get(request.getId());
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

  /**
   * Helper method for finding an existing event group and updating its array of events, or creating an event group if one does not exist.
   * The group to add to is taken from the group set on the event.
   */
  public void addEventToEventGroup(long streamId, Common.Event event) {
    synchronized (myStreamEvents) {
      List<Transport.EventGroup.Builder> groups = getListForStream(streamId);
      long groupId = event.getGroupId();
      Optional<Transport.EventGroup.Builder> eventGroup = groups.stream().filter(group -> group.getGroupId() == groupId).findFirst();
      if (eventGroup.isPresent()) {
        eventGroup.get().addEvents(event);
      }
      else {
        groups.add(Transport.EventGroup.newBuilder().setGroupId(groupId).addEvents(event));
      }
    }
  }

  /**
   * Helper method for creating a list of event groups if one does not exist, otherwise returning the existing group.
   */
  private List<Transport.EventGroup.Builder> getListForStream(long streamId) {
    if (!myStreamEvents.containsKey(streamId)) {
      myStreamEvents.put(streamId, new ArrayList<>());
    }
    return myStreamEvents.get(streamId);
  }

  @Override
  public void execute(Transport.ExecuteRequest request, StreamObserver<Transport.ExecuteResponse> responseObserver) {
    assertThat(myCommandHandlers.containsKey(request.getCommand().getType()))
      .named("Missing command handler for: %s", request.getCommand().getType().toString()).isTrue();
    myCommandHandlers.get(request.getCommand().getType())
      .handleCommand(request.getCommand(), getListForStream(request.getCommand().getStreamId()));
    responseObserver.onNext(Transport.ExecuteResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void getEventGroups(Transport.GetEventGroupsRequest request, StreamObserver<Transport.GetEventGroupsResponse> responseObserver) {
    if (myThrowErrorOnGetDevices) {
      responseObserver.onError(new RuntimeException("Server error"));
      return;
    }
    // This logic mirrors that logic of transport-database. We do proper filtering of all events here so our test, behave as close to runtime as
    // possible.
    HashMap<Long, Transport.EventGroup.Builder> eventGroups = new HashMap<>();
    synchronized (myStreamEvents) {
      for (long streamId : myStreamEvents.keySet()) {
        if (request.getStreamId() != EMPTY_REQUEST_VALUE && streamId != request.getStreamId()) {
          continue;
        }
        for (Transport.EventGroup.Builder eventGroup : myStreamEvents.get(streamId)) {
          for (Common.Event event : eventGroup.getEventsList()) {
            if (request.getPid() != EMPTY_REQUEST_VALUE && request.getPid() != event.getPid()) {
              continue;
            }
            if (request.getGroupId() != EMPTY_REQUEST_VALUE && request.getGroupId() != event.getGroupId()) {
              continue;
            }
            if (request.getFromTimestamp() != EMPTY_REQUEST_VALUE && request.getFromTimestamp() > event.getTimestamp()) {
              continue;
            }
            if (request.getToTimestamp() != EMPTY_REQUEST_VALUE && request.getToTimestamp() < event.getTimestamp()) {
              continue;
            }
            if (request.getKind() != event.getKind()) {
              continue;
            }
            if (!eventGroups.containsKey(eventGroup.getGroupId())) {
              eventGroups.put(eventGroup.getGroupId(), Transport.EventGroup.newBuilder().setGroupId(eventGroup.getGroupId()));
            }
            eventGroups.get(eventGroup.getGroupId()).addEvents(event);
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
}
