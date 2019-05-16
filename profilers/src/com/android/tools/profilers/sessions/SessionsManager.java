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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact;
import com.android.tools.profilers.memory.HprofSessionArtifact;
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.android.tools.profilers.StudioProfilers.buildSessionName;

/**
 * A wrapper class for keeping track of the list of sessions that the profilers have seen, along with their associated artifacts (e.g.
 * memory heap dump, CPU capture)
 */
public class SessionsManager extends AspectModel<SessionAspect> {
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
    List<SessionArtifact> fetch(@NotNull StudioProfilers profilers,
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
   * The session that is actively being profiled. Note that there can only be one profiling session at a time, but it does not have to the
   * one that is currently selected (e.g. Users can profile in the background while exploring other sessions history).
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

  private int importedSessionCount = 0;

  /**
   * A list of functions that should be called for each {@link Common.Session} for retrieving its data artifacts.
   */
  @NotNull
  private final List<ArtifactFetcher> myArtifactsFetchers;

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
    myArtifactsFetchers.add(LegacyAllocationsSessionArtifact::getSessionArtifacts);
    myArtifactsFetchers.add(CpuCaptureSessionArtifact::getSessionArtifacts);
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
    GetSessionsResponse sessionsResponse = myProfilers.getClient().getProfilerClient().getSessions(GetSessionsRequest.getDefaultInstance());
    updateSessionItems(sessionsResponse.getSessionsList());
  }

  /**
   * Change the current selected session explicitly, such as when importing an old session or caputre files, or the user manually navigate
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

  /**
   * Request to begin a new session using the input device and process.
   */
  public void beginSession(@NotNull Common.Device device, @Nullable Common.Process process) {
    // We currently don't support more than one profiling session at a time.
    assert Common.Session.getDefaultInstance().equals(myProfilingSession);

    // No process is specified, starts a default empty session.
    if (process == null) {
      setProfilingSession(Common.Session.getDefaultInstance());
      setSessionInternal(myProfilingSession);
      return;
    }

    // TODO(b/77649021): This part is currently only for backward compatibility.
    if (device.getState() != Common.Device.State.ONLINE || process.getState() != Common.Process.State.ALIVE) {
      return;
    }

    BeginSessionRequest.Builder requestBuilder = BeginSessionRequest.newBuilder()
                                                                    .setDeviceId(device.getDeviceId())
                                                                    .setPid(process.getPid())
                                                                    .setSessionName(buildSessionName(device, process))
                                                                    .setRequestTimeEpochMs(System.currentTimeMillis());
    // Attach agent for advanced profiling if JVMTI is enabled
    if (device.getFeatureLevel() >= AndroidVersion.VersionCodes.O) {
      // If an agent has been previously attached, Perfd will only re-notify the existing agent of the updated grpc target instead
      // of re-attaching an agent. See ProfilerService::AttachAgent on the Perfd side for more details.
      requestBuilder.setJvmtiConfig(BeginSessionRequest.JvmtiConfig.newBuilder()
                                                                   .setAttachAgent(true)
                                                                   .setAgentLibFileName(
                                                                     String.format("libperfa_%s.so", process.getAbiCpuArch()))
                                                                   .setLiveAllocationEnabled(myProfilers.getIdeServices().getFeatureConfig()
                                                                                                        .isLiveAllocationsEnabled())
                                                                   .build());
    }

    BeginSessionResponse response = myProfilers.getClient().getProfilerClient().beginSession(requestBuilder.build());
    Common.Session session = response.getSession();

    setProfilingSession(session);
    updateSessionItems(Collections.singletonList(session));
    setSessionInternal(session);
  }

  /**
   * Request to end the currently profiling session if there is one.
   */
  public void endCurrentSession() {
    if (Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      return;
    }

    EndSessionResponse response = myProfilers.getClient().getProfilerClient().endSession(EndSessionRequest.newBuilder()
                                                                                                          .setDeviceId(myProfilingSession
                                                                                                                         .getDeviceId())
                                                                                                          .setSessionId(myProfilingSession
                                                                                                                          .getSessionId())
                                                                                                          .build());
    boolean selectedSessionIsProfilingSession = myProfilingSession.equals(mySelectedSession);
    setProfilingSession(Common.Session.getDefaultInstance());

    Common.Session session = response.getSession();
    updateSessionItems(Collections.singletonList(session));
    if (selectedSessionIsProfilingSession) {
      setSessionInternal(session);
    }
  }

