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
package com.android.tools.datastore.service;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.ServicePassThrough;
import com.android.tools.datastore.TaskDatabaseManager;
import com.android.tools.datastore.database.DataStoreTable;
import com.android.tools.datastore.database.DeviceProcessTable;
import com.android.tools.datastore.database.UnifiedEventsTable;
import com.android.tools.datastore.poller.UnifiedEventsDataPoller;
import com.android.tools.idea.io.grpc.Channel;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.AgentData;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.Stream;
import com.android.tools.profiler.proto.Common.StreamData;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.AgentStatusRequest;
import com.android.tools.profiler.proto.Transport.BytesRequest;
import com.android.tools.profiler.proto.Transport.FileResponse;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.ExecuteResponse;
import com.android.tools.profiler.proto.Transport.GetDevicesRequest;
import com.android.tools.profiler.proto.Transport.GetDevicesResponse;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profiler.proto.Transport.GetProcessesRequest;
import com.android.tools.profiler.proto.Transport.GetProcessesResponse;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.Transport.VersionRequest;
import com.android.tools.profiler.proto.Transport.SetTaskDbRequest;
import com.android.tools.profiler.proto.Transport.SetTaskDbResponse;
import com.android.tools.profiler.proto.Transport.UnsetTaskDbRequest;
import com.android.tools.profiler.proto.Transport.UnsetTaskDbResponse;
import com.android.tools.profiler.proto.Transport.VersionResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.android.tools.idea.io.grpc.StatusRuntimeException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.IOException;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Datastore layer of the unified event rpc pipeline, responsible for forwarding commands to the device and handling generic event
 * queries to the database. It currently also houses the legacy APIs for querying for devices and processes. e.g.
 * {@link #getDevices(GetDevicesRequest, StreamObserver)}, {@link #getProcesses(GetProcessesRequest, StreamObserver)}, etc.
 */
public class TransportService extends TransportServiceGrpc.TransportServiceImplBase implements ServicePassThrough {
  private static final Logger LOG = Logger.getInstance(TransportService.class);
  /**
   * A set of event kinds that should be stored in a task-specific database ("task DB") if one is active.
   * The task DB is a separate database file dedicated to a single, profiler task (e.g., Live View, Allocations),
   * which can be exported and shared as a file.
   * All other events are stored in the main, shared profiler database.
   */
  private static final Set<Event.Kind> TASK_DB_EVENT_KINDS =
    ImmutableSet.of(
      Event.Kind.SESSION,

      Event.Kind.CPU_USAGE,
      Event.Kind.CPU_THREAD,
      Event.Kind.CPU_TRACE,

      Event.Kind.MEMORY_GC,
      Event.Kind.MEMORY_ALLOC_SAMPLING,
      Event.Kind.MEMORY_ALLOC_TRACKING,
      Event.Kind.MEMORY_ALLOC_TRACKING_STATUS,
      Event.Kind.MEMORY_ALLOC_CONTEXTS,
      Event.Kind.MEMORY_ALLOC_EVENTS,
      Event.Kind.MEMORY_JNI_REF_EVENTS,
      Event.Kind.MEMORY_ALLOC_STATS,
      Event.Kind.MEMORY_USAGE,

      Event.Kind.VIEW,
      Event.Kind.INTERACTION,
      Event.Kind.LIVE_VIEW_STATUS
    );

  /**
   * A subset of {@link #TASK_DB_EVENT_KINDS} that should be written to *both* the active task DB and the main profiler database.
   * This is necessary for events that need to be discoverable from outside the context of a specific task (e.g., to populate the list
   * of past recordings).
   * Writing them to the task DB also ensures their metadata and information are included when the database is
   * shared as a trace file.
   */
  private static final Set<Event.Kind> DUAL_WRITE_EVENT_KINDS =
    ImmutableSet.of(
      Event.Kind.SESSION,
      Event.Kind.MEMORY_ALLOC_TRACKING,
      Event.Kind.LIVE_VIEW_STATUS
    );

  private final Consumer<Runnable> myFetchExecutor;
  @NotNull private final UnifiedEventsTable myTable;
  @NotNull private final DeviceProcessTable myLegacyTable;
  @NotNull private final DataStoreService myService;

  /**
   * A mapping of active channels to pollers. This mapping allows us to keep track of active pollers for a channel, and clean up pollers
   * when channels are closed.
   */
  private final Map<Channel, UnifiedEventsDataPoller> myUnifiedEventsPollers = Maps.newHashMap();
  /**
   * A map of active channels to unified event streams. This map helps us clean up streams when a channel is closed.
   */
  private final Map<Channel, Stream> myChannelToStream = Maps.newHashMap();
  @VisibleForTesting
  public final AtomicInteger myNextCommandId = new AtomicInteger();

  public TransportService(@NotNull DataStoreService service,
                          @NotNull UnifiedEventsTable unifiedTable,
                          Consumer<Runnable> fetchExecutor) {
    myService = service;
    myFetchExecutor = fetchExecutor;
    myTable = unifiedTable;
    myLegacyTable = new DeviceProcessTable();
  }

  @NotNull
  @Override
  public List<DataStoreService.BackingNamespace> getBackingNamespaces() {
    return Collections.singletonList(DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE);
  }

  @Override
  public void setBackingStore(@NotNull DataStoreService.BackingNamespace namespace, @NotNull Connection connection) {
    assert namespace == DataStoreService.BackingNamespace.DEFAULT_SHARED_NAMESPACE;
    myTable.initialize(connection);
  }

  /**
   * Connects the datastore layer to a channel. By default, this starts the {@link UnifiedEventsDataPoller} for the transport pipeline which
   * streams Events into the database.
   */
  public void connectToChannel(Stream stream, Channel channel) {
    long streamId = stream.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub stub = myService.getTransportClient(streamId);
    assert (stub != null);
    streamConnected(stream);
    UnifiedEventsDataPoller unifiedPoller =
      new UnifiedEventsDataPoller(stream.getStreamId(), event -> insertEvent(stream.getStreamId(), event), stub, myService);
    myUnifiedEventsPollers.put(channel, unifiedPoller);
    myChannelToStream.put(channel, stream);
    DataStoreTable.addDataStoreErrorCallback(unifiedPoller);
    myFetchExecutor.accept(unifiedPoller);
  }

  public void disconnectFromChannel(Channel channel) {
    if (myUnifiedEventsPollers.containsKey(channel)) {
      UnifiedEventsDataPoller poller = myUnifiedEventsPollers.remove(channel);
      poller.stop();
      DataStoreTable.removeDataStoreErrorCallback(poller);
      streamDisconnected(myChannelToStream.remove(channel));
    }
  }

  /**
   * Inserts an event into the appropriate database.
   *
   * <p>This method is the primary sink for all events polled from the device. It is called by
   * {@link UnifiedEventsDataPoller} for each event received from the transport pipeline.
   *
   * <p>The method first checks if the event should trigger the creation of a new, task-specific
   * database (e.g. for a Live View or Allocations recording).
   *
   * <p>Then, it determines where to write the event based on its kind and whether a task database
   * is currently active:
   * <ul>
   *   <li>If a task DB is active and the event is task-related (its kind is in {@link
   *       #TASK_DB_EVENT_KINDS}), it is written to the task DB.
   *   <li>If the event is also marked for dual-writing (its kind is in {@link
   *       #DUAL_WRITE_EVENT_KINDS}), it is additionally written to the main database for
   *       discoverability.
   *   <li>Otherwise, the event is written only to the main database.
   * </ul>
   *
   * @param streamId The ID of the stream the event belongs to.
   * @param event The event to insert.
   */
  private void insertEvent(long streamId, @NotNull Event event) {
    tryAutoStartTaskDb(streamId, event);

    // Decide where to insert
    UnifiedEventsTable taskTable = myService.getTaskEventsTable();
    if (taskTable != null && TASK_DB_EVENT_KINDS.contains(event.getKind())) {
      // This event belongs to a task. Write it to the task-specific DB.
      taskTable.insertUnifiedEvent(streamId, event);

      if (DUAL_WRITE_EVENT_KINDS.contains(event.getKind())) {
        // This event should also be in the main DB for discoverability.
        myTable.insertUnifiedEvent(streamId, event);
      }
    }
    else {
      // Not a task event, or no task active. Write to main DB.
      myTable.insertUnifiedEvent(streamId, event);
    }
  }

  /**
   * For certain task types, we want to automatically create a dedicated database file when the task session starts.
   * This method checks if the given event is a session start for such a task, and if so, creates and sets the task DB.
   */
  private void tryAutoStartTaskDb(long streamId, @NotNull Event event) {
    if (event.getKind() != Event.Kind.SESSION ||
        event.getIsEnded() ||
        !event.hasSession() ||
        !event.getSession().hasSessionStarted()) {
      return;
    }

    var sessionStarted = event.getSession().getSessionStarted();
    var taskType = sessionStarted.getTaskType();
    if (taskType != Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS && taskType != Common.ProfilerTaskType.LIVE_VIEW) {
      return;
    }

    String dbPath;
    try {
      dbPath = FileUtil.createTempFile(getTaskDbPath(taskType, event.getTimestamp()), ".asdb", true).getAbsolutePath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myService.setTaskDb(sessionStarted.getSessionId(), dbPath, taskType, streamId, event.getPid());

    // Store a reference to this DB in the main database, so it can be looked up later
    // via getFile (e.g. for exporting, or for re-associating the DB with the task
    // via SessionsManager.setTaskDb).
    myTable.insertFile(streamId, Long.toString(event.getTimestamp()), FileResponse.newBuilder().setFilePath(dbPath).build());
  }

  private String getTaskDbPath(Common.ProfilerTaskType taskType, long timestamp) {
    String nameHint = "task";
    if (taskType == Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS) {
      nameHint = "java-kotlin-allocs";
    }
    else if (taskType == Common.ProfilerTaskType.LIVE_VIEW) {
      nameHint = "live-view";
    }
    return nameHint + "-" + timestamp;
  }

  private void streamConnected(Stream stream) {
    myTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setGroupId(stream.getStreamId())
      .setTimestamp(System.nanoTime())
      .setStream(StreamData.newBuilder()
                   .setStreamConnected(StreamData.StreamConnected.newBuilder()
                                         .setStream(stream)))
      .build());
  }

  private void streamDisconnected(Stream stream) {
    myTable.insertUnifiedEvent(DataStoreService.DATASTORE_RESERVED_STREAM_ID, Event.newBuilder()
      .setKind(Event.Kind.STREAM)
      .setGroupId(stream.getStreamId())
      .setIsEnded(true)
      .setTimestamp(System.nanoTime())
      .build());
  }

  @Override
  public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> observer) {
    // This function can get called before the datastore is connected to a device as such we need to check
    // if we have a connection before attempting to get the time.
    TransportServiceGrpc.TransportServiceBlockingStub client = myService.getTransportClient(request.getStreamId());
    if (client != null) {
      observer.onNext(client.getCurrentTime(request));
    }
    else {
      // Need to return something in the case of no device.
      observer.onNext(TimeResponse.getDefaultInstance());
    }
    observer.onCompleted();
  }

  @Override
  public void getVersion(VersionRequest request, StreamObserver<VersionResponse> observer) {
    TransportServiceGrpc.TransportServiceBlockingStub client = myService.getTransportClient(request.getStreamId());
    if (client != null) {
      observer.onNext(client.getVersion(request));
    }
    observer.onCompleted();
  }

  @Override
  public void getDevices(GetDevicesRequest request, StreamObserver<GetDevicesResponse> observer) {
    GetDevicesResponse response = myLegacyTable.getDevices();
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getProcesses(GetProcessesRequest request, StreamObserver<GetProcessesResponse> observer) {
    GetProcessesResponse response = myLegacyTable.getProcesses(request);
    observer.onNext(response);
    observer.onCompleted();
  }

  @Override
  public void getAgentStatus(AgentStatusRequest request, StreamObserver<AgentData> observer) {
    observer.onNext(myLegacyTable.getAgentStatus(request));
    observer.onCompleted();
  }

  @Override
  public void getFile(BytesRequest request, StreamObserver<FileResponse> responseObserver) {
    // First, check the local database to see if we have a cached path for this file ID.
    // This avoids re-downloading large files from the device.
    FileResponse response = myTable.getFile(request);
    long streamId = request.getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client = myService.getTransportClient(streamId);

    // If it's a cache miss and we have a connection to the device, fetch the file.
    if (response == null && client != null) {
      try {
        response = client.getFile(request);
        if (!response.getFilePath().isEmpty()) {
          // Cache the new file's path in our database for future requests.
          myTable.insertFile(streamId, request.getId(), response);
        }
      }
      catch (StatusRuntimeException ex) {
        // The call to the device failed. Log the error and fall through to return a default response.
        LOG.warn(String.format(Locale.US, "Failed to get bytes for stream %d, id %s", streamId, request.getId()), ex);
      }
    }
    else if (response == null) {
      response = FileResponse.getDefaultInstance();
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> responseObserver) {
    // TODO (b/114751407): Send stream id 0 to all streams.
    long streamId = request.getCommand().getStreamId();
    TransportServiceGrpc.TransportServiceBlockingStub client = myService.getTransportClient(streamId);
    if (client != null) {
      Commands.Command command = request.getCommand();
      int commandId = myNextCommandId.incrementAndGet();
      request = request.toBuilder().setCommand(command.toBuilder().setCommandId(commandId)).build();
      responseObserver.onNext(client.execute(request).toBuilder().setCommandId(commandId).build());
    }
    else {
      responseObserver.onNext(ExecuteResponse.getDefaultInstance());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void getEventGroups(GetEventGroupsRequest request, StreamObserver<GetEventGroupsResponse> responseObserver) {
    GetEventGroupsResponse.Builder response = GetEventGroupsResponse.newBuilder();
    Collection<EventGroup> events;
    UnifiedEventsTable tableToQuery = myTable;

    UnifiedEventsTable taskTable = myService.getTaskEventsTable();
    if (taskTable != null && (TASK_DB_EVENT_KINDS.contains(request.getKind()) && !DUAL_WRITE_EVENT_KINDS.contains(request.getKind()))) {
      tableToQuery = taskTable;

      // For imported sessions, the request contains a fake streamId and pid=0.
      // We need to translate these to the real IDs from the database file before querying.
      if (request.getPid() == 0) {
        TaskDatabaseManager.ImportedSessionMapping mapping = myService.getImportedSessionMapping();
        if (mapping != null) {
          request = request.toBuilder().setStreamId(mapping.realStreamId()).setPid(mapping.realPid()).build();
        }
      }
    }

    events = tableToQuery.queryUnifiedEventGroups(request);
    response.addAllGroups(events);
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  @Override
  public void deleteEvents(Transport.DeleteEventsRequest request, StreamObserver<Transport.DeleteEventsResponse> responseObserver) {
    myTable.deleteEvents(request.getStreamId(),
                         request.getPid(),
                         request.getGroupId(),
                         request.getKind(),
                         request.getFromTimestamp(),
                         request.getToTimestamp());
    responseObserver.onNext(Transport.DeleteEventsResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void setTaskDb(SetTaskDbRequest request, StreamObserver<SetTaskDbResponse> responseObserver) {
    myService.setTaskDb(request.getSessionId(), request.getDbPath(), null, 0, 0);
    responseObserver.onNext(SetTaskDbResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void unsetTaskDb(UnsetTaskDbRequest request, StreamObserver<UnsetTaskDbResponse> responseObserver) {
    myService.unsetTaskDb(request.getSessionId());
    responseObserver.onNext(UnsetTaskDbResponse.getDefaultInstance());
    responseObserver.onCompleted();
  }
}
