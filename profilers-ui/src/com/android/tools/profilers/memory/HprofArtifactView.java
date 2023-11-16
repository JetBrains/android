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

import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionArtifactView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.text.DateFormatUtil;
import icons.StudioIcons;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SessionArtifactView} that represents a heap dump object.
 */
public final class HprofArtifactView extends SessionArtifactView<HprofSessionArtifact> {

  public HprofArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull HprofSessionArtifact artifact) {
    super(artifactDrawInfo, artifact);
  }

  @Override
  @NotNull
  protected JComponent buildComponent() {
    var artifact = getArtifact();
    return buildCaptureArtifactView(artifact.getName(), getSubtitle(artifact), StudioIcons.Profiler.Sessions.HEAP, artifact.isOngoing());
  }

  @Override
  protected void exportArtifact() {
    assert !getArtifact().isOngoing();
    getSessionsView().getIdeProfilerComponents().createExportDialog().open(
      () -> "Export As",
      () -> MemoryProfiler.generateCaptureFileName(),
      () -> "hprof",
      file -> getSessionsView().getProfilers().getIdeServices().saveFile(file, outputStream -> getArtifact().export(outputStream), null));
  }

  @VisibleForTesting
  public static @NotNull String getSubtitle(MemorySessionArtifact<?> artifact) {
    if (artifact.getSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE) {
      return DateFormatUtil.formatDateTime(TimeUnit.NANOSECONDS.toMillis(artifact.getSession().getStartTimestamp()));
    }
    else if (artifact.isOngoing()) {
      return SessionArtifact.CAPTURING_SUBTITLE;
    }
    else {
      return TimeFormatter.getFullClockString(TimeUnit.NANOSECONDS.toMicros(artifact.getTimestampNs()));
    }
  }
}
