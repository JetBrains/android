/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions;

import static com.android.tools.profilers.StudioProfilers.buildSessionName;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.protobuf.GeneratedMessageV3;
import com.android.tools.idea.transport.EventStreamServer;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.profiler.proto.Commands.BeginSession;
import com.android.tools.profiler.proto.Commands.Command;
import com.android.tools.profiler.proto.Commands.EndSession;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Common.Device;
import com.android.tools.profiler.proto.Common.Event;
import com.android.tools.profiler.proto.Common.SessionData;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact;
import com.android.tools.profilers.memory.AllocationSessionArtifact;
import com.android.tools.profilers.memory.HeapProfdSessionArtifact;
import com.android.tools.profilers.memory.HprofSessionArtifact;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A wrapper class for keeping track of the list of sessions that the profilers have seen, along with their associated artifacts (e.g.
 * memory heap dump, CPU capture)
 */
public class SessionsManager extends AspectModel<SessionAspect> {
  private static Logger getLogger() { return Logger.getInstance(SessionsManager.class); }

  /**
   * For usage tracking purposes - specify where a session creation was originated from.
   */
  public enum SessionCreationSource {
    MANUAL, // Session is created by user selecting a process from the dropdown.
    // TODO add enums for sessions created via the toolbar's profile button, or via opening the profiler UI manually
  }

  /**
   * An interface for querying artifacts that belong to a session (e.g. heap dump, cpu capture, bookmarks).
   */
  private interface ArtifactFetcher {
    List<SessionArtifact<?>> fetch(@NotNull StudioProfilers profilers,
                                   @NotNull Common.Session session,
                                   @NotNull Common.SessionMetaData sessionMetaData);
  }

  private static final SessionArtifactComparator ARTIFACT_COMPARATOR = new SessionArtifactComparator();

  @NotNull private final StudioProfilers myProfilers;

  /**
   * A map of Session's Id -> {@link SessionItem}
   */
  @NotNull private Map<Long, SessionItem> mySessionItems;

  /**
   * A map of Session's Id -> {@link Common.SessionMetaData}
   */
  @NotNull private Map<Long, Common.SessionMetaData> mySessionMetaDatas;

  /**
   * A list of session-related items for display in the Sessions panel.
   */
  @NotNull private List<SessionArtifact> mySessionArtifacts;

  /**
   * The currently selected session.
   */
  @NotNull private Common.Session mySelectedSession;

  /**
   * The currently selected artifact's proto
   */
  private GeneratedMessageV3 mySelectedArtifactProto;

  /**
   * The session that is actively being profiled. Note that there can only be one profiling session at a time, but it does not have to be
   * the one that is currently selected (e.g. Users can profile in the background while exploring other sessions history).
   */
  @NotNull private Common.Session myProfilingSession;

  /**
   * A cache of the view ranges that were used by each session before it was unselected. Note that the key represents a Session's id.
   */
  private final Map<Long, Range> mySessionViewRangeMap;

  /**
   * A list of handlers that import sessions based on their file types.
   */
  private final Map<String, Consumer<File>> myImportHandlers = new HashMap<>();

  /**
   * A list of functions that should be called for each {@link Common.Session} for retrieving its data artifacts.
   */
  @NotNull
  private final List<ArtifactFetcher> myArtifactsFetchers;

  /**
   * Cache the EventStreamServers that were created for imported streams so events and bytes can be added at a later time if desired.
   */
  @NotNull private final Map<Long, EventStreamServer> myStreamIdToStreamServerMap = new HashMap<>();

  public SessionsManager(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    mySelectedSession = myProfilingSession = Common.Session.getDefaultInstance();
    mySessionItems = new HashMap<>();
    mySessionMetaDatas = new HashMap<>();
    // Always return the SessionMetaData default instance for a Session default instance.
    mySessionMetaDatas.put(Common.Session.getDefaultInstance().getSessionId(), Common.SessionMetaData.getDefaultInstance());
    mySessionArtifacts = new ArrayList<>();
    mySessionViewRangeMap = new HashMap<>();

    myArtifactsFetchers = new ArrayList<>();
    myArtifactsFetchers.add(HprofSessionArtifact::getSessionArtifacts);
    myArtifactsFetchers.add(CpuCaptureSessionArtifact::getSessionArtifacts);
    myArtifactsFetchers.add(HeapProfdSessionArtifact::getSessionArtifacts);
    myArtifactsFetchers.add(AllocationSessionArtifact::getSessionArtifacts);
  }

