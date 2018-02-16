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
package com.android.tools.profilers;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Render for a {@link com.android.tools.profiler.proto.Common.Session}.
 */
public final class SessionItemRenderer implements SessionArtifactRenderer<SessionItem> {

  @Override
  public JComponent generateComponent(@NotNull JList<SessionArtifact> list,
                                      @NotNull SessionItem item,
                                      int index,
                                      boolean selected,
                                      boolean hasFocus) {
    // 1st column reserved for expand-collapse row
    // 1st row for showing session start time, 2nd row for name, 3rd row for duration
    JPanel panel = new JPanel(new TabularLayout("Fit,*", "Fit,Fit,Fit"));
    Border selectionBorder = selected ?
                             BorderFactory.createMatteBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0, JBColor.blue) :
                             BorderFactory.createEmptyBorder(0, SESSION_HIGHLIGHT_WIDTH, 0, 0);

    // Skip the top border for the first entry as that would duplicate with the toolbar's border
    panel.setBorder(index == 0 ? selectionBorder : BorderFactory.createCompoundBorder(AdtUiUtils.DEFAULT_TOP_BORDER, selectionBorder));

    // TODO b\73780379 replace filler with expand/collapse button
    JComponent spacer = new Box.Filler(new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                       new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, 0),
                                       new Dimension(EXPAND_COLLAPSE_COLUMN_WIDTH, Short.MAX_VALUE));
    panel.add(spacer, new TabularLayout.Constraint(0, 0));

    // TODO b\73780379 proper formatting needed.
    // TODO b\73780379 add duration.
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    JLabel startTime = new JLabel(timeFormat.format(new Date(item.getTimestampNs())));
    startTime.setBorder(SESSION_TIME_PADDING);
    startTime.setFont(SESSION_TIME_FONT);
    JLabel sessionName = new JLabel(item.getName());
    sessionName.setBorder(SESSION_INFO_PADDING);
    sessionName.setFont(SESSION_INFO_FONT);
    panel.add(startTime, new TabularLayout.Constraint(0, 1));
    panel.add(sessionName, new TabularLayout.Constraint(1, 1));

    return panel;
  }
}
