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

import static com.android.tools.profilers.memory.MemoryProfiler.saveHeapDumpToFile;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.intellij.util.containers.ContainerUtil;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * An artifact representation of a memory heap dump.
 */
public final class HprofSessionArtifact extends MemorySessionArtifact<HeapDumpInfo> {


  public HprofSessionArtifact(@NotNull StudioProfilers profilers,
                              @NotNull Common.Session session,
                              @NotNull Common.SessionMetaData sessionMetaData,
                              @NotNull HeapDumpInfo info) {
    super(profilers, session, sessionMetaData, info, "Heap Dump");
  }

  @Override
  protected long getStartTime() {
    return getArtifactProto().getStartTime();
  }

  @Override
  protected long getEndTime() {
    return getArtifactProto().getEndTime();
  }

  @Override
  public void export(@NotNull OutputStream outputStream) {
    assert getCanExport();
    saveHeapDumpToFile(getProfilers().getClient(), getSession(), getArtifactProto(), outputStream,
                       getProfilers().getIdeServices().getFeatureTracker());
  }

  public static List<SessionArtifact<?>> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                             @NotNull Common.Session session,
                                                             @NotNull Common.SessionMetaData sessionMetaData) {
    Range queryRangeUs = new Range(TimeUnit.NANOSECONDS.toMicros(session.getStartTimestamp()),
                                   session.getEndTimestamp() == Long.MAX_VALUE
                                   ? Long.MAX_VALUE
                                   : TimeUnit.NANOSECONDS.toMicros(session.getEndTimestamp()));
    List<HeapDumpInfo> infos = MemoryProfiler.getHeapDumpsForSession(profilers.getClient(), session, queryRangeUs);
    return ContainerUtil.map(infos, info -> new HprofSessionArtifact(profilers, session, sessionMetaData, info));
  }

  @Override
  public void onSelect() {
    // Ignore if this heap dump is already loaded
    if (getProfilers().getStage() instanceof BaseMemoryProfilerStage) {
      CaptureObject loaded = ((BaseMemoryProfilerStage)getProfilers().getStage()).getCaptureSelection().getSelectedCapture();
      if (loaded != null && loaded.getStartTimeNs() == getStartTime()) {
        return;
      }
    }
    super.onSelect();
  }
}
