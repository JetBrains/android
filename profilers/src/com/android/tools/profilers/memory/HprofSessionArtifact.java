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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An artifact representation of a memory heap dump.
 */
public final class HprofSessionArtifact implements SessionArtifact {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  @NotNull private final HeapDumpInfo myInfo;

  public HprofSessionArtifact(@NotNull StudioProfilers profilers,
                              @NotNull Common.Session session,
                              @NotNull Common.SessionMetaData sessionMetaData,
                              @NotNull HeapDumpInfo info) {
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
    return "Heap Dump";
  }

  @Override
  public long getTimestampNs() {
    return myInfo.getStartTime() - mySession.getStartTimestamp();
  }

  @Override
  public void onSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    // If memory profiler is not yet open, we need to do it.
    boolean needsToOpenMemoryProfiler = !(myProfilers.getStage() instanceof MemoryProfilerStage);
    if (needsToOpenMemoryProfiler) {
      myProfilers.setStage(new MemoryProfilerStage(myProfilers));
    }

    // Adjust the view range to fit the capture object.
    assert myProfilers.getStage() instanceof MemoryProfilerStage;
    MemoryProfilerStage stage = (MemoryProfilerStage)myProfilers.getStage();
    long startTimestamp = TimeUnit.NANOSECONDS.toMicros(myInfo.getStartTime());
    long endTimestamp = TimeUnit.NANOSECONDS.toMicros(myInfo.getEndTime());
    Range captureRange = new Range(startTimestamp, endTimestamp);
    myProfilers.getTimeline().adjustRangeCloseToMiddleView(captureRange);

    // Finally, we set and select the capture in the MemoryProfilerStage, which should be the current stage of StudioProfilers.
    ((MemoryProfilerStage)myProfilers.getStage()).getSelectionModel().set(captureRange.getMin(), captureRange.getMax());
    myProfilers.getIdeServices().getFeatureTracker().trackSessionArtifactSelected(this, myProfilers.getSessionsManager().isSessionAlive());
  }

  public static List<SessionArtifact> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    ListHeapDumpInfosResponse response = profilers.getClient().getMemoryClient()
      .listHeapDumpInfos(
        ListDumpInfosRequest.newBuilder().setSession(session).setStartTime(session.getStartTimestamp())
          .setEndTime(session.getEndTimestamp()).build());

    List<SessionArtifact> artifacts = new ArrayList<>();
    for (HeapDumpInfo info : response.getInfosList()) {
      artifacts.add(new HprofSessionArtifact(profilers, session, sessionMetaData, info));
    }

    return artifacts;
  }
}
