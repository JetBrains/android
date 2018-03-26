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
import com.android.tools.adtui.stdui.CommonButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

import static com.android.tools.profilers.ProfilerColors.*;

/**
 * A {@link SessionArtifactView} that represents a {@link com.android.tools.profiler.proto.Common.Session}
 */
public final class SessionItemView extends SessionArtifactView<SessionItem> {

  private static final Border DIVIDER_BORDER = JBUI.Borders.customLine(SESSION_DIVIDER_COLOR, 1, 0, 0, 0);
  private static final Border COMPONENT_PADDING = JBUI.Borders.empty(4, 9, 4, 4);
  private static final Font SESSION_TIME_FONT =
    TITLE_FONT.deriveFont(Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD));

  @NotNull private final JComponent myComponent;

  public SessionItemView(@NotNull ArtifactDrawInfo drawInfo, @NotNull SessionItem artifact) {
    super(drawInfo, artifact);

    // 1st column reserved for the Session's title (time), 2nd column for the live session icon.
    // 1st row for showing session start time, 2nd row for name, 3rd row for duration
    myComponent = new JPanel(new TabularLayout("Fit,Fit,*", "Fit,Fit,Fit"));
    if (isHovered()) {
      myComponent.setBackground(HOVERED_SESSION_COLOR);
    }
    Border selectionBorder = isSessionSelected() ?
                             JBUI.Borders.merge(SELECTED_BORDER, COMPONENT_PADDING, false) :
                             JBUI.Borders.merge(UNSELECTED_BORDER, COMPONENT_PADDING, false);
    // Skip the top border for the first entry as that would duplicate with the toolbar's border
    myComponent.setBorder(getIndex() == 0 ? selectionBorder : JBUI.Borders.merge(DIVIDER_BORDER, selectionBorder, false));

    // TODO b\73780379 add duration.
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    JLabel startTime = new JLabel(timeFormat.format(new Date(getArtifact().getSessionMetaData().getStartTimestampEpochMs())));
    startTime.setBorder(LABEL_PADDING);
    startTime.setFont(SESSION_TIME_FONT);
    myComponent.add(startTime, new TabularLayout.Constraint(0, 0));
    // Session is ongoing.
    if (getArtifact().getSession().getEndTimestamp() == Long.MAX_VALUE) {
      JPanel liveDotWrapper = new JPanel();
      liveDotWrapper.setOpaque(false);
      LiveSessionDot liveDot = new LiveSessionDot();
      liveDotWrapper.add(liveDot, BorderLayout.CENTER);
      myComponent.add(liveDotWrapper, new TabularLayout.Constraint(0, 1));
    }

    JLabel sessionName = new JLabel(getArtifact().getName());
    sessionName.setBorder(LABEL_PADDING);
    sessionName.setFont(STATUS_FONT);
    myComponent.add(sessionName, new TabularLayout.Constraint(1, 0, 1, 3));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * A component for rendering a green dot in {@link SessionItemView} to indicate that the session is ongoing.
   */
  private static class LiveSessionDot extends JComponent {

    private static final int SIZE = JBUI.scale(10);
    private static final Dimension DIMENSION = new Dimension(SIZE, SIZE);

    @Override
    public Dimension getMinimumSize() {
      return DIMENSION;
    }

    @Override
    public Dimension getMaximumSize() {
      return DIMENSION;
    }

    @Override
    public Dimension getPreferredSize() {
      return DIMENSION;
    }

    @Override
    public void paint(Graphics g) {
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setColor(ACTIVE_SESSION_COLOR);
      g2d.fillOval(0, 0, SIZE, SIZE);
      g2d.dispose();
    }
  }
}