  public void deleteSession(@NotNull Common.Session session) {
    assert mySessionItems.containsKey(session.getSessionId()) && mySessionItems.get(session.getSessionId()).getSession().equals(session);

    // Selected session can change after we stop profiling so caching the value first.
    boolean sessionIsSelectedSession = mySelectedSession.equals(session);
    if (myProfilingSession.equals(session)) {
      // Route to StudioProfiler to set a null device, which will stop the session properly.
      myProfilers.setDevice(null);
    }

    // When deleting a currently selected session, set the session back to default so the profilers will go to the null stage.
    if (sessionIsSelectedSession) {
      setSessionInternal(Common.Session.getDefaultInstance());
    }

    DeleteSessionRequest request = DeleteSessionRequest.newBuilder().setSessionId(session.getSessionId()).build();
    myProfilers.getClient().getProfilerClient().deleteSession(request);
    mySessionItems.remove(session.getSessionId());
    updateSessionItems(Collections.emptyList());
  }

  /**
   * Create and a new session with a specific type
   *
   * @param sessionName name of the new session
   * @param sessionType type of the new session
   * @return the new session
   */
  @NotNull
  public Common.Session createImportedSession(@NotNull String sessionName,
                                              @NotNull Common.SessionMetaData.SessionType sessionType,
                                              long startTimestampNs,
                                              long endTimestampNs,
                                              long startTimestampEpochMs) {
    Common.Session session = Common.Session.newBuilder()
                                           .setSessionId(generateUniqueSessionId())
                                           .setStartTimestamp(startTimestampNs)
                                           .setEndTimestamp(endTimestampNs)
                                           .build();

    Profiler.ImportSessionRequest sessionRequest = Profiler.ImportSessionRequest.newBuilder()
                                                                                .setSession(session)
                                                                                .setSessionName(sessionName)
                                                                                .setSessionType(sessionType)
                                                                                .setStartTimestampEpochMs(startTimestampEpochMs)
                                                                                .build();
    myProfilers.getClient().getProfilerClient().importSession(sessionRequest);
    return session;
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
    int indexOfDot = file.getName().indexOf('.');
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
   * Return a unique Session ID
   */
  private int generateUniqueSessionId() {
    // TODO: b/74401257 generate session ID in a proper way
    return ++importedSessionCount;
  }

  /**
   * Update or add to the list of {@link SessionItem} based on the input list.
   *
   * @param sessions the list of {@link Common.Session} objects that have been added/updated.
   */
  private void updateSessionItems(@NotNull List<Common.Session> sessions) {
    List previousProtos = mySessionArtifacts.stream().map(artifact -> artifact.getArtifactProto()).collect(Collectors.toList());

    // Note: we only add to a growing list of sessions at the moment.
    sessions.forEach(session -> {
      SessionItem sessionItem = mySessionItems.get(session.getSessionId());
      if (sessionItem == null) {
        Profiler.GetSessionMetaDataResponse response = myProfilers.getClient().getProfilerClient()
                                                                  .getSessionMetaData(Profiler.GetSessionMetaDataRequest.newBuilder()
                                                                                                                        .setSessionId(
                                                                                                                          session
                                                                                                                            .getSessionId())
                                                                                                                        .build());
        Common.SessionMetaData metadata = response.getData();
        sessionItem = new SessionItem(myProfilers, session, metadata);

        mySessionItems.put(session.getSessionId(), sessionItem);
        mySessionMetaDatas.put(session.getSessionId(), metadata);
      }
      else {
        sessionItem.setSession(session);
      }
    });

    List<SessionArtifact> sessionArtifacts = new ArrayList<>();
    for (SessionItem item : mySessionItems.values()) {
      sessionArtifacts.add(item);
      List<SessionArtifact> artifacts = new ArrayList<>();
      myArtifactsFetchers.forEach(fetcher -> artifacts.addAll(fetcher.fetch(myProfilers, item.getSession(), item.getSessionMetaData())));
      item.setChildArtifacts(artifacts);
      if (item.getSessionMetaData().getType() == Common.SessionMetaData.SessionType.FULL) {
        sessionArtifacts.addAll(artifacts);
      }
    }
    Collections.sort(sessionArtifacts, ARTIFACT_COMPARATOR);

    List newProtos = sessionArtifacts.stream().map(artifact -> artifact.getArtifactProto()).collect(Collectors.toList());
    if (!previousProtos.equals(newProtos)) {
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().unregister(artifact));
      mySessionArtifacts = sessionArtifacts;
      changed(SessionAspect.SESSIONS);
      mySessionArtifacts.forEach(artifact -> myProfilers.getUpdater().register(artifact));
    }
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
