/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.ui;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

public abstract class BaseSegment extends JComponent {

  /**
   * Top/bottom border between segments.
   */
  public static final Border SEGMENT_BORDER = new CompoundBorder(new MatteBorder(0, 0, 1, 0, AdtUiUtils.DEFAULT_BORDER_COLOR),
                                                                  new EmptyBorder(0, 0, 0, 0));

  /**
   * Padding for the header labels.
   */
  private static final Border LABEL_BORDER = BorderFactory.createEmptyBorder(4, 4, 4, 4);

  private static final int OVERLAY_CONTENT_WIDTH = 100;

  @NotNull
  private JBLayeredPane mLayeredPane;

  private JLabel mLeftLabel;

  private JPanel mLeftPanel;

  private JLabel mRightLabel;

  private JPanel mRightPanel;

  private JPanel mCenterPanel;

  @NotNull
  protected final String myName;

  @NotNull
  protected Range mXRange;

  @NotNull
  protected final EventDispatcher<ProfilerEventListener> mEventDispatcher;

  public BaseSegment(@NotNull String name, @NotNull Range xRange, @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    myName = name;
    mXRange = xRange;
    mEventDispatcher = dispatcher;
  }

  public static int getOverlayContentWidth() {
    return OVERLAY_CONTENT_WIDTH;
  }

  public void initializeComponents() {
    setLayout(new BorderLayout());

    // The layout of a segment is a 2x3 grid, with the main content spanning the entire second row. Optional content (left/right
    // axes) are layered on top of the main content in the first/third columns. The three columns in the first row are used to display
    // additional info associated with the left/main/right content respectively.
    JBPanel panel = new JBPanel();
    panel.setLayout(new GridBagLayout());

    // Setup the top center panel.
    JBPanel topPanel = new JBPanel();
    topPanel.setLayout(new BorderLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.weighty = 0;
    panel.add(topPanel, gbc);
    setTopCenterContent(topPanel);

    mLayeredPane = new JBLayeredPane();
    //Setup the left panel
    if (hasLeftContent()) {
      mLeftPanel = new JBPanel();
      mLeftPanel.setLayout(new BorderLayout());
      mLeftPanel.setOpaque(false);
      mLayeredPane.add(mLeftPanel);
      setLeftContent(mLeftPanel);

      mLeftLabel = new JBLabel(getLeftContentLabel());
      mLeftLabel.setBorder(LABEL_BORDER);
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weightx = 0;
      gbc.weighty = 0;
      panel.add(mLeftLabel, gbc);
    }

    //Setup the right panel
    if (hasRightContent()) {
      mRightPanel = new JBPanel();
      mRightPanel.setLayout(new BorderLayout());
      mRightPanel.setOpaque(false);
      mLayeredPane.add(mRightPanel);
      setRightContent(mRightPanel);

      mRightLabel = new JBLabel(getRightContentLabel());
      mRightLabel.setBorder(LABEL_BORDER);
      gbc.gridx = 2;
      gbc.gridy = 0;
      gbc.weightx = 0;
      gbc.weighty = 0;
      panel.add(mRightLabel, gbc);
    }

    //Setup the center panel, the primary component.
    mCenterPanel = new JBPanel();
    mCenterPanel.setLayout(new BorderLayout());
    mLayeredPane.add(mCenterPanel);
    setCenterContent(mCenterPanel);

    mLayeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            if (c == mLeftPanel) {
              c.setBounds(0, 0, OVERLAY_CONTENT_WIDTH, dim.height);
            }
            else if (c == mRightPanel) {
              c.setBounds(dim.width - OVERLAY_CONTENT_WIDTH, 0, OVERLAY_CONTENT_WIDTH, dim.height);
            }
            else if (c == mCenterPanel) {
              c.setBounds(0, 0, dim.width, dim.height);
            }
          }
        }
      }
    });

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 3;
    gbc.weightx = 1;
    gbc.weighty = 1;
    panel.add(mLayeredPane, gbc);
    add(panel, BorderLayout.CENTER);

    // By default, starts in L1, this gives the Segment a chance to determine what it should rendered.
    toggleView(false);
  }

  /**
   * This enables segments to toggle the visibility of the right panel.
   *
   * @param isVisible True indicates the panel is visible, false hides it.
   */
  public void setRightSpacerVisible(boolean isVisible) {
    if (hasRightContent()) {
      mRightPanel.setVisible(isVisible);
      mRightLabel.setVisible(isVisible);
    }
  }

  public void toggleView(boolean isExpanded) {
    setRightSpacerVisible(isExpanded);
  }

  public void createComponentsList(@NotNull List<Animatable> animatables) {}

  /**
   * A read-only flag that indicates whether this segment has left overlay content. If true, {@link #setLeftContent(JPanel)} will be
   * invoked. Subclasses of {@link BaseSegment} can override this to change how the segment is laid out and whether it will participate in
   * any transitions toggling between the overview and detailed view.
   */
  protected boolean hasLeftContent() {
    return true;
  }

  protected void setLeftContent(@NotNull JPanel panel) {}

  /**
   * Label for the left overlay content which is rendered at the top left corner of the segment.
   */
  protected String getLeftContentLabel() {
    return myName;
  }

  protected abstract void setCenterContent(@NotNull JPanel panel);

  /**
   * A read-only flag that indicates whether this segment has right overlay content. If true, {@link #setRightContent(JPanel)} will be
   * invoked. Subclasses of {@link BaseSegment} can override this to change how the segment is laid out and whether it will participate in
   * any transitions toggling between the overview and detailed view.
   */
  protected boolean hasRightContent() {
    return true;
  }

  protected void setRightContent(@NotNull JPanel panel) {}

  /**
   * Label for the right overlay content which is rendered at the top right corner of the segment. By default, subclasses need to override
   * this to return an non-empty string, and it is only used if {@link #hasRightContent()} returns true.
   */
  protected String getRightContentLabel() {
    return "";
  }

  protected void setTopCenterContent(@NotNull JPanel panel) {}
}