  @NotNull
  public Common.Session getSelectedSession() {
    return mySelectedSession;
  }

  @NotNull
  public Common.Session getProfilingSession() {
    return myProfilingSession;
  }

  /**
   * Return the currently selected artifact's proto
   */
  @VisibleForTesting
  public GeneratedMessageV3 getSelectedArtifactProto() {
    return mySelectedArtifactProto;
  }

  /**
   * Return the meta data of current selected session
   */
  @NotNull
  public Common.SessionMetaData getSelectedSessionMetaData() {
    return mySessionMetaDatas.get(mySelectedSession.getSessionId());
  }

  @NotNull
  public List<SessionArtifact> getSessionArtifacts() {
    return mySessionArtifacts;
  }

  public boolean isSessionAlive() {
    return isSessionAlive(mySelectedSession);
  }

  public static boolean isSessionAlive(@NotNull Common.Session session) {
    return session.getEndTimestamp() == Long.MAX_VALUE;
  }

  @NotNull
  public Range getSessionPreferredViewRange(@NotNull Common.Session session) {
    double viewRangeMin = TimeUnit.NANOSECONDS.toMicros(session.getStartTimestamp());
    double viewRangeMax = TimeUnit.NANOSECONDS.toMicros(session.getEndTimestamp());
    // If there is a cached view range, use it instead of showing the full range.
    if (mySessionViewRangeMap.containsKey(session.getSessionId())) {
      Range cachedRange = mySessionViewRangeMap.get(session.getSessionId());
      // The previous view range could contain the initial empty space if the data range is short, just clamp the view range's min to the
      // data range's min in that case.
      viewRangeMin = Math.max(viewRangeMin, cachedRange.getMin());
      // If a device is disconnected (e.g. unplugged, the update loop could have put the view range's max over the session's end time,
      // which is determined by the timestamp of the last TimeResponse we received from the device, simply clamp the max here to be the
      // session's end time when that happens.
      viewRangeMax = Math.min(viewRangeMax, cachedRange.getMax());
    }

    return new Range(viewRangeMin, viewRangeMax);
  }

  /**
   * Perform an update to retrieve all session instances.
   */
  public void update() {
    GetEventGroupsRequest request = GetEventGroupsRequest.newBuilder().setKind(Event.Kind.SESSION).build();
    GetEventGroupsResponse response = myProfilers.getClient().getTransportClient().getEventGroups(request);
    updateSessionItemsByGroup(response.getGroupsList());
  }

  /**
   * Update or add to the list of {@link SessionItem} based on the queried {@link EventGroup}.
   */
  private void updateSessionItemsByGroup(List<EventGroup> groups) {
    List<SessionArtifact> sessionArtifacts = new ArrayList<>();
    List previousArtifactProtos = ContainerUtil.map(mySessionArtifacts, artifact -> artifact.getArtifactProto());

    // Note: we only add to a growing list of sessions at the moment.
    // If there are multiple groups being updated (e.g., one session ends and another one starts), we want to
    // process the new session at last. The last one being processed will be the selected session.
    List<EventGroup> sortedGroups = Lists.newArrayList(groups);
    // Each group should have up to two events. The first event is the start event, and the second one is the end.
    // So the new session should have one event, while completed sessions have two events. The order of completed
    // sessions usually doesn't matter, but when a new project is loaded, every session is perceived as new and we
    // want to select the last imported one.
    Collections.sort(sortedGroups, Comparator.comparing(EventGroup::getEventsCount, Comparator.reverseOrder())
      .thenComparingLong(g -> g.getEventsCount() > 0 ? g.getEvents(0).getSession().getSessionStarted().getStartTimestampEpochMs() : 0));
    sortedGroups.forEach(group -> {
      SessionItem sessionItem = mySessionItems.get(group.getGroupId());
      boolean sessionStateChanged = false;
      Common.Event startEvent = group.getEvents(0);
      // For non-full sessions (e.g. import), we expect to receive both the BEGIN_SESSION and END_SESSION events first before
      // processing them, otherwiser the profiler model might think that it is an ongoing session for a brief moment if all the events
      // have not been streamed to the database.
      if (startEvent.getSession().getSessionStarted().getType() != SessionData.SessionStarted.SessionType.FULL &&
          group.getEventsCount() < 2) {
        return;
      }

      // We found a new session we process it and update our internal state.
      if (sessionItem == null) {
        sessionItem = processSessionStarted(startEvent);
        sessionStateChanged = true;
      }
      // If we ended a session we process that end here.
      if (group.getEventsCount() == 2 && sessionItem.isOngoing()) {
        Common.Session session = sessionItem.getSession().toBuilder().setEndTimestamp(group.getEvents(1).getTimestamp()).build();
        sessionItem.setSession(session);
        sessionStateChanged = true;
      }
      if (sessionStateChanged) {
        setSessionInternal(sessionItem.getSession());
        if (sessionItem.isOngoing()) {
          setProfilingSession(sessionItem.getSession());
        }
        setSessionInternal(sessionItem.getSession());
      }
      final SessionItem item = sessionItem;
      sessionArtifacts.add(item);
      List<SessionArtifact<?>> artifacts = new ArrayList<>();
      myArtifactsFetchers.forEach(fetcher -> artifacts.addAll(fetcher.fetch(myProfilers, item.getSession(), item.getSessionMetaData())));
      item.setChildArtifacts(artifacts);
      if (item.getSessionMetaData().getType() == Common.SessionMetaData.SessionType.FULL) {
        sessionArtifacts.addAll(artifacts);
      }
      Collections.sort(sessionArtifacts, ARTIFACT_COMPARATOR);
    });

    // Trigger artifact updates.
    List newArtifactProtos = ContainerUtil.map(sessionArtifacts, artifact -> artifact.getArtifactProto());
    if (!previousArtifactProtos.equals(newArtifactProtos)) {
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().unregister(artifact));
      mySessionArtifacts = sessionArtifacts;
      changed(SessionAspect.SESSIONS);
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().register(artifact));

