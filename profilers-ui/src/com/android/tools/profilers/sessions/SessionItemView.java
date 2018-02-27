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
package com.android.tools.profilers.sessions;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.stdui.CommonButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A {@link SessionArtifactView} that represents a {@link com.android.tools.profiler.proto.Common.Session}
 */
public final class SessionItemView extends SessionArtifactView<SessionItem> {

  @NotNull private final JComponent myComponent;
  @Nullable private JButton myExpandCollapseButton;

  public SessionItemView(@NotNull ArtifactDrawInfo drawInfo, @NotNull SessionItem artifact) {
    super(drawInfo, artifact);

    // 1st column reserved for expand-collapse row
    // 1st row for showing session start time, 2nd row for name, 3rd row for duration
    myComponent = new JPanel(new TabularLayout("Fit,*", "Fit,Fit,Fit"));
    Border selectionBorder = isSessionSelected() ? SELECTED_BORDER : UNSELECTED_BORDER;
    // Skip the top border for the first entry as that would duplicate with the toolbar's border
    myComponent
      .setBorder(getIndex() == 0 ? selectionBorder : BorderFactory.createCompoundBorder(AdtUiUtils.DEFAULT_TOP_BORDER, selectionBorder));

    if (getArtifact().canExpand()) {
      myExpandCollapseButton = new CommonButton(getArtifact().isExpanded() ? COLLAPSE_ICON : EXPAND_ICON);
      myExpandCollapseButton.setBorder(EXPAND_ICON_BORDER);
      myExpandCollapseButton.addActionListener(e -> getArtifact().setExpanded(!getArtifact().isExpanded()));
      myComponent.add(myExpandCollapseButton, new TabularLayout.Constraint(0, 0));
    }
    else {
      JComponent spacer = new Box.Filler(new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                         new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                         new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, Short.MAX_VALUE));
      myComponent.add(spacer, new TabularLayout.Constraint(0, 0));
    }

    // TODO b\73780379 proper formatting needed.
    // TODO b\73780379 add duration.
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    JLabel startTime = new JLabel(timeFormat.format(new Date(getArtifact().getSessionMetaData().getStartTimestampEpochMs())));
    startTime.setBorder(SESSION_TIME_PADDING);
    startTime.setFont(SESSION_TIME_FONT);
    JLabel sessionName = new JLabel(getArtifact().getName());
    sessionName.setBorder(SESSION_INFO_PADDING);
    sessionName.setFont(SESSION_INFO_FONT);
    myComponent.add(startTime, new TabularLayout.Constraint(0, 1));
    myComponent.add(sessionName, new TabularLayout.Constraint(1, 1));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void handleClick(@NotNull Point point) {
    if (myExpandCollapseButton != null && myExpandCollapseButton.contains(point)) {
      myExpandCollapseButton.doClick();
      return;
    }

    // The artifact is responsible for selecting the session.
    getArtifact().onSelect();
  }
}
