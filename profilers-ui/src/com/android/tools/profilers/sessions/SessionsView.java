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
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ViewBinder;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

  @NotNull private final SessionsManager mySessionsManager;
  @NotNull private final JComponent myComponent;
  @NotNull private final JButton myExpandButton;
  @NotNull private final JButton myCollapseButton;
  @NotNull private final JList<SessionArtifact> mySessionsList;
  @NotNull private final DefaultListModel<SessionArtifact> mySessionsListModel;

  private boolean myIsExpanded;

  public SessionsView(@NotNull SessionsManager sessionsManager) {
    mySessionsManager = sessionsManager;
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
    mySessionsList.setMinimumSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, 0));
    mySessionsList.setOpaque(false);
    mySessionsList.setCellRenderer(new SessionsCellRenderer(this));
    mySessionsList.setSelectionMode(SINGLE_SELECTION);
    mySessionsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        SessionArtifact artifact = mySessionsList.getSelectedValue();
        if (artifact != null) {
          artifact.onSelect();
        }
      }
    });
    mySessionsManager.addDependency(this)
      .onChange(SessionAspect.SESSIONS, this::refreshSessions)
      .onChange(SessionAspect.SELECTED_SESSION, this::refreshSelection);
    initializeUI();
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @VisibleForTesting
  JList<SessionArtifact> getSessionsList() {
    return mySessionsList;
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
      toolbar = new JPanel(new TabularLayout("*,Fit,Fit", "*,Fit,*"));
      toolbar.setBorder(AdtUiUtils.DEFAULT_BOTTOM_BORDER);
      toolbar.setMinimumSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));
      toolbar.setPreferredSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));

      JLabel label = new JLabel("SESSIONS");
      label.setBorder(TOOLBAR_LABEL_BORDER);
      toolbar.add(label, new TabularLayout.Constraint(1, 0));

      // TODO replace with proper icon.
      toolbar.add(myCollapseButton, new TabularLayout.Constraint(0, 1, 3, 1));
      myCollapseButton.setVisible(true);
      // Note - if we simply remove the expand button after it is clicked, next time we add it back it will
      // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
      // render and update its state even though it is hidden.
      toolbar.add(myExpandButton, new TabularLayout.Constraint(0, 2, 3, 1));
      myExpandButton.setVisible(false);
    }
    else {
      toolbar = new JPanel(new TabularLayout("*,Fit,*", "Fit,Fit,*"));
      toolbar.setMinimumSize(new Dimension(SESSIONS_COLLAPSED_MIN_WIDTH, 0));

      // TODO replace with proper icon.
      toolbar.add(myExpandButton, new TabularLayout.Constraint(0, 0, 1, 3));
      myExpandButton.setVisible(true);
      // Note - if we simply remove the collapse button after it is clicked, next time we add it back it will
      // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
      // render and update its state even though it is hidden.
      toolbar.add(myCollapseButton, new TabularLayout.Constraint(1, 0, 1, 3));
      myCollapseButton.setVisible(false);
    }

    return toolbar;
  }

  private void refreshSessions() {
    java.util.List<SessionArtifact> sessionItems = mySessionsManager.getSessionArtifacts();

    SessionArtifact previousSelectedArtifact = mySessionsList.getSelectedValue();
    Common.Session previousSelectedSession =
      previousSelectedArtifact != null ? previousSelectedArtifact.getSession() : Common.Session.getDefaultInstance();

    mySessionsListModel.clear();
    int newSelectionIndex = -1;
    for (int i = 0; i < sessionItems.size(); i++) {
      SessionArtifact item = sessionItems.get(i);
      mySessionsListModel.addElement(item);
      if (previousSelectedSession.equals(item.getSession())) {
        newSelectionIndex = i;
      }
    }

    mySessionsList.setSelectedIndex(newSelectionIndex);
  }

  private void refreshSelection() {
    Common.Session selectedSession = mySessionsManager.getSelectedSession();
    for (int i = 0; i < mySessionsListModel.size(); i++) {
      SessionArtifact item = mySessionsListModel.get(i);
      if (selectedSession.equals(item.getSession())) {
        mySessionsList.setSelectedIndex(i);
        break;
      }
    }
  }

  private static class SessionsCellRenderer implements ListCellRenderer<SessionArtifact> {

    @NotNull private final SessionsView mySessionsView;
    @NotNull private final ViewBinder<SessionsView, SessionArtifact, SessionArtifactRenderer> myViewBinder;

    public SessionsCellRenderer(@NotNull SessionsView sessionsView) {
      mySessionsView = sessionsView;
      myViewBinder = new ViewBinder<>();
      myViewBinder.bind(SessionItem.class, (p, m) -> new SessionItemRenderer());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SessionArtifact> list,
                                                  SessionArtifact item,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      SessionArtifactRenderer renderer = myViewBinder.build(mySessionsView, item);
      return renderer.generateComponent(list, item, index, isSelected, cellHasFocus);
    }
  }
}