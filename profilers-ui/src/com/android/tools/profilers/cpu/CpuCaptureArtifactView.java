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

import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionArtifactView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.text.DateFormatUtil;
import icons.StudioIcons;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SessionArtifactView} that represents a CPU capture object.
 */
public class CpuCaptureArtifactView extends SessionArtifactView<CpuCaptureSessionArtifact> {

  public CpuCaptureArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull CpuCaptureSessionArtifact artifact) {
    super(artifactDrawInfo, artifact);
  }

  @Override
  @NotNull
  protected JComponent buildComponent() {
    var artifact = getArtifact();
    return buildCaptureArtifactView(artifact.getName(), getArtifactSubtitle(artifact), StudioIcons.Profiler.Sessions.CPU, artifact.isOngoing());
  }

  @Override
  protected void exportArtifact() {
    assert !getArtifact().isOngoing();
    getSessionsView().getIdeProfilerComponents().createExportDialog().open(
      () -> "Export As",
      () -> CpuProfiler.generateCaptureFileName(TraceType.from(getArtifact().getArtifactProto().getConfiguration())),
      () -> "trace",
      file -> getArtifact().getProfilers().getIdeServices().saveFile(file, outputStream -> getArtifact().export(outputStream), null));
  }

  @VisibleForTesting
  public static @NotNull String getArtifactSubtitle(CpuCaptureSessionArtifact artifact) {
    if (artifact.isOngoing()) {
      return SessionArtifact.CAPTURING_SUBTITLE;
    }
    else if (artifact.isImportedSession()) {
      // For imported sessions, we show the time the file was imported, as it doesn't make sense to show the capture start time within the
      // session, which is always going to be 00:00:00
      return DateFormatUtil.formatDateTime(TimeUnit.NANOSECONDS.toMillis(artifact.getSession().getStartTimestamp()));
    }
    else {
      // Otherwise, we show the formatted timestamp of the capture relative to the session start time.
      return TimeFormatter.getFullClockString(TimeUnit.NANOSECONDS.toMicros(artifact.getTimestampNs()));
    }
  }
}
