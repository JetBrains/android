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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.sessions.SessionArtifactView;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SessionArtifactView} that represents a CPU capture object.
 */
public class CpuCaptureArtifactView extends SessionArtifactView<CpuCaptureSessionArtifact> {

  public CpuCaptureArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull CpuCaptureSessionArtifact artifact) {
    super(artifactDrawInfo, artifact);

    // 1st column for artifact's icon, 2nd column for texts
    // 1st row for showing name, second row for time.
    setLayout(new TabularLayout("Fit-,*", "Fit-,Fit-"));

    JLabel icon = new JLabel(artifact.isOngoingCapture()
                             // TODO(b/74975946): use proper icon for in-progress captures. Maybe animate.
                             ? StudioIcons.LayoutEditor.Palette.PROGRESS_BAR
                             : StudioIcons.Profiler.Sessions.CPU);
    icon.setBorder(ARTIFACT_ICON_BORDER);
    add(icon, new TabularLayout.Constraint(0, 0));

    JLabel artifactName = new JLabel(getArtifact().getName());
    artifactName.setBorder(LABEL_PADDING);
    artifactName.setFont(TITLE_FONT);
    JLabel artifactTime =
      new JLabel(TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(getArtifact().getTimestampNs())));
    artifactTime.setBorder(LABEL_PADDING);
    artifactTime.setFont(STATUS_FONT);
    add(artifactName, new TabularLayout.Constraint(0, 1));
    add(artifactTime, new TabularLayout.Constraint(1, 1));
  }

  @Override
  protected void selectedSessionChanged() {
    setBorder(isSessionSelected() ?
              BorderFactory.createCompoundBorder(SELECTED_BORDER, ARTIFACT_PADDING) :
              BorderFactory.createCompoundBorder(UNSELECTED_BORDER, ARTIFACT_PADDING));
  }
}