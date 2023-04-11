/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerFonts.H4_FONT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.JBEmptyBorder;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper for a [StageView] with an accompanying toolbar and context menu.
 */
public class StageWithToolbarView extends AspectObserver {
  public static final String ATTACH_LIVE = "Attach to live";
  public static final String DETACH_LIVE = "Detach live";
  public static final String ZOOM_IN = "Zoom in";
  public static final String ZOOM_OUT = "Zoom out";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";

  private final StudioProfilers myProfiler;
  private final CardLayout myStageCenterCardLayout;
  private final JPanel myStageCenterComponent;
  private final LoadingPanel myStageLoadingPanel;
  private JPanel myToolbar;
  private StageNavigationToolbar myStageNavigationToolbar;
  private JPanel myTimelineNavigationToolbar;
  private CommonButton myZoomOut;
  private CommonButton myZoomIn;
  private CommonButton myResetZoom;
  private CommonButton myZoomToSelectionButton;
  private DefaultContextMenuItem myZoomToSelectionAction;
  private JPanel myGoLiveToolbar;
  private JToggleButton myGoLive;
  private JPanel myCustomStageToolbar;
  private StageView myStageView;

  public StageWithToolbarView(@NotNull StudioProfilers profilers,
                              @NotNull JPanel stageComponent,
                              @NotNull IdeProfilerComponents ideProfilerComponents,
                              @NotNull Function<Stage, StageView> stageViewBuilder,
                              @NotNull JComponent containerComponent) {
    this.myProfiler = profilers;

    myStageCenterCardLayout = new CardLayout();
    myStageCenterComponent = new JPanel(myStageCenterCardLayout);

    myStageLoadingPanel = ideProfilerComponents.createLoadingPanel(0);
    myStageLoadingPanel.setLoadingText("");
    myStageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    initializeStageUi(containerComponent, stageComponent);

    myProfiler.addDependency(this)
      .onChange(ProfilerAspect.STAGE, () -> updateStageView(stageViewBuilder, containerComponent))
      .onChange(ProfilerAspect.AGENT, this::toggleStageLayout)
      .onChange(ProfilerAspect.PREFERRED_PROCESS, this::toggleStageLayout);
    updateStageView(stageViewBuilder, containerComponent);
    toggleStageLayout();
  }

  @VisibleForTesting
  final JComponent getStageLoadingComponent() {
    return myStageLoadingPanel.getComponent();
  }

  @VisibleForTesting
  @NotNull
  JPanel getTimelineNavigationToolbar() {
    return myTimelineNavigationToolbar;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return myZoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return myZoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return myResetZoom;
  }

  @VisibleForTesting
  @NotNull
  public CommonButton getZoomToSelectionButton() {
    return myZoomToSelectionButton;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return myGoLive;
  }

  @VisibleForTesting
  final JComponent getStageViewComponent() {
    return myStageView.getComponent();
  }

  @Nullable
  public StageView getStageView() {
    return myStageView;
  }

