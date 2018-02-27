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
import com.android.tools.profilers.sessions.SessionArtifactView;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * A {@link SessionArtifactView} that represents a heap dump object.
 */
public final class HprofArtifactView extends SessionArtifactView<HprofSessionArtifact> {

  @NotNull private final JPanel myComponent;

  public HprofArtifactView(@NotNull ArtifactDrawInfo drawInfo, @NotNull HprofSessionArtifact artifact) {
    super(drawInfo, artifact);

    // 1st column reserved for expand-collapse row, 2nd column for artifact's icon
    // 1st row for showing name, second row for time.
    myComponent = new JPanel(new TabularLayout("Fit,Fit,*", "Fit,Fit"));
    myComponent.setBorder(isSessionSelected() ?
                          BorderFactory.createCompoundBorder(SELECTED_BORDER, ARTIFACT_PADDING) :
                          BorderFactory.createCompoundBorder(UNSELECTED_BORDER, ARTIFACT_PADDING));

    JComponent spacer = new Box.Filler(new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                       new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                       new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, Short.MAX_VALUE));
    myComponent.add(spacer, new TabularLayout.Constraint(0, 0));

    JLabel icon = new JLabel(StudioIcons.Profiler.Sessions.CPU);
    icon.setBorder(ARTIFACT_ICON_BORDER);
    myComponent.add(icon, new TabularLayout.Constraint(0, 1));

    JLabel artifactName = new JLabel(getArtifact().getName());
    artifactName.setBorder(ARTIFACT_PADDING);
    artifactName.setFont(ARTIFACT_TITLE_FONT);
    JLabel artifactTime =
      new JLabel(TimeAxisFormatter.DEFAULT.getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(getArtifact().getTimestampNs())));
    artifactTime.setBorder(ARTIFACT_PADDING);
    artifactTime.setFont(ARTIFACT_STATUS_FONT);
    myComponent.add(artifactName, new TabularLayout.Constraint(0, 2));
    myComponent.add(artifactTime, new TabularLayout.Constraint(1, 2));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
