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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

/**
 * A model corresponding to a {@link Common.Session}.
 */
public class SessionItem implements SessionArtifact {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  private boolean myIsExpanded;
  private boolean myCanExpand;

  public SessionItem(@NotNull StudioProfilers profilers, @NotNull Common.Session session) {
    myProfilers = profilers;
    mySession = session;
    Profiler.GetSessionMetaDataResponse response = myProfilers.getClient().getProfilerClient()
      .getSessionMetaData(Profiler.GetSessionMetaDataRequest.newBuilder().setSessionId(mySession.getSessionId()).build());
    mySessionMetaData = response.getData();
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

  @Override
  @NotNull
  public String getName() {
    return mySessionMetaData.getSessionName();
  }

  @Override
  public long getTimestampNs() {
    return 0;
  }

  /**
   * Update the {@link Common.Session} object. Note that while the content within the session can change, the new session instance should
   * correspond to the same one as identified by the session's id.
   */
  public void setSession(@NotNull Common.Session session) {
    assert session.getSessionId() == mySession.getSessionId();
    mySession = session;
  }

  public boolean isExpanded() {
    return myIsExpanded;
  }

  public void setExpanded(boolean expanded) {
    if (myIsExpanded == expanded) {
      return;
    }

    myIsExpanded = expanded;
    /**
     * The SessionsManager needs to retrieve the additional items that should be included in list as returned via
     * {@link SessionsManager#getSessionArtifacts()}.
     */
    myProfilers.getSessionsManager().update();
  }

  public boolean canExpand() {
    return myCanExpand;
  }

  /**
   * Sets whether this {@link SessionItem} can be expanded (e.g. it has additional artifacts to show}. Note that setting this to false
   * does not auto collapse the {@link SessionItem} if it is already expanded. This allows the instance to keep the expansion state if
   * artifacts are removed and new ones are added again.
   */
  public void setCanExpand(boolean canExpand) {
    myCanExpand = canExpand;
  }

  @Override
  public void onSelect() {
    myProfilers.getSessionsManager().setSession(mySession);
  }
}