  private void initializeStageUi(@NotNull JComponent containerComponent, @NotNull JPanel stageComponent) {
    myToolbar = new JPanel(new BorderLayout());
    myToolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    myToolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    myStageNavigationToolbar = new StageNavigationToolbar(myProfiler);
    myToolbar.add(myStageNavigationToolbar, BorderLayout.WEST);

    myTimelineNavigationToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myToolbar.add(myTimelineNavigationToolbar, BorderLayout.EAST);
    myTimelineNavigationToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    myZoomOut = new CommonButton(AllIcons.General.ZoomOut);
    myZoomOut.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomOut));
    myZoomOut.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomOut();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    DefaultContextMenuItem zoomOutAction =
      new DefaultContextMenuItem.Builder(ZOOM_OUT).setContainerComponent(containerComponent).setActionRunnable(() -> myZoomOut.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();

    myZoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    myTimelineNavigationToolbar.add(myZoomOut);

    myZoomIn = new CommonButton(AllIcons.General.ZoomIn);
    myZoomIn.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomIn));
    myZoomIn.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomIn();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    DefaultContextMenuItem zoomInAction =
      new DefaultContextMenuItem.Builder(ZOOM_IN).setContainerComponent(containerComponent)
        .setActionRunnable(() -> myZoomIn.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER)).build();
    myZoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    myTimelineNavigationToolbar.add(myZoomIn);

    myResetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    myResetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    myResetZoom.addActionListener(event -> {
      myStageView.getStage().getTimeline().resetZoom();
      myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    DefaultContextMenuItem resetZoomAction =
      new DefaultContextMenuItem.Builder("Reset zoom").setContainerComponent(containerComponent)
        .setActionRunnable(() -> myResetZoom.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                       KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    myResetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    myTimelineNavigationToolbar.add(myResetZoom);

    myZoomToSelectionButton = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    myZoomToSelectionButton.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    myZoomToSelectionButton.addActionListener(event -> {
      myStageView.getStage().getTimeline().frameViewToRange(myStageView.getStage().getTimeline().getSelectionRange());
      myProfiler.getIdeServices().getFeatureTracker().trackZoomToSelection();
    });
    myZoomToSelectionAction = new DefaultContextMenuItem.Builder("Zoom to Selection")
      .setContainerComponent(containerComponent)
      .setActionRunnable(() -> myZoomToSelectionButton.doClick(0))
      .setEnableBooleanSupplier(() -> myStageView != null && !myStageView.getStage().getTimeline().getSelectionRange().isEmpty())
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0))
      .build();
    myZoomToSelectionButton.setToolTipText(myZoomToSelectionAction.getDefaultToolTipText());
    myTimelineNavigationToolbar.add(myZoomToSelectionButton);

    myGoLiveToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myGoLiveToolbar.add(new FlatSeparator());

    myGoLive = new CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    myGoLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    myGoLive.setFont(H4_FONT);
    myGoLive.setHorizontalTextPosition(SwingConstants.LEFT);
    myGoLive.setHorizontalAlignment(SwingConstants.LEFT);
    myGoLive.setBorder(new JBEmptyBorder(3, 7, 3, 7));
    // Configure shortcuts for GoLive.
    DefaultContextMenuItem attachAction =
      new DefaultContextMenuItem.Builder(ATTACH_LIVE).setContainerComponent(containerComponent)
        .setActionRunnable(() -> myGoLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> myGoLive.isEnabled() &&
                !myGoLive.isSelected() &&
                myStageView.supportsStreaming())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();
    DefaultContextMenuItem detachAction =
      new DefaultContextMenuItem.Builder(DETACH_LIVE).setContainerComponent(containerComponent)
        .setActionRunnable(() -> myGoLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> myGoLive.isEnabled() &&
                myGoLive.isSelected() &&
                myStageView.supportsStreaming())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)).build();

    myGoLive.setToolTipText(detachAction.getDefaultToolTipText());
    myGoLive.addActionListener(event -> {
      Timeline currentStageTimeline = myStageView.getStage().getTimeline();
      // b/221920489 Hot key may trigger this action from another stage without the streaming timeline
      if (currentStageTimeline instanceof StreamingTimeline) {
        ((StreamingTimeline)currentStageTimeline).toggleStreaming();
        myProfiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
      }
    });
    myGoLive.addChangeListener(e -> {
      boolean isSelected = myGoLive.isSelected();
      myGoLive.setIcon(isSelected ? StudioIcons.Profiler.Toolbar.PAUSE_LIVE : StudioIcons.Profiler.Toolbar.GOTO_LIVE);
      myGoLive.setToolTipText(isSelected ? detachAction.getDefaultToolTipText() : attachAction.getDefaultToolTipText());
    });
    myProfiler.getTimeline().addDependency(this).onChange(StreamingTimeline.Aspect.STREAMING, this::updateStreaming);
    myGoLiveToolbar.add(myGoLive);
    myTimelineNavigationToolbar.add(myGoLiveToolbar);

    ProfilerContextMenu.createIfAbsent(stageComponent)
      .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    myProfiler.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, this::toggleTimelineButtons);
    toggleTimelineButtons();

    myCustomStageToolbar = new JPanel(new BorderLayout());
    myToolbar.add(myCustomStageToolbar, BorderLayout.CENTER);

    stageComponent.add(myToolbar, BorderLayout.NORTH);
    stageComponent.add(myStageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void toggleTimelineButtons() {
    boolean isAlive = myProfiler.getSessionsManager().isSessionAlive();
    if (isAlive) {
      Common.AgentData agentData = myProfiler.getAgentData();
      boolean waitForAgent = agentData.getStatus() == Common.AgentData.Status.UNSPECIFIED;
      if (waitForAgent) {
        // Disable all controls if the agent is still initialization/attaching.
        myZoomOut.setEnabled(false);
        myZoomIn.setEnabled(false);
        myResetZoom.setEnabled(false);
        myZoomToSelectionButton.setEnabled(false);
        myGoLive.setEnabled(false);
        myGoLive.setSelected(false);
      }
      else {
        myZoomOut.setEnabled(true);
        myZoomIn.setEnabled(true);
        myResetZoom.setEnabled(true);
        myZoomToSelectionButton.setEnabled(myZoomToSelectionAction.isEnabled());
        myGoLive.setEnabled(true);
        myGoLive.setSelected(true);
      }
    }
    else {
      boolean isValidSession = !Common.Session.getDefaultInstance().equals(myProfiler.getSessionsManager().getSelectedSession());
      myZoomOut.setEnabled(isValidSession);
      myZoomIn.setEnabled(isValidSession);
      myResetZoom.setEnabled(isValidSession);
      myZoomToSelectionButton.setEnabled(isValidSession && myZoomToSelectionAction.isEnabled());
      myGoLive.setEnabled(false);
      myGoLive.setSelected(false);
    }
  }


  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
  }

  private void updateStageView(@NotNull Function<Stage, StageView> stageViewBuilder,
                               @NotNull JComponent containerComponent) {
    Stage stage = myProfiler.getStage();
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    if (myStageView != null) {
      myStageView.getStage().getTimeline().getSelectionRange().removeDependencies(this);
    }
    myStageView = stageViewBuilder.apply(stage);
    myStageView.getStage().getTimeline().getSelectionRange().addDependency(this)
      .onChange(Range.Aspect.RANGE, () -> myZoomToSelectionButton.setEnabled(myZoomToSelectionAction.isEnabled()));
    SwingUtilities.invokeLater(() -> {
      Component focussed = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (focussed == null || !SwingUtilities.isDescendingFrom(focussed, containerComponent)) {
        containerComponent.requestFocusInWindow();
      }
    });

    myStageCenterComponent.removeAll();
    myStageCenterComponent.add(myStageView.getComponent(), STAGE_VIEW_CARD);
    myStageCenterComponent.add(myStageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    myStageCenterComponent.revalidate();
    myCustomStageToolbar.removeAll();
    myCustomStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
    myCustomStageToolbar.revalidate();
    myToolbar.setVisible(myStageView.isToolbarVisible());
    myGoLiveToolbar.setVisible(myStageView.supportsStreaming());

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myStageNavigationToolbar.setVisible(!topLevel && myStageView.supportsStageNavigation());

    myTimelineNavigationToolbar.setVisible(stage.isInteractingWithTimeline());
  }

  private void toggleStageLayout() {
    // Show the loading screen if StudioProfilers is waiting for a process to profile or if it is waiting for an agent to attach.
    boolean loading = (myProfiler.getAutoProfilingEnabled() && myProfiler.getPreferredProcessName() != null) &&
                      !myProfiler.getSessionsManager().isSessionAlive();
    Common.AgentData agentData = myProfiler.getAgentData();
    loading |= (agentData.getStatus() == Common.AgentData.Status.UNSPECIFIED && myProfiler.getSessionsManager().isSessionAlive());

    // Show the loading screen only if the device is supported.
    loading &= (myProfiler.getDevice() != null && myProfiler.getDevice().getUnsupportedReason().isEmpty());

    if (loading) {
      myStageLoadingPanel.startLoading();
      myStageCenterCardLayout.show(myStageCenterComponent, LOADING_VIEW_CARD);
    }
    else {
      myStageLoadingPanel.stopLoading();
      myStageCenterCardLayout.show(myStageCenterComponent, STAGE_VIEW_CARD);
    }
    toggleTimelineButtons();
  }
}
