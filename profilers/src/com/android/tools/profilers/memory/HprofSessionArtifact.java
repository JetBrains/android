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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    // TODO b\67509689 handle selection of the hprof capture.
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
