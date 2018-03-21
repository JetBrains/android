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
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.profilers.ProfilerLayout.*;
import static com.android.tools.profilers.StudioProfilers.buildDeviceName;

/**
 * A collapsible panel which lets users see the list of and interact with their profiling sessions.
 */
public class SessionsView extends AspectObserver {
  // Collapsed width should essentially look like a toolbar.
  private static final int SESSIONS_COLLAPSED_MIN_WIDTH = JBUI.scale(32);
  private static final int SESSIONS_EXPANDED_MIN_WIDTH = JBUI.scale(200);

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final SessionsManager mySessionsManager;
  @NotNull private final JComponent myComponent;
  @NotNull private final JScrollPane myScrollPane;
  @NotNull private final JButton myExpandButton;
  @NotNull private final JButton myCollapseButton;
  @NotNull private final JButton myStopProfilingButton;
  @NotNull private final CommonAction myProcessSelectionAction;
  @NotNull private final CommonDropDownButton myProcessSelectionDropDown;
  @NotNull private final SessionsList mySessionsList;
  @NotNull private final DefaultListModel<SessionArtifact> mySessionsListModel;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  private boolean myIsExpanded;

  public SessionsView(@NotNull StudioProfilers profilers, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfilers = profilers;
    myIdeProfilerComponents = ideProfilerComponents;
    mySessionsManager = myProfilers.getSessionsManager();
    // Starts out with a collapsed sessions panel.
    // TODO b\73159126 make this configurable. e.g. save user's previous settings.
    myIsExpanded = false;
    myComponent = new JPanel(new BorderLayout());
    myComponent.setBorder(AdtUiUtils.DEFAULT_RIGHT_BORDER);
    myExpandButton = new CommonButton(StudioIcons.Profiler.Toolbar.EXPAND_SESSION);
    myExpandButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    myExpandButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    myExpandButton.setBorder(TOOLBAR_ICON_BORDER);
    myExpandButton.setToolTipText("Expand the Sessions panel.");
    myExpandButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsExpanded = true;
        initializeUI();
      }
    });
    myCollapseButton = new CommonButton(StudioIcons.Profiler.Toolbar.COLLAPSE_SESSION);
    myCollapseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    myCollapseButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    myCollapseButton.setBorder(TOOLBAR_ICON_BORDER);
    myCollapseButton.setToolTipText("Collapse the Sessions panel.");
    myCollapseButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsExpanded = false;
        initializeUI();
      }
    });

    myStopProfilingButton = new CommonButton(StudioIcons.Profiler.Toolbar.STOP_SESSION);
    myStopProfilingButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_SESSION));
    myStopProfilingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    myStopProfilingButton.setAlignmentY(Component.CENTER_ALIGNMENT);
    myStopProfilingButton.setBorder(TOOLBAR_ICON_BORDER);
    myStopProfilingButton.setToolTipText("Stop the current profiling session.");
    myStopProfilingButton.setEnabled(false);
    myStopProfilingButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySessionsManager.endCurrentSession();
      }
    });

    myProcessSelectionAction = new CommonAction("", StudioIcons.Common.ADD);
    myProcessSelectionDropDown = new CommonDropDownButton(myProcessSelectionAction);
    myProcessSelectionDropDown.setToolTipText("Start a new profiling session.");
    myProcessSelectionDropDown.setAlignmentX(Component.CENTER_ALIGNMENT);
    myProcessSelectionDropDown.setAlignmentY(Component.CENTER_ALIGNMENT);
    myProcessSelectionDropDown.setBorder(TOOLBAR_ICON_BORDER);
    myProfilers.addDependency(this)
      .onChange(ProfilerAspect.DEVICES, this::refreshProcessDropdown)
      .onChange(ProfilerAspect.PROCESSES, this::refreshProcessDropdown);
    addImportAction();

    mySessionsListModel = new DefaultListModel<>();
    mySessionsList = new SessionsList(mySessionsListModel);
    mySessionsList.setOpaque(false);
    mySessionsManager.addDependency(this)
      .onChange(SessionAspect.SESSIONS, this::refreshSessions)
      .onChange(SessionAspect.PROFILING_SESSION, () -> myStopProfilingButton
        .setEnabled(!Common.Session.getDefaultInstance().equals(mySessionsManager.getProfilingSession())));

    myScrollPane = new JBScrollPane(mySessionsList);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    initializeUI();
  }

  @NotNull
  public static Dimension getComponentMinimizeSize(boolean isExpanded) {
    return isExpanded ? new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, 0) : new Dimension(SESSIONS_COLLAPSED_MIN_WIDTH, 0);
  }

  @NotNull
  public JComponent getComponent() {
    return myComponent;
  }

  @VisibleForTesting
  @NotNull
  JList<SessionArtifact> getSessionsList() {
    return mySessionsList;
  }

  @VisibleForTesting
  @NotNull
  CommonAction getProcessSelectionAction() {
    return myProcessSelectionAction;
  }

  @VisibleForTesting
  @NotNull
  JButton getStopProfilingButton() {
    return myStopProfilingButton;
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
      myComponent.add(myScrollPane, BorderLayout.CENTER);
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
      toolbar = new JPanel();
      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.setBorder(AdtUiUtils.DEFAULT_BOTTOM_BORDER);
      toolbar.setMinimumSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));
      toolbar.setPreferredSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));

      JLabel label = new JLabel("SESSIONS");
      label.setAlignmentY(Component.CENTER_ALIGNMENT);
      label.setBorder(TOOLBAR_LABEL_BORDER);
      label.setFont(TOOLBAR_LABEL_FONT);
      toolbar.add(label);
      toolbar.add(Box.createHorizontalGlue());
      toolbar.add(myProcessSelectionDropDown);
      toolbar.add(myStopProfilingButton);
      toolbar.add(myCollapseButton);
      myCollapseButton.setVisible(true);
      // Note - if we simply remove the expand button after it is clicked, next time we add it back it will
      // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
      // render and update its state even though it is hidden.
      toolbar.add(myExpandButton);
      myExpandButton.setVisible(false);
    }
    else {
      toolbar = new JPanel();
      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
      toolbar.setMinimumSize(new Dimension(SESSIONS_COLLAPSED_MIN_WIDTH, 0));

      toolbar.add(myExpandButton);
      myExpandButton.setVisible(true);
      // Note - if we simply remove the collapse button after it is clicked, next time we add it back it will
      // maintain its hovered/clicked state until it is hovered again. Adding it here so it has a chance to
      // render and update its state even though it is hidden.
      toolbar.add(myCollapseButton, new TabularLayout.Constraint(1, 0, 1, 3));
      myCollapseButton.setVisible(false);
      toolbar.add(myStopProfilingButton);
      toolbar.add(myProcessSelectionDropDown);
    }

    return toolbar;
  }

  private void refreshSessions() {
    List<SessionArtifact> sessionItems = mySessionsManager.getSessionArtifacts();
    mySessionsListModel.clear();
    for (int i = 0; i < sessionItems.size(); i++) {
      SessionArtifact item = sessionItems.get(i);
      mySessionsListModel.addElement(item);
    }
  }

  private void addImportAction() {
    // Add the dropdown action for loading from file
    if (myProfilers.getIdeServices().getFeatureConfig().isSessionImportEnabled()) {
      CommonAction loadAction = new CommonAction("Load from file...", null);
      loadAction.setAction(
        () -> myIdeProfilerComponents.createImportDialog().open(
          () -> "Open",
          Collections.singletonList("hprof"),
          file -> {
            if (!myProfilers.getSessionsManager().importSessionFromFile(new File(file.getPath()))) {
              myIdeProfilerComponents.createUiMessageHandler()
                .displayErrorMessage(myComponent, "File Open Error", String.format("Unknown file type: %s", file.getPath()));
            }
          }));
      myProcessSelectionAction.addChildrenActions(loadAction);
    }
  }

  private void refreshProcessDropdown() {
    Map<Common.Device, java.util.List<Common.Process>> processMap = myProfilers.getDeviceProcessMap();
    myProcessSelectionAction.clear();

    addImportAction();

    if (!processMap.isEmpty()) {
      myProcessSelectionAction.addChildrenActions(new CommonAction.Separator());
    }

    Common.Device selectedDevice = myProfilers.getDevice();
    Common.Process selectedProcess = myProfilers.getProcess();
    // Rebuild the action tree.
    for (Common.Device device : processMap.keySet()) {
      CommonAction deviceAction = new CommonAction(buildDeviceName(device), null);
      for (Common.Process process : processMap.get(device)) {
        CommonAction processAction = new CommonAction(process.getName(), null);
        processAction.setAction(() -> {
          myProfilers.setDevice(device);
          myProfilers.setProcess(process);
        });
        processAction.setSelected(process.equals(selectedProcess));
        deviceAction.addChildrenActions(processAction);
      }
      deviceAction.setEnabled(deviceAction.getChildrenActionCount() > 0);
      deviceAction.setSelected(device.equals(selectedDevice));
      myProcessSelectionAction.addChildrenActions(deviceAction);
    }
  }
}