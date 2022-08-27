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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * An artifact representation of a CPU capture.
 */
public class CpuCaptureSessionArtifact implements SessionArtifact<Cpu.CpuTraceInfo> {

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Common.Session mySession;
  @NotNull private final Common.SessionMetaData mySessionMetaData;
  @NotNull private final Cpu.CpuTraceInfo myInfo;
  private final boolean myIsOngoingCapture;

  public CpuCaptureSessionArtifact(@NotNull StudioProfilers profilers,
                                   @NotNull Common.Session session,
                                   @NotNull Common.SessionMetaData sessionMetaData,
                                   @NotNull Cpu.CpuTraceInfo info) {
    myProfilers = profilers;
    mySession = session;
    mySessionMetaData = sessionMetaData;
    myInfo = info;
    myIsOngoingCapture = info.getToTimestamp() == -1;
  }

  @NotNull
  @Override
  public Cpu.CpuTraceInfo getArtifactProto() {
    return myInfo;
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
    Cpu.CpuTraceConfiguration.UserOptions options = myInfo.getConfiguration().getUserOptions();
    return ProfilingTechnology.fromTypeAndMode(options.getTraceType(), options.getTraceMode()).getName();
  }

  public String getSubtitle() {
    if (myIsOngoingCapture) {
      return CAPTURING_SUBTITLE;
    }
    else if (isImportedSession()) {
      // For imported sessions, we show the time the file was imported, as it doesn't make sense to show the capture start time within the
      // session, which is always going to be 00:00:00
      return TimeFormatter.getLocalizedDateTime(TimeUnit.NANOSECONDS.toMillis(mySession.getStartTimestamp()));
    }
    else {
      // Otherwise, we show the formatted timestamp of the capture relative to the session start time.
      return TimeFormatter.getFullClockString(TimeUnit.NANOSECONDS.toMicros(getTimestampNs()));
    }
  }

  @Override
  public long getTimestampNs() {
    // For imported traces, we only have an artifact and it should be aligned with session's start time.
    if (isImportedSession()) {
      return 0;
    }
    // Otherwise, calculate the relative timestamp of the capture
    return myInfo.getFromTimestamp() - mySession.getStartTimestamp();
  }

  @Override
  public void doSelect() {
    // If the capture selected is not part of the currently selected session, we need to select the session containing the capture.
    boolean needsToChangeSession = mySession != myProfilers.getSession();
    if (needsToChangeSession) {
      myProfilers.getSessionsManager().setSession(mySession);
    }

    if (isImportedSession()) {
      // Sessions created from imported traces handle its selection callback via a session change listener, so we just return early here.
      return;
    }

    // If CPU profiler is not yet open, we need to do it.
    boolean needsToOpenCpuProfiler = !(myProfilers.getStage() instanceof CpuProfilerStage);
    if (needsToOpenCpuProfiler) {
      myProfilers.setStage(new CpuProfilerStage(myProfilers));
    }

    // If the capture is in progress we jump to its start range
    if (myIsOngoingCapture) {
      SessionArtifact.navigateTimelineToOngoingCapture(myProfilers.getTimeline(), TimeUnit.NANOSECONDS.toMicros(myInfo.getFromTimestamp()));
    }
    // Otherwise, we set and select the capture in the CpuProfilerStage
    else {
      assert myProfilers.getStage() instanceof CpuProfilerStage;
      ((CpuProfilerStage)myProfilers.getStage()).setAndSelectCapture(myInfo.getTraceId());
    }

    myProfilers.getIdeServices().getFeatureTracker()
      .trackSessionArtifactSelected(this, myProfilers.getSessionsManager().isSessionAlive());
  }

  @Override
  public boolean isOngoing() {
    return myIsOngoingCapture;
  }

  @Override
  public boolean getCanExport() {
    return !isOngoing();
  }

  @Override
  public void export(@NotNull OutputStream outputStream) {
    assert getCanExport();
    CpuProfiler.saveCaptureToFile(myProfilers, getArtifactProto(), outputStream);
  }

  private boolean isImportedSession() {
    return mySessionMetaData.getType() == Common.SessionMetaData.SessionType.CPU_CAPTURE;
  }

  public static List<SessionArtifact<?>> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                             @NotNull Common.Session session,
                                                             @NotNull Common.SessionMetaData sessionMetaData) {

    Range requestRange = new Range();
    if (sessionMetaData.getType() == Common.SessionMetaData.SessionType.FULL) {
      requestRange
        .set(TimeUnit.NANOSECONDS.toMicros(session.getStartTimestamp()), TimeUnit.NANOSECONDS.toMicros(session.getEndTimestamp()));
    }
    else {
      // We need to list imported traces and their timestamps might not be within the session range, so we search for max range.
      requestRange.set(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    // TODO b/133324501 handle the case where a CpuTraceInfo is still ongoing after a session has ended.
    List<Cpu.CpuTraceInfo> traceInfoList = CpuProfiler.getTraceInfoFromRange(profilers.getClient(), session, requestRange);
    List<SessionArtifact<?>> artifacts = new ArrayList<>();
    for (Cpu.CpuTraceInfo info : traceInfoList) {
      artifacts.add(new CpuCaptureSessionArtifact(profilers, session, sessionMetaData, info));
    }

    return artifacts;
  }
}