      registerImplicitlySelectedArtifactProto(mySessionArtifacts, previousArtifactProtos);
    }
  }

  /**
   * Attempt to register the implicit selection of newly added
   * artifacts done by the UI. These registered selections prevent reparsing
   * on reselection of an artifact.
   */
  private void registerImplicitlySelectedArtifactProto(List<SessionArtifact> sessionArtifacts,
                                                     List<GeneratedMessageV3> previousArtifactProtos) {
    // Get the newly added artifacts based off their backing proto
    SessionArtifact[] newlyAddedArtifacts =
      (sessionArtifacts.stream().filter(i -> !previousArtifactProtos.contains(i.getArtifactProto()))).toArray(SessionArtifact[]::new);
    if (newlyAddedArtifacts.length >= 1) {
      // Registers the implicit UI selections of a newly started session,
      // most recently recording's generated artifact (non-imported or session container),
      // or the first displayed imported session.
      SessionArtifact artifact = newlyAddedArtifacts[0];
      boolean onlyOneArtifactIsNew = newlyAddedArtifacts.length == 1;

      // User started new session
      if (onlyOneArtifactIsNew && artifact.isTopLevelArtifact() && artifact.isOngoing() ||
          // User recording/capture ends and artifact is generated, and is non-api initiated.
          // Api-initiated selections are not registered here because in most cases the profiler
          // does not jump to the captured trace in the UI. If we registered it here as the selected
          // artifact, then in these aforementioned cases, the ui and state of selection would be out
          // of sync. Thus, we only register the selection of an api-initiated trace when the ui
          // transitions to the capture stage in CpuProfilerStage's InProgressTraceHandler.
          onlyOneArtifactIsNew && !artifact.isTopLevelArtifact() && !artifact.isOngoing() && !artifact.isInitiatedByApi() ||
          // User's current session ends or user is importing session(s)
          // Also checks to make sure that the current selection is not a child
          // artifact of ending session. This avoids the selection being
          // overriden by its parent session on session end.
          artifact.isTopLevelArtifact() && !artifact.isOngoing() &&
          ((SessionItem)artifact).getChildArtifacts().stream().noneMatch(x -> x.getArtifactProto().equals(getSelectedArtifactProto()))
      ) {
        registerSelectedArtifactProto(artifact.getArtifactProto());
      }
    }
  }

  /**
   * Create a {@link Common.Session}, {@link Common.SessionMetaData}, and {@link SessionItem} for a given event with
   * {@link Common.SessionData.SessionStarted} data.
   */
  private SessionItem processSessionStarted(Event event) {
    SessionData.SessionStarted sessionData = event.getSession().getSessionStarted();
    Common.Session session = Common.Session.newBuilder()
      .setSessionId(sessionData.getSessionId())
      .setPid(sessionData.getPid())
      .setStartTimestamp(event.getTimestamp())
      .setEndTimestamp(Long.MAX_VALUE)
      .setStreamId(sessionData.getStreamId())
      .build();
    Common.SessionMetaData metadata = Common.SessionMetaData.newBuilder()
      .setSessionId(session.getSessionId())
      .setType(Common.SessionMetaData.SessionType.forNumber(sessionData.getType().getNumber()))
      .setStartTimestampEpochMs(sessionData.getStartTimestampEpochMs())
      .setProcessAbi(sessionData.getProcessAbi())
      .setJvmtiEnabled(sessionData.getJvmtiEnabled())
      .setSessionName(sessionData.getSessionName())
      .build();
    SessionItem sessionItem = new SessionItem(myProfilers, session, metadata);
    mySessionItems.put(session.getSessionId(), sessionItem);
    mySessionMetaDatas.put(session.getSessionId(), metadata);
    return sessionItem;
  }

  /**
   * Select the session with the matching id if one exists.
   *
   * @return true if the session is successfully selected,  false otherwise.
   */
  public boolean setSessionById(long sessionId) {
    if (!mySessionItems.containsKey(sessionId)) {
      return false;
    }

    setSession(mySessionItems.get(sessionId).getSession());
    return true;
  }

  /**
   * Change the current selected session explicitly, such as when importing an old session or captured files, or the user manually navigate
   * to a different session via the sessions panel.
   * This has the effect of disabling the auto-process selection logic. Also see {@link StudioProfilers#setAutoProfilingEnabled(boolean)}.
   */
  public void setSession(@NotNull Common.Session session) {
    myProfilers.setAutoProfilingEnabled(false);
    setSessionInternal(session);
  }

  private void setSessionInternal(@NotNull Common.Session session) {
    if (session.equals(mySelectedSession)) {
      return;
    }

    assert Common.Session.getDefaultInstance().equals(session) ||
           (mySessionItems.containsKey(session.getSessionId()) && mySessionItems.get(session.getSessionId()).getSession().equals(session));

    // First cache the view range associated with the previous session.
    if (!Common.Session.getDefaultInstance().equals(mySelectedSession)) {
      mySessionViewRangeMap.put(mySelectedSession.getSessionId(), new Range(myProfilers.getTimeline().getViewRange()));
    }

    mySelectedSession = session;
    changed(SessionAspect.SELECTED_SESSION);
  }

  private void setProfilingSession(@NotNull Common.Session session) {
    if (session.equals(myProfilingSession)) {
      return;
    }

    myProfilingSession = session;
    changed(SessionAspect.PROFILING_SESSION);
  }

  public void registerSelectedArtifactProto(GeneratedMessageV3 selectedArtifactProto) {
    mySelectedArtifactProto = selectedArtifactProto;
  }

  public void resetSelectedArtifactProto() {
    registerSelectedArtifactProto(null);
  }

  public void beginSession(long streamId, @NotNull Common.Device device, @NotNull Common.Process process) {
    // We currently don't support more than one profiling session at a time.
    assert Common.Session.getDefaultInstance().equals(myProfilingSession);
    assert device.getState() == Device.State.ONLINE;
    assert process.getState() == Common.Process.State.ALIVE;

    assert streamId != 0;
    BeginSession.Builder requestBuilder = BeginSession.newBuilder()
      .setSessionName(buildSessionName(device, process))
      .setRequestTimeEpochMs(System.currentTimeMillis())
      .setProcessAbi(process.getAbiCpuArch());
    // Attach agent for advanced profiling if JVMTI is enabled and the process is debuggable
    if (device.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
        process.getExposureLevel() == Common.Process.ExposureLevel.DEBUGGABLE) {
      // If an agent has been previously attached, Perfd will only re-notify the existing agent of the updated grpc target instead
      // of re-attaching an agent. See ProfilerService::AttachAgent on the Perfd side for more details.
      requestBuilder.setJvmtiConfig(
        BeginSession.JvmtiConfig.newBuilder()
          .setAttachAgent(true)
          .setAgentLibFileName(String.format("libjvmtiagent_%s.so", process.getAbiCpuArch()))
          // TODO remove hard-coded path by sharing what's used in TransportFileManager
          .setAgentConfigPath("/data/local/tmp/perfd/agent.config")
          .setPackageName(process.getPackageName())
          .build());
    }

    Command command = Command.newBuilder()
      .setStreamId(streamId)
      .setPid(process.getPid())
      .setBeginSession(requestBuilder)
      .setType(Command.CommandType.BEGIN_SESSION)
      .build();
    myProfilers.getClient().executeAsync(command, myProfilers.getIdeServices().getPoolExecutor());
  }

  /**
   * Request to end the currently profiling session if there is one.
   */
  public void endCurrentSession() {
    if (Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      return;
    }
    Common.Session profilingSession = myProfilingSession;
    setProfilingSession(Common.Session.getDefaultInstance());

    Command command = Command.newBuilder()
      .setStreamId(profilingSession.getStreamId())
      .setPid(profilingSession.getPid())
      .setEndSession(EndSession.newBuilder().setSessionId(profilingSession.getSessionId()))
      .setType(Command.CommandType.END_SESSION)
      .build();
    myProfilers.getClient().executeAsync(command, myProfilers.getIdeServices().getPoolExecutor());
  }

  public void deleteSession(@NotNull Common.Session session) {
    assert mySessionItems.containsKey(session.getSessionId()) && mySessionItems.get(session.getSessionId()).getSession().equals(session);

    // Selected session can change after we stop profiling so caching the value first.
    boolean sessionIsSelectedSession = mySelectedSession.equals(session);
    if (myProfilingSession.equals(session)) {
      // Route to StudioProfiler to set a null device + process, which will stop the session properly.
      myProfilers.setProcess(null, null);
    }

    // When deleting a currently selected session, set the session back to default so the profilers will go to the null stage.
    if (sessionIsSelectedSession) {
      setSessionInternal(Common.Session.getDefaultInstance());
    }

    Transport.DeleteEventsRequest deleteRequest = Transport.DeleteEventsRequest.newBuilder()
      .setStreamId(session.getStreamId())
      .setPid(session.getPid())
      .setGroupId(session.getSessionId())
      .setKind(Event.Kind.SESSION)
      .setFromTimestamp(session.getStartTimestamp())
      .setToTimestamp(session.getEndTimestamp())
      .build();
    // TODO(b/150503095)
    Transport.DeleteEventsResponse response = myProfilers.getClient().getTransportClient().deleteEvents(deleteRequest);

    // TODO b/141261422 the main update loop does not handle removing items at the moment. For now we manually remove the SessionItem and
    // force an update so any artifacts (e.g. heap dump, cpu captures) are also removed from being displayed.
    mySessionItems.remove(session.getSessionId());
    updateSessionItems(Collections.emptyList());
  }

  /**
   * @return the EventStreamServer corresponding to a particular stream id.
   */
  @Nullable
  public EventStreamServer getEventStreamServer(long streamId) {
    return myStreamIdToStreamServerMap.get(streamId);
  }

  /**
   * Create and a new session with a specific type. Note that this function will generate the corresponding session begin and end event
   * pair, so the caller does not have to include those into the input events list.
   *
   * @param startTimestampEpochMs epoch timestamp of the session - this is used for ordering in the sessions panel.
   * @param byteCacheMap          the byte cache for the session.
   * @param events                the list of events which can be queried for the session.
   */
  public void createImportedSession(@NotNull String sessionName,
                                    @NotNull SessionData.SessionStarted.SessionType sessionType,
                                    long startTimestampNs,
                                    long endTimestampNs,
                                    long startTimestampEpochMs,
                                    Map<String, ByteString> byteCacheMap,
                                    Common.Event... events) {
    EventStreamServer streamServer = new EventStreamServer(Long.toString(startTimestampEpochMs));
    try {
      streamServer.start();
    }
    catch (IOException exception) {
      getLogger().error(String.format("Failed to create a event server. Aborting import for session %s", sessionName));
      return;
    }
    Common.Stream stream = TransportService.getInstance().registerStreamServer(Common.Stream.Type.FILE, streamServer);
    myStreamIdToStreamServerMap.put(stream.getStreamId(), streamServer);
    streamServer.getByteCacheMap().putAll(byteCacheMap);
    BlockingDeque<Event> deque = streamServer.getEventDeque();
    for (Event event : events) {
      deque.offer(event);
    }
    // inserts the pair of Session begin + end events.
    deque.offer(Common.Event.newBuilder()
                  .setKind(Common.Event.Kind.SESSION)
                  .setGroupId(startTimestampNs)
                  .setTimestamp(startTimestampNs)
                  .setSession(Common.SessionData.newBuilder()
                                .setSessionStarted(Common.SessionData.SessionStarted.newBuilder()
                                                     .setStreamId(stream.getStreamId())
                                                     .setSessionId(startTimestampNs)
                                                     .setType(sessionType)
                                                     .setStartTimestampEpochMs(startTimestampEpochMs)
                                                     .setSessionName(sessionName)))
                  .build());
    deque.offer(Common.Event.newBuilder()
                  .setKind(Common.Event.Kind.SESSION)
                  .setGroupId(startTimestampNs)
                  .setTimestamp(endTimestampNs)
                  .setIsEnded(true)
                  .build());

    // New imported session will be auto selected once it is queried in the update loop.
  }

  /**
   * Register the import handler for a specific extension
   *
   * @param extension extension of the file
   * @param handler   handles the file imported
   */
  public void registerImportHandler(@NotNull String extension, @NotNull Consumer<File> handler) {
    myImportHandlers.put(extension, handler);
  }

  /**
   * Import session from file base on its extension
   *
   * @param file where the session is imported from
   * @return true if import was successful, or false otherwise.
   */
  public boolean importSessionFromFile(@NotNull File file) {
    int indexOfDot = file.getName().lastIndexOf('.');
    if (indexOfDot == -1) {
      return false;
    }
    String extension = StringUtil.toLowerCase(file.getName().substring(indexOfDot + 1));
    if (myImportHandlers.get(extension) == null) {
      return false;
    }
    myImportHandlers.get(extension).accept(file);
    return true;
  }

  /**
   * Update or add to the list of {@link SessionItem} based on the input list.
   *
   * @param sessions the list of {@link Common.Session} objects that have been added/updated.
   */
  private void updateSessionItems(@NotNull List<Common.Session> sessions) {
    List previousProtos = ContainerUtil.map(mySessionArtifacts, artifact -> artifact.getArtifactProto());

    // Note: we only add to a growing list of sessions at the moment.
    sessions.forEach(session -> {
      SessionItem sessionItem = mySessionItems.get(session.getSessionId());
      if (sessionItem != null) {
        sessionItem.setSession(session);
      }
    });

    List<SessionArtifact> sessionArtifacts = new ArrayList<>();
    for (SessionItem item : mySessionItems.values()) {
      sessionArtifacts.add(item);
      List<SessionArtifact<?>> artifacts = new ArrayList<>();
      myArtifactsFetchers.forEach(fetcher -> artifacts.addAll(fetcher.fetch(myProfilers, item.getSession(), item.getSessionMetaData())));
      item.setChildArtifacts(artifacts);
      if (item.getSessionMetaData().getType() == Common.SessionMetaData.SessionType.FULL) {
        sessionArtifacts.addAll(artifacts);
      }
    }
    Collections.sort(sessionArtifacts, ARTIFACT_COMPARATOR);

    List newProtos = ContainerUtil.map(sessionArtifacts, artifact -> artifact.getArtifactProto());
    if (!previousProtos.equals(newProtos)) {
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().unregister(artifact));
      mySessionArtifacts = sessionArtifacts;
      changed(SessionAspect.SESSIONS);
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().register(artifact));
    }
  }

  /**
   * Attempt to register selection of artifact (through its backing proto).
   * If attempted selection is already selected, return false indicating reselection
   * has occurred, otherwise return true.
   */
  public boolean selectArtifactProto(GeneratedMessageV3 artifactProto) {
    if (artifactProto.equals(getSelectedArtifactProto())) {
      return false;
    }

    // If it was not reselected, register this current selection
    registerSelectedArtifactProto(artifactProto);
    return true;
  }

  private static class SessionArtifactComparator implements Comparator<SessionArtifact> {
    @Override
    public int compare(SessionArtifact artifact1, SessionArtifact artifact2) {
      // More recent session should appear at the top.
      int result =
        Long.compare(artifact2.getSessionMetaData().getStartTimestampEpochMs(), artifact1.getSessionMetaData().getStartTimestampEpochMs());
      if (result != 0) {
        return result;
      }
      // Within a session: a) The session item itself always comes first
      if (artifact1 instanceof SessionItem) {
        return -1;
      }
      if (artifact2 instanceof SessionItem) {
        return 1;
      }

      // b) more recent artifacts should appear at the top.
      return Long.compare(artifact2.getTimestampNs(), artifact1.getTimestampNs());
    }
  }
}
