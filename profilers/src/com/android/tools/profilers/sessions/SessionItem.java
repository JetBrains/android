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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.StudioMonitorStage;
import com.android.tools.profilers.StudioProfilers;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * A model corresponding to a {@link Common.Session}.
 */
public class SessionItem extends AspectModel<SessionItem.Aspect> implements SessionArtifact<Common.Session> {

  public enum Aspect {
    MODEL,
  }

  private static final String SESSION_INITIALIZING = "Starting...";
  @VisibleForTesting static final String SESSION_LOADING = "Loading...";

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  private long myDurationNs;
  private boolean myWaitingForAgent;
  /**
   * The list of artifacts (e.g. cpu capture, hprof, etc) that belongs to this session.
   */
  @NotNull private List<SessionArtifact> myChildArtifacts = Collections.emptyList();

  public SessionItem(@NotNull StudioProfilers profilers, @NotNull Common.Session session, @NotNull Common.SessionMetaData metaData) {
    myProfilers = profilers;
    mySession = session;
    mySessionMetaData = metaData;

    if (!SessionsManager.isSessionAlive(mySession)) {
      myDurationNs = mySession.getEndTimestamp() - mySession.getStartTimestamp();
    }

    profilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
    agentStatusChanged();
  }

  @NotNull
  @Override
  public Common.Session getArtifactProto() {
    return mySession;
  }

  @NotNull
  @Override
  public StudioProfilers getProfilers() {
    return myProfilers;
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @Override
  @NotNull
  public Common.SessionMetaData getSessionMetaData() {
    return mySessionMetaData;
  }

  @VisibleForTesting
  @NotNull
  List<SessionArtifact> getChildArtifacts() {
    return myChildArtifacts;
  }

  @Override
  @NotNull
  public String getName() {
    String name = mySessionMetaData.getSessionName();
    if (mySessionMetaData.getType() != Common.SessionMetaData.SessionType.FULL) {
      return name;
    }

    // Everything before the first space is the app's name (the format is {APP_NAME (DEVICE_NAME)})
    int firstSpace = name.indexOf(' ');
    // We always expect the device name to exist
    assert firstSpace != -1;
    String appName = name.substring(0, firstSpace);
    int lastDot = appName.lastIndexOf('.');
    if (lastDot != -1) {
      // Strips the packages from the application name
      appName = appName.substring(lastDot + 1);
    }
    return appName + name.substring(firstSpace);
  }

  @NotNull
  public String getSubtitle() {
    if (mySessionMetaData.getType() != Common.SessionMetaData.SessionType.FULL) {
      if (!myChildArtifacts.isEmpty()) {
        assert myChildArtifacts.size() == 1;
        return myChildArtifacts.get(0).getName();
      }
      else {
        return SESSION_LOADING;
      }
    }

    if (myWaitingForAgent) {
      return SESSION_INITIALIZING;
    }
    else {
      long durationUs = TimeUnit.NANOSECONDS.toMicros(myDurationNs);
      return TimeFormatter.getMultiUnitDurationString(durationUs);
    }
  }

  @Override
  public long getTimestampNs() {
    return 0;
  }

  @Override
  public boolean isOngoing() {
    return SessionsManager.isSessionAlive(mySession);
  }

  /**
   * Update the {@link Common.Session} object. Note that while the content within the session can change, the new session instance should
   * correspond to the same one as identified by the session's id.
   */
  public void setSession(@NotNull Common.Session session) {
    assert session.getSessionId() == mySession.getSessionId();
    mySession = session;
  }

  public void setChildArtifacts(@NotNull List<SessionArtifact> childArtifacts) {
    myChildArtifacts = childArtifacts;
    changed(Aspect.MODEL);
  }

  @Override
  public void onSelect() {
    // Navigate to the new session
    myProfilers.getSessionsManager().setSession(mySession);
    if (mySessionMetaData.getType() == Common.SessionMetaData.SessionType.FULL &&
        !myProfilers.getStageClass().equals(StudioMonitorStage.class)) {
      myProfilers.setStage(new StudioMonitorStage(myProfilers));
    }
    myProfilers.getIdeServices().getFeatureTracker().trackSessionArtifactSelected(this, myProfilers.getSessionsManager().isSessionAlive());
  }

  @Override
  public void update(long elapsedNs) {
    if (SessionsManager.isSessionAlive(mySession)) {
      myDurationNs += elapsedNs;
      changed(Aspect.MODEL);
    }
  }

  private void agentStatusChanged() {
    boolean oldValue = myWaitingForAgent;
    if (SessionsManager.isSessionAlive(mySession) && mySession.equals(myProfilers.getSessionsManager().getSelectedSession())) {
      Common.AgentData agentData = myProfilers.getAgentData();
      myWaitingForAgent = agentData.getStatus() == Common.AgentData.Status.UNSPECIFIED;
    }
    else {
      myWaitingForAgent = false;
    }

    if (oldValue != myWaitingForAgent) {
      changed(Aspect.MODEL);
    }
  }

  public void deleteSession() {
    myProfilers.getSessionsManager().deleteSession(mySession);
  }
}
