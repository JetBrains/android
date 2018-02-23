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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler.*;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.profilers.StudioProfilers.buildSessionName;

/**
 * A wrapper class for keeping track of the list of sessions that the profilers have seen, along with their associated artifacts (e.g.
 * memory heap dump, CPU capture)
 */
public class SessionsManager extends AspectModel<SessionAspect> {

  @NotNull private final StudioProfilers myProfilers;

  /**
   * A map of Session's Id -> {@link SessionItem}
   */
  @NotNull private Map<Long, SessionItem> mySessionItems;

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

  public SessionsManager(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    mySelectedSession = myProfilingSession = Common.Session.getDefaultInstance();
    mySessionItems = new HashMap<>();
    mySessionArtifacts = new ArrayList<>();
  }

  @NotNull
  public Common.Session getSelectedSession() {
    return mySelectedSession;
  }

  @NotNull
  public Common.Session getProfilingSession() {
    return myProfilingSession;
  }

  @NotNull
  public List<SessionArtifact> getSessionArtifacts() {
    return mySessionArtifacts;
  }

  public boolean isSessionAlive() {
    return mySelectedSession.getEndTimestamp() == Long.MAX_VALUE;
  }

  /**
   * Perform an update to retrieve all session instances.
   */
  public void update() {
    GetSessionsResponse sessionsResponse = myProfilers.getClient().getProfilerClient().getSessions(GetSessionsRequest.getDefaultInstance());
    updateSessionItems(sessionsResponse.getSessionsList());
  }

  /**
   * Change the current selected session.
   */
  public void setSession(@NotNull Common.Session session) {
    if (session.equals(mySelectedSession)) {
      return;
    }

    assert Common.Session.getDefaultInstance().equals(session) ||
           (mySessionItems.containsKey(session.getSessionId()) && mySessionItems.get(session.getSessionId()).getSession().equals(session));

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
  public void beginSession(@Nullable Common.Device device, @Nullable Common.Process process) {
    // We currently don't support more than one profiling session at a time.
    assert Common.Session.getDefaultInstance().equals(myProfilingSession);

    if (device == null || process == null) {
      setProfilingSession(Common.Session.getDefaultInstance());
      setSession(myProfilingSession);
      return;
    }

    // TODO this part is currently only for backward compatibility
    // Once we switched to the new device+process dropdown (b/67509466), we should not see offline device and process anymore.
    if (device.getState() != Common.Device.State.ONLINE || process.getState() != Common.Process.State.ALIVE) {
      return;
    }

    BeginSessionRequest.Builder requestBuilder = BeginSessionRequest.newBuilder()
      .setDeviceId(device.getDeviceId())
      .setPid(process.getPid())
      .setSessionName(buildSessionName(device, process))
      .setRequestTimeEpochMs(System.currentTimeMillis());
    // Attach agent for advanced profiling if JVMTI is enabled
    if (device.getFeatureLevel() >= AndroidVersion.VersionCodes.O &&
        myProfilers.getIdeServices().getFeatureConfig().isJvmtiAgentEnabled()) {
      // If an agent has been previously attached, Perfd will only re-notify the existing agent of the updated grpc target instead
      // of re-attaching an agent. See ProfilerService::AttachAgent on the Perfd side for more details.
      requestBuilder.setJvmtiConfig(BeginSessionRequest.JvmtiConfig.newBuilder()
                                      .setAttachAgent(true)
                                      .setAgentLibFileName(String.format("libperfa_%s.so", process.getAbiCpuArch()))
                                      .setLiveAllocationEnabled(myProfilers.getIdeServices().getFeatureConfig().isLiveAllocationsEnabled())
                                      .build());
    }

    BeginSessionResponse response = myProfilers.getClient().getProfilerClient().beginSession(requestBuilder.build());
    Common.Session session = response.getSession();

    setProfilingSession(session);
    updateSessionItems(Collections.singletonList(session));
    setSession(session);
  }

  /**
   * Request to end the currently profiling session if there is one.
   */
  public void endCurrentSession() {
    if (Common.Session.getDefaultInstance().equals(myProfilingSession)) {
      return;
    }

    EndSessionResponse response = myProfilers.getClient().getProfilerClient().endSession(EndSessionRequest.newBuilder()
                                                                                           .setDeviceId(myProfilingSession.getDeviceId())
                                                                                           .setSessionId(myProfilingSession.getSessionId())
                                                                                           .build());
    boolean selectedSessionIsProfilingSession = myProfilingSession.equals(mySelectedSession);
    setProfilingSession(Common.Session.getDefaultInstance());

    Common.Session session = response.getSession();
    updateSessionItems(Collections.singletonList(session));
    if (selectedSessionIsProfilingSession) {
      setSession(session);
    }
  }

  /**
   * Update or add to the list of {@link SessionItem} based on the input list.
   *
   * @param sessions the list of {@link Common.Session} objects that have been added/updated.
   */
  private void updateSessionItems(@NotNull List<Common.Session> sessions) {
    // Note: we only add to a growing list of sessions at the moment.
    sessions.forEach(session -> {
      SessionItem sessionItem = mySessionItems.get(session.getSessionId());
      if (sessionItem == null) {
        sessionItem = new SessionItem(myProfilers, session);
        mySessionItems.put(session.getSessionId(), sessionItem);
      }
      else {
        sessionItem.setSession(session);
      }
    });

    // TODO b/67509285 query for the artifacts (e.g. capture objects) associated with each SessionItem as well.
    mySessionArtifacts = new ArrayList<>(mySessionItems.values());
    Collections.sort(mySessionArtifacts, Comparator.comparingLong(SessionArtifact::getTimestampNs).reversed());
    changed(SessionAspect.SESSIONS);
  }
}
