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
import com.android.tools.adtui.common.RotatedLabel;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profiler.proto.Common;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_LABEL_BORDER;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

/**
 * A collapsible panel which lets users see the list of and interact with their profiling sessions.
 */
public class SessionsView extends AspectObserver {
  // Collapsed width should essentially look like a toolbar.
  private static final int SESSIONS_COLLAPSED_MIN_WIDTH = JBUI.scale(20);
  private static final int SESSIONS_EXPANDED_MIN_WIDTH = JBUI.scale(200);

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final JComponent myComponent;
  @NotNull private final JButton myExpandButton;
  @NotNull private final JButton myCollapseButton;
  @NotNull private final JList<Common.Session> mySessionsList;
  @NotNull private final DefaultListModel<Common.Session> mySessionsListModel;
  private Common.Session mySelectedSession;

  private boolean myIsExpanded;

  public SessionsView(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
    // Starts out with a collapsed sessions panel.
    // TODO b\73159126 make this configurable. e.g. save user's previous settings.
    myIsExpanded = false;
    myComponent = new JPanel(new BorderLayout());
    myComponent.setBorder(AdtUiUtils.DEFAULT_RIGHT_BORDER);
    myExpandButton = new CommonButton();
    myExpandButton.setIcon(StudioIcons.Common.ZOOM_IN);
    myExpandButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsExpanded = true;
        initializeUI();
      }
    });
    myCollapseButton = new CommonButton();
    myCollapseButton.setIcon(StudioIcons.Common.ZOOM_OUT);
    myCollapseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsExpanded = false;
        initializeUI();
      }
    });

    mySessionsListModel = new DefaultListModel<>();
    mySessionsList = new JList<>(mySessionsListModel);
    mySessionsList.setCellRenderer(new SessionsCellRenderer());
    mySessionsList.setSelectionMode(SINGLE_SELECTION);
    myProfilers.addDependency(this).onChange(ProfilerAspect.SESSIONS, this::refreshSessions);
    initializeUI();
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  /**
   * @param listener Listener for when the sessions panel is expanded.
   */
  public void addExpandListener(@NotNull ActionListener listener) {
    myExpandButton.addActionListener(listener);
  }

  /**
   * @param listener Listener for when the sessions panel is collapsed.
   */
  public void addCollapseListener(@NotNull ActionListener listener) {
    myCollapseButton.addActionListener(listener);
  }

  private void initializeUI() {
    myComponent.removeAll();
    if (myIsExpanded) {
      myComponent.add(createToolbar(), BorderLayout.NORTH);
      myComponent.add(mySessionsList, BorderLayout.CENTER);
    }
    else {
      // We only need the toolbar when collapsed
      myComponent.add(createToolbar(), BorderLayout.CENTER);
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  @NotNull
  private JComponent createToolbar() {
    JPanel toolbar;
    if (myIsExpanded) {
      toolbar = new JPanel(new TabularLayout("*,Fit", "*,Fit,*"));
      toolbar.setBorder(DEFAULT_BOTTOM_BORDER);
      toolbar.setMinimumSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));
      toolbar.setPreferredSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));

      JLabel label = new JLabel("SESSIONS");
      label.setBorder(TOOLBAR_LABEL_BORDER);
      toolbar.add(label, new TabularLayout.Constraint(1, 0));

      // TODO replace with proper icon.
      toolbar.add(myCollapseButton, new TabularLayout.Constraint(0, 1, 3, 1));
    }
    else {
      toolbar = new JPanel(new TabularLayout("*,Fit,*", "Fit,*,Fit"));
      toolbar.setMinimumSize(new Dimension(SESSIONS_COLLAPSED_MIN_WIDTH, 0));
      toolbar.setPreferredSize(new Dimension(SESSIONS_COLLAPSED_MIN_WIDTH, 0));

      RotatedLabel label = new RotatedLabel("SESSIONS");
      label.setBorder(TOOLBAR_LABEL_BORDER);
      toolbar.add(label, new TabularLayout.Constraint(2, 1, 1, 1));

      // TODO replace with proper icon.
      toolbar.add(myExpandButton, new TabularLayout.Constraint(0, 0, 1, 3));
    }

    return toolbar;
  }

  private void refreshSessions() {
    java.util.List<Common.Session> sessions = new ArrayList<>(myProfilers.getSessions().values());
    Collections.sort(sessions, Comparator.comparingLong(Common.Session::getStartTimestamp));

    mySelectedSession = myProfilers.getSession();
    mySessionsListModel.clear();
    int selectionIndex = -1;
    for (int i = 0; i < sessions.size(); i++) {
      Common.Session session = sessions.get(i);
      mySessionsListModel.addElement(session);

      if (mySelectedSession == session) {
        selectionIndex = i;
      }
    }
    mySessionsList.setSelectedIndex(selectionIndex);
  }

  /**
   * TODO b\67509537 we need a much more customized cell renderer.
   */
  private static class SessionsCellRenderer extends ListCellRendererWrapper<Common.Session> {
    @Override
    public void customize(JList list, Common.Session value, int index, boolean selected, boolean hasFocus) {
      String duration = value.getEndTimestamp() == Long.MAX_VALUE ? "current" :
                        TimeAxisFormatter.DEFAULT
                          .getClockFormattedString(TimeUnit.NANOSECONDS.toMicros(value.getEndTimestamp() - value.getStartTimestamp()));
      setText(String.format("Session - %s", duration));
    }
  }
}