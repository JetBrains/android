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

  public SessionItem(@NotNull StudioProfilers profilers, @NotNull Common.Session session) {
    myProfilers = profilers;
    mySession = session;
    Profiler.GetSessionMetaDataResponse response = myProfilers.getClient().getProfilerClient()
      .getSessionMetaData(Profiler.GetSessionMetaDataRequest.newBuilder().setSessionId(mySession.getSessionId()).build());
    mySessionMetaData = response.getData();
  }

  @Override
  @NotNull
  public Common.Session getSession() {
    return mySession;
  }

  @Override
  @NotNull
  public String getName() {
    return mySessionMetaData.getSessionName();
  }

  @Override
  public long getTimestampNs() {
    return mySessionMetaData.getStartTimestampEpochMs();
  }

  /**
   * Update the {@link Common.Session} object. Note that while the content within the session can change, the new session instance should
   * correspond to the same one as identified by the session's id.
   */
  public void setSession(@NotNull Common.Session session) {
    assert session.getSessionId() == mySession.getSessionId();
    mySession = session;
  }

  @Override
  public void onSelect() {
    myProfilers.getSessionsManager().setSession(mySession);
  }
}
