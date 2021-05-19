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

import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_LABEL_BORDER;
import static com.android.tools.profilers.StudioProfilers.buildDeviceName;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.model.stdui.CommonAction;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.adtui.stdui.menu.CommonDropDownButton;
import com.android.tools.idea.IdeInfo;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.CpuCaptureArtifactView;
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact;
import com.android.tools.profilers.memory.HeapProfdArtifactView;
import com.android.tools.profilers.memory.HeapProfdSessionArtifact;
import com.android.tools.profilers.memory.HprofArtifactView;
import com.android.tools.profilers.memory.HprofSessionArtifact;
import com.android.tools.profilers.memory.LegacyAllocationsArtifactView;
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Ordering;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;

/**
 * A collapsible panel which lets users see the list of and interact with their profiling sessions.
 */
public class SessionsView extends AspectObserver {

  private static final String HIDE_STOP_PROMPT = "session.hide.stop.prompt";
  private static final String HIDE_RESTART_PROMPT = "session.hide.restart.prompt";
  private static final String CONFIRM_END_TITLE = "End Session";
  private static final String CONFIRM_END_MESSAGE = "Are you sure you want to end the current profiling session?";
  private static final String CONFIRM_RESTART_MESSAGE =
    "Selecting a different process stops the current profiler session and starts a new one. Do you want to continue?";
  private static final String CONFIRM_BUTTON_TEXT = "Yes";
  private static final String CANCEL_BUTTON_TEXT = "Cancel";

  /**
   * Preference string for whether the sessions UI is collapsed (bool).
   */
  public static final String SESSION_IS_COLLAPSED = "SESSION_IS_COLLAPSED";
  /**
   * Preference string for the last known width (int) of the sessions UI when it was expanded.
   */
  public static final String SESSION_EXPANDED_WIDTH = "SESSION_EXPANDED_WIDTH";
  /**
   * String to display in the dropdown when no devices are detected.
   */
  @VisibleForTesting static final String NO_SUPPORTED_DEVICES = "No supported devices";
  /**
   * String to display in the dropdown when no debuggable processes are detected.
   */
  @VisibleForTesting static final String NO_DEBUGGABLE_PROCESSES = "No debuggable processes";

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
  @NotNull private final JPanel mySessionsPanel;
  @NotNull ViewBinder<SessionArtifactView.ArtifactDrawInfo, SessionArtifact, SessionArtifactView> mySessionArtifactViewBinder;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  private boolean myIsCollapsed;

  public SessionsView(@NotNull StudioProfilers profilers, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfilers = profilers;
    myIdeProfilerComponents = ideProfilerComponents;
    mySessionsManager = myProfilers.getSessionsManager();
    myIsCollapsed = myProfilers.getIdeServices().getPersistentProfilerPreferences().getBoolean(SESSION_IS_COLLAPSED, false);
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
        myIsCollapsed = false;
        initializeUI();
        myProfilers.getIdeServices().getPersistentProfilerPreferences().setBoolean(SESSION_IS_COLLAPSED, myIsCollapsed);
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
        myIsCollapsed = true;
        initializeUI();
        myProfilers.getIdeServices().getPersistentProfilerPreferences().setBoolean(SESSION_IS_COLLAPSED, myIsCollapsed);
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
        boolean confirmed = true;
        if (!myProfilers.getIdeServices().getTemporaryProfilerPreferences().getBoolean(HIDE_STOP_PROMPT, false)) {
          confirmed = myIdeProfilerComponents.createUiMessageHandler().displayOkCancelMessage(
            CONFIRM_END_TITLE,
            CONFIRM_END_MESSAGE,
            CONFIRM_BUTTON_TEXT,
            CANCEL_BUTTON_TEXT,
            null,
            result -> myProfilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HIDE_STOP_PROMPT, result)
          );
        }

