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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.sessions.SessionArtifactView;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerColors.HOVERED_SESSION_COLOR;

/**
 * A {@link SessionArtifactView} that represents a heap dump object.
 */
public final class HprofArtifactView extends SessionArtifactView<HprofSessionArtifact> {

  @NotNull private final JPanel myComponent;

  public HprofArtifactView(@NotNull ArtifactDrawInfo drawInfo, @NotNull HprofSessionArtifact artifact) {
    super(drawInfo, artifact);

    // 1st column for artifact's icon, 2nd column for texts
    // 1st row for showing name, 2nd row for time.
    myComponent = new JPanel(new TabularLayout("Fit,*", "Fit,Fit"));
    if (isHovered()) {
      myComponent.setBackground(HOVERED_SESSION_COLOR);
    }
    myComponent.setBorder(isSessionSelected() ?
                          BorderFactory.createCompoundBorder(SELECTED_BORDER, ARTIFACT_PADDING) :
                          BorderFactory.createCompoundBorder(UNSELECTED_BORDER, ARTIFACT_PADDING));

    JLabel icon = new JLabel(StudioIcons.Profiler.Sessions.HEAP);
    icon.setBorder(ARTIFACT_ICON_BORDER);
    myComponent.add(icon, new TabularLayout.Constraint(0, 0));

    JLabel artifactName = new JLabel(getArtifact().getName());
    artifactName.setBorder(LABEL_PADDING);
    artifactName.setFont(TITLE_FONT);

    JLabel artifactTime;
    if (getArtifact().getSessionMetaData().getType() == Common.SessionMetaData.SessionType.MEMORY_CAPTURE) {
      DateFormat timeFormat = new SimpleDateFormat("MM/dd/yyyy, hh:mm a");
      artifactTime = new JLabel(timeFormat.format(new Date(TimeUnit.NANOSECONDS.toMillis(getArtifact().getSession().getStartTimestamp()))));
    }
    else {
      artifactTime =
        new JLabel(TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(getArtifact().getTimestampNs())));
    }
    artifactTime.setBorder(LABEL_PADDING);
    artifactTime.setFont(STATUS_FONT);
    myComponent.add(artifactName, new TabularLayout.Constraint(0, 1));
    myComponent.add(artifactTime, new TabularLayout.Constraint(1, 1));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
