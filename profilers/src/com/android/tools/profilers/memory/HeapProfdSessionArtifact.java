/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class HeapProfdSessionArtifact extends MemorySessionArtifact<Memory.MemoryNativeSampleData> {
  public HeapProfdSessionArtifact(@NotNull StudioProfilers profilers,
                                  @NotNull Common.Session session,
                                  @NotNull Common.SessionMetaData sessionMetaData,
                                  @NotNull Memory.MemoryNativeSampleData info) {
    super(profilers, session, sessionMetaData, info, "Native Sampled");
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
    MemoryProfiler.saveHeapProfdSampleToFile(getProfilers().getClient(), getSession(), getArtifactProto(), outputStream);
    File symbols = new File(String.format(Locale.US, "%s%s%d.symbols", FileUtil.getTempDirectory(), File.separator, getStartTime()));
    if (symbols.exists()) {
      try {
        FileUtil.copy(new FileInputStream(symbols), outputStream);
      } catch (IOException ignored) {
        //  Failed to append symbols to end of export file.
      }
    }
  }

  public static List<SessionArtifact<?>> getSessionArtifacts(@NotNull StudioProfilers profilers,
                                                          @NotNull Common.Session session,
                                                          @NotNull Common.SessionMetaData sessionMetaData) {
    Range queryRangeUs = new Range(TimeUnit.NANOSECONDS.toMicros(session.getStartTimestamp()),
                                   session.getEndTimestamp() == Long.MAX_VALUE
                                   ? Long.MAX_VALUE
                                   : TimeUnit.NANOSECONDS.toMicros(session.getEndTimestamp()));
    List<Memory.MemoryNativeSampleData> infos =
      MemoryProfiler.getNativeHeapSamplesForSession(profilers.getClient(), session, queryRangeUs);
    return ContainerUtil.map(infos, info -> new HeapProfdSessionArtifact(profilers, session, sessionMetaData, info));
  }
}
