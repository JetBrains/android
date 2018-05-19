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

import com.android.tools.profilers.ProfilerAction;
import com.android.tools.profilers.sessions.SessionArtifactView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

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
    return buildCaptureArtifactView(getArtifact().getName(), getArtifact().getSubtitle(), StudioIcons.Profiler.Sessions.CPU,
                                    getArtifact().isOngoingCapture());
  }

  @Override
  @NotNull
  protected List<ContextMenuItem> getContextMenus() {
    ProfilerAction action = new ProfilerAction.Builder("Export...")
      .setEnableBooleanSupplier(() -> !getArtifact().isOngoingCapture())
      .setActionRunnable(() -> {
        getSessionsView().getIdeProfilerComponents().createExportDialog().open(
          () -> "Export As",
          () -> CpuProfiler.generateCaptureFileName(getArtifact().getArtifactProto().getProfilerType()),
          () -> "trace",
          file -> getArtifact().getProfilers().getIdeServices().saveFile(file, outputStream -> CpuProfiler
            .saveCaptureToFile(getArtifact().getArtifactProto(), outputStream), null));
      })
      .build();
    return Collections.singletonList(action);
  }
}