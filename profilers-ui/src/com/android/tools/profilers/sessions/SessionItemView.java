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

import static com.android.tools.profilers.ProfilerColors.ACTIVE_SESSION_COLOR;
import static com.android.tools.profilers.ProfilerColors.HOVERED_SESSION_COLOR;

/**
 * A {@link SessionArtifactView} that represents a {@link com.android.tools.profiler.proto.Common.Session}
 */
public final class SessionItemView extends SessionArtifactView<SessionItem> {

  private static final Border COMPONENT_PADDING = JBUI.Borders.empty(4, 0);
  private static final Font SESSION_TIME_FONT =
    TITLE_FONT.deriveFont(Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD));

  @NotNull private final JComponent myComponent;
  @Nullable private JButton myExpandCollapseButton;

  public SessionItemView(@NotNull ArtifactDrawInfo drawInfo, @NotNull SessionItem artifact) {
    super(drawInfo, artifact);

    // 1st column reserved for expand-collapse row, 2nd column for the Session's title (time), 3rd column for the live session icon.
    // 1st row for showing session start time, 2nd row for name, 3rd row for duration
    myComponent = new JPanel(new TabularLayout("Fit,Fit,Fit,*", "Fit,Fit,Fit"));
    if (isHovered()) {
      myComponent.setBackground(HOVERED_SESSION_COLOR);
    }
    Border selectionBorder = isSessionSelected() ?
                             JBUI.Borders.merge(SELECTED_BORDER, COMPONENT_PADDING, false) :
                             JBUI.Borders.merge(UNSELECTED_BORDER, COMPONENT_PADDING, false);
    // Skip the top border for the first entry as that would duplicate with the toolbar's border
    myComponent
      .setBorder(getIndex() == 0 ? selectionBorder : JBUI.Borders.merge(AdtUiUtils.DEFAULT_TOP_BORDER, selectionBorder, false));

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

    // TODO b\73780379 add duration.
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    JLabel startTime = new JLabel(timeFormat.format(new Date(getArtifact().getSessionMetaData().getStartTimestampEpochMs())));
    startTime.setBorder(LABEL_PADDING);
    startTime.setFont(SESSION_TIME_FONT);
    myComponent.add(startTime, new TabularLayout.Constraint(0, 1));
    // Session is ongoing.
    if (getArtifact().getSession().getEndTimestamp() == Long.MAX_VALUE) {
      JPanel liveDotWrapper = new JPanel();
      liveDotWrapper.setOpaque(false);
      LiveSessionDot liveDot = new LiveSessionDot();
      liveDotWrapper.add(liveDot, BorderLayout.CENTER);
      myComponent.add(liveDotWrapper, new TabularLayout.Constraint(0, 2));
    }

    JLabel sessionName = new JLabel(getArtifact().getName());
    sessionName.setBorder(LABEL_PADDING);
    sessionName.setFont(TITLE_FONT);
    myComponent.add(sessionName, new TabularLayout.Constraint(1, 1, 1, 3));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void handleMouseEvent(@NotNull MouseEvent event) {
    if (myExpandCollapseButton != null && (myExpandCollapseButton.contains(event.getPoint()) || event.getClickCount() > 1)) {
      myExpandCollapseButton.doClick();
      return;
    }

    // The artifact is responsible for selecting the session.
    getArtifact().onSelect();
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
