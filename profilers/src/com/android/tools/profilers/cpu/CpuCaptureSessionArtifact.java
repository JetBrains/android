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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoRequest;
import com.android.tools.profiler.proto.CpuProfiler.GetTraceInfoResponse;
import com.android.tools.profiler.proto.CpuProfiler.TraceInfo;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An artifact representation of a CPU capture.
 */
public class CpuCaptureSessionArtifact implements SessionArtifact {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  @NotNull private final TraceInfo myInfo;

  public CpuCaptureSessionArtifact(@NotNull StudioProfilers profilers,
                                   @NotNull Common.Session session,
                                   @NotNull Common.SessionMetaData sessionMetaData,
                                   @NotNull TraceInfo info) {
    myProfilers = profilers;
    mySession = session;
    mySessionMetaData = sessionMetaData;
    myInfo = info;
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

  @NotNull
  @Override
  public Common.SessionMetaData getSessionMetaData() {
    return mySessionMetaData;
  }

  @Override
  @NotNull
  public String getName() {
    return "CPU Trace";
  }

  @Override
  public long getTimestampNs() {
    return myInfo.getFromTimestamp() - mySession.getStartTimestamp();
  }

  @Override
  public void onSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    // If CPU profiler is not yet open, we need to do it.
    boolean needsToOpenCpuProfiler = !(myProfilers.getStage() instanceof CpuProfilerStage);
    if (needsToOpenCpuProfiler) {
      myProfilers.setStage(new CpuProfilerStage(myProfilers));
    }

    // Finally, we set and select the capture in the CpuProfilerStage, which should be the current stage of StudioProfilers.
    assert myProfilers.getStage() instanceof CpuProfilerStage;
    ((CpuProfilerStage)myProfilers.getStage()).setAndSelectCapture(myInfo.getTraceId());
  }

  public static List<SessionArtifact> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    // TODO b/74362035 this currently does not include ongoing traces, but we need to find a way to show it in the sessions panel.
    GetTraceInfoResponse response = profilers.getClient().getCpuClient().getTraceInfo(
      GetTraceInfoRequest.newBuilder().setSession(session).setFromTimestamp(session.getStartTimestamp())
        .setToTimestamp(session.getEndTimestamp()).build());

    List<SessionArtifact> artifacts = new ArrayList<>();
    for (TraceInfo info : response.getTraceInfoList()) {
      artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, info));
    }

    return artifacts;
  }
}