        if (confirmed) {
          stopProfilingSession();
        }
      }
    });

    myProcessSelectionAction = new CommonAction("", AllIcons.General.Add);
    myProcessSelectionAction.setAction(() -> myProfilers.getIdeServices().getFeatureTracker().trackSessionDropdownClicked());
    myProcessSelectionDropDown = new CommonDropDownButton(myProcessSelectionAction);
    myProcessSelectionDropDown.setToolTipText("Start a new profiling session.");
    myProcessSelectionDropDown.setAlignmentX(Component.CENTER_ALIGNMENT);
    myProcessSelectionDropDown.setAlignmentY(Component.CENTER_ALIGNMENT);
    myProcessSelectionDropDown.setBorder(TOOLBAR_ICON_BORDER);
    myProfilers.addDependency(this)
      .onChange(ProfilerAspect.PROCESSES, this::refreshProcessDropdown);

    mySessionsManager.addDependency(this)
      .onChange(SessionAspect.SESSIONS, this::refreshSessions)
      .onChange(SessionAspect.PROFILING_SESSION, () -> myStopProfilingButton
        .setEnabled(!Common.Session.getDefaultInstance().equals(mySessionsManager.getProfilingSession())));

    // Sessions artifacts are vertically stacked in a single column.
    // We are using a scrollable JPanel instead of an JList because JList's cell renderer are not designed to support the animation and
    // interaction we want to support within each list item (e.g. spinning icons, nested buttons, etc)
    mySessionsPanel = new JPanel();
    mySessionsPanel.setLayout(new TabularLayout("*"));
    mySessionsPanel.setOpaque(false);

    myScrollPane = new JBScrollPane(mySessionsPanel);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setOpaque(false);
    myScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

    mySessionArtifactViewBinder = new ViewBinder<>();
    mySessionArtifactViewBinder.bind(SessionItem.class, SessionItemView::new);
    mySessionArtifactViewBinder.bind(HprofSessionArtifact.class, HprofArtifactView::new);
    mySessionArtifactViewBinder.bind(HeapProfdSessionArtifact.class, HeapProfdArtifactView::new);
    mySessionArtifactViewBinder.bind(LegacyAllocationsSessionArtifact.class, LegacyAllocationsArtifactView::new);
    mySessionArtifactViewBinder.bind(CpuCaptureSessionArtifact.class, CpuCaptureArtifactView::new);

    initializeUI();
    refreshProcessDropdown();
  }

  @NotNull
  public StudioProfilers getProfilers() {
    return myProfilers;
  }

  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
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
  public boolean getCollapsed() {
    return myIsCollapsed;
  }

  @VisibleForTesting
  @NotNull
  JComponent getSessionsPanel() {
    return mySessionsPanel;
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

  @VisibleForTesting
  @NotNull
  public JButton getExpandButton() {
    return myExpandButton;
  }

  @VisibleForTesting
  @NotNull
  public JButton getCollapseButton() {
    return myCollapseButton;
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
    if (myIsCollapsed) {
      // We only need the toolbar when collapsed
      myComponent.add(createToolbar(), BorderLayout.CENTER);
    }
    else {
      myComponent.add(createToolbar(), BorderLayout.NORTH);
      myComponent.add(myScrollPane, BorderLayout.CENTER);
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  @NotNull
  private JComponent createToolbar() {
    JPanel toolbar;
    if (myIsCollapsed) {
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
    else {
      toolbar = new JPanel();
      toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.X_AXIS));
      toolbar.setBorder(AdtUiUtils.DEFAULT_BOTTOM_BORDER);
      toolbar.setMinimumSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));
      toolbar.setPreferredSize(new Dimension(SESSIONS_EXPANDED_MIN_WIDTH, TOOLBAR_HEIGHT));

      JLabel label = new JLabel("SESSIONS");
      label.setAlignmentY(Component.CENTER_ALIGNMENT);
      label.setBorder(TOOLBAR_LABEL_BORDER);
      label.setFont(ProfilerFonts.SMALL_FONT);
      label.setForeground(StandardColors.TEXT_COLOR);
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

    return toolbar;
  }

  private void refreshSessions() {
    List<SessionArtifact> sessionItems = mySessionsManager.getSessionArtifacts();
    mySessionsPanel.removeAll();
    for (int i = 0; i < sessionItems.size(); i++) {
      SessionArtifact item = sessionItems.get(i);
      SessionArtifactView.ArtifactDrawInfo drawInfo = new SessionArtifactView.ArtifactDrawInfo(this, i);
      mySessionsPanel.add(mySessionArtifactViewBinder.build(drawInfo, item), new TabularLayout.Constraint(i, 0));
    }

    mySessionsPanel.revalidate();
    mySessionsPanel.repaint();
  }

  private void addImportAction() {
    // Add the dropdown action for loading from file
    CommonAction loadAction = new CommonAction("Load from file...", null);
    List<String> supportedExtensions = new ArrayList<>();
    supportedExtensions.add("trace");
    supportedExtensions.add("alloc");
    supportedExtensions.add("hprof");
    if (getProfilers().getIdeServices().getFeatureConfig().isNativeMemorySampleEnabled()) {
      supportedExtensions.add("heapprofd");
    }
    loadAction.setAction(
      () -> myIdeProfilerComponents.createImportDialog().open(
        () -> "Open", supportedExtensions,
        file -> {
          if (!myProfilers.getSessionsManager().importSessionFromFile(new File(file.getPath()))) {
            myIdeProfilerComponents.createUiMessageHandler()
              .displayErrorMessage(myComponent, "File Open Error",
                                   String.format("Unknown file type: %s", file.getPath()));
          }
        }));
    myProcessSelectionAction.addChildrenActions(loadAction, new CommonAction.SeparatorAction());
  }

  void stopProfilingSession() {
    // We should not start auto-profiling other things if the user manually stops a session.
    myProfilers.setAutoProfilingEnabled(false);
    // Unselect the device and process which stops the session. This also avoids them from appearing to be selected in the process
    // selection dropdown even after the session has stopped.
    myProfilers.setProcess(null, null);
    myProfilers.getIdeServices().getFeatureTracker().trackStopSession();
  }

  private void refreshProcessDropdown() {
    Map<Common.Device, List<Common.Process>> processMap = myProfilers.getDeviceProcessMap();
    myProcessSelectionAction.clear();
    addImportAction();

    // Rebuild the action tree.
    Set<Common.Device> devices = processMap.keySet().stream()
      .filter(device -> device.getState() == Common.Device.State.ONLINE).collect(Collectors.toSet());
    if (devices.isEmpty()) {
      CommonAction noDeviceAction = new CommonAction(NO_SUPPORTED_DEVICES, null);
      noDeviceAction.setEnabled(false);
      myProcessSelectionAction.addChildrenActions(noDeviceAction);
    }
    else {
      for (Common.Device device : devices) {
        CommonAction deviceAction = new CommonAction(buildDeviceName(device), null);
        List<Common.Process> processes =
          ContainerUtil.filter(processMap.get(device), process -> process.getState() == Common.Process.State.ALIVE);
        if (processes.isEmpty()) {
          String noProcessReason = device.getUnsupportedReason().isEmpty() ? NO_DEBUGGABLE_PROCESSES : device.getUnsupportedReason();
          CommonAction noProcessAction = new CommonAction(noProcessReason, null);
          noProcessAction.setEnabled(false);
          deviceAction.addChildrenActions(noProcessAction);
        }
        else {
          List<CommonAction> preferredProcessActions = new ArrayList<>();
          List<CommonAction> otherProcessActions = new ArrayList<>();
          for (Common.Process process : processes) {
            CommonAction processAction = new CommonAction(String.format(Locale.US, "%s (%d)", process.getName(), process.getPid()), null);
            processAction.setAction(() -> {
              // First warn and stop the currently profiling session if there is one.
              if (SessionsManager.isSessionAlive(myProfilers.getSessionsManager().getProfilingSession())) {
                boolean confirmed = true;
                if (!myProfilers.getIdeServices().getTemporaryProfilerPreferences().getBoolean(HIDE_RESTART_PROMPT, false)) {
                  confirmed = myIdeProfilerComponents.createUiMessageHandler().displayOkCancelMessage(
                    CONFIRM_END_TITLE,
                    CONFIRM_RESTART_MESSAGE,
                    CONFIRM_BUTTON_TEXT,
                    CANCEL_BUTTON_TEXT,
                    null,
                    result -> myProfilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HIDE_RESTART_PROMPT, result)
                  );
                }

                if (!confirmed) {
                  // Do not continue to start a new session.
                  return;
                }

                stopProfilingSession();
              }

              myProfilers.setProcess(device, process);
              myProfilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.FULL,
                                                                                  SessionsManager.SessionCreationSource.MANUAL);
            });

            String preferredProcess = myProfilers.getPreferredProcessName();
            // Do a prefix instead of exact match as they could be multiple candidates from the same project.
            if (preferredProcess != null && process.getName().startsWith(preferredProcess)) {
              preferredProcessActions.add(processAction);
            }
            else {
              otherProcessActions.add(processAction);
            }
          }

          if (!preferredProcessActions.isEmpty()) {
            preferredProcessActions.sort(Comparator.comparing(CommonAction::getText, Ordering.natural()));
            deviceAction.addChildrenActions(preferredProcessActions);
          }

          if (!otherProcessActions.isEmpty()) {
            // Only add the separator if there are preferred processes added.
            if (deviceAction.getChildrenActionCount() != 0) {
              deviceAction.addChildrenActions(new CommonAction.SeparatorAction());
            }

            if (IdeInfo.isGameTool()) {
              // In standalone profiler, all processes falls under "Other processes" so there is no point to have this separate flyout.
              deviceAction.addChildrenActions(otherProcessActions);
            }
            else {
              CommonAction otherProcessesFlyout = new CommonAction("Other processes", null);
              otherProcessActions.sort(Comparator.comparing(CommonAction::getText, Ordering.natural()));
              otherProcessesFlyout.addChildrenActions(otherProcessActions);
              deviceAction.addChildrenActions(otherProcessesFlyout);
            }
          }
        }
        myProcessSelectionAction.addChildrenActions(deviceAction);
      }
    }
  }
}