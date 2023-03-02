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
package com.android.tools.profilers;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerFonts.H4_FONT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static com.android.tools.profilers.sessions.SessionsView.SESSION_EXPANDED_WIDTH;
import static com.android.tools.profilers.sessions.SessionsView.SESSION_IS_COLLAPSED;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.adtui.stdui.TooltipLayeredPane;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.android.tools.profilers.cpu.CpuCaptureStageView;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStageView;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.energy.EnergyProfilerStageView;
import com.android.tools.profilers.memory.AllocationStage;
import com.android.tools.profilers.memory.AllocationStageView;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.MainMemoryProfilerStageView;
import com.android.tools.profilers.memory.MemoryCaptureStage;
import com.android.tools.profilers.memory.MemoryCaptureStageView;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsView;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

public class StudioProfilersView extends AspectObserver implements Disposable {
  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  @NotNull public static final String ATTACH_LIVE = "Attach to live";
  @NotNull public static final String DETACH_LIVE = "Detach live";
  @NotNull public static final String ZOOM_IN = "Zoom in";
  @NotNull public static final String ZOOM_OUT = "Zoom out";

  private final StudioProfilers myProfiler;
  private final ViewBinder<StudioProfilersView, Stage, StageView> myBinder;
  private StageView myStageView;

  @NotNull
  private final TooltipLayeredPane myLayeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter mySplitter;
  @NotNull private final LoadingPanel myStageLoadingPanel;
  private final JPanel myStageComponent;
  private final JPanel myStageCenterComponent;
  private final CardLayout myStageCenterCardLayout;
  private SessionsView mySessionsView;
  private JPanel myToolbar;
  private JPanel myStageToolbar;
  private JPanel myCommonToolbar;
  private JPanel myGoLiveToolbar;
  private JPanel myRightToolbar;
  private JToggleButton myGoLive;
  private CommonButton myZoomOut;
  private CommonButton myZoomIn;
  private CommonButton myResetZoom;
  private CommonButton myZoomToSelection;
  private CommonButton myBack;
  private DefaultContextMenuItem myZoomToSelectionAction;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  public StudioProfilersView(@NotNull StudioProfilers profiler, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;
    myStageView = null;
    myStageComponent = new JPanel(new BorderLayout());
    myStageCenterCardLayout = new CardLayout();
    myStageCenterComponent = new JPanel(myStageCenterCardLayout);

    myStageLoadingPanel = myIdeProfilerComponents.createLoadingPanel(0);
    myStageLoadingPanel.setLoadingText("");
    myStageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    mySplitter = new ThreeComponentsSplitter(this);
    // Override the splitter's custom traversal policy back to the default, because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    mySplitter.setDividerWidth(0);
    mySplitter.setDividerMouseZoneSize(-1);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLastComponent(myStageComponent);

    myLayeredPane = new TooltipLayeredPane(mySplitter);
    initializeSessionUi();
    initializeStageUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(CpuCaptureStage.class, CpuCaptureStageView::new);
    myBinder.bind(MainMemoryProfilerStage.class, MainMemoryProfilerStageView::new);
    myBinder.bind(MemoryCaptureStage.class, MemoryCaptureStageView::new);
    myBinder.bind(AllocationStage.class, AllocationStageView::new);
    myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new);
    myBinder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);
    myBinder.bind(CustomEventProfilerStage.class, CustomEventProfilerStageView::new);

    myProfiler.addDependency(this)
      .onChange(ProfilerAspect.STAGE, this::updateStageView)
      .onChange(ProfilerAspect.AGENT, this::toggleStageLayout)
      .onChange(ProfilerAspect.PREFERRED_PROCESS, this::toggleStageLayout);
    updateStageView();
    toggleStageLayout();
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return myZoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return myZoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return myResetZoom;
  }

  @VisibleForTesting
  @NotNull
  public CommonButton getZoomToSelectionButton() {
    return myZoomToSelection;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return myGoLive;
  }

  @VisibleForTesting
  @NotNull
  public CommonButton getBackButton() {
    return myBack;
  }

  @VisibleForTesting
  public StageView getStageView() {
    return myStageView;
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return mySessionsView;
  }

  @VisibleForTesting
  @NotNull
  JPanel getRightToolbar() {
    return myRightToolbar;
  }

  @NotNull
  public StudioProfilers getStudioProfilers() {
    return myProfiler;
  }

  private void initializeSessionUi() {
    mySessionsView = new SessionsView(myProfiler, myIdeProfilerComponents);
    JComponent sessionsComponent = mySessionsView.getComponent();
    mySplitter.setFirstComponent(sessionsComponent);
    mySessionsView.addExpandListener(e -> {
      toggleSessionsPanel(false);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(true);
    });
    mySessionsView.addCollapseListener(e -> {
      toggleSessionsPanel(true);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(false);
    });
    boolean initiallyCollapsed =
      myProfiler.getIdeServices().getPersistentProfilerPreferences().getBoolean(SESSION_IS_COLLAPSED, false);
    toggleSessionsPanel(initiallyCollapsed);

    // Track Sessions UI resize event.
    // The divider mechanism within ThreeComponentsSplitter consumes the mouse event so we cannot use regular mouse listeners on the
    // splitter itself. Instead, we mirror the logic that the divider uses to capture mouse event and check whether the width of the
    // sessions UI has changed between mouse press and release. Using Once here to mimic ThreeComponentsSplitter's implementation, as
    // we only need to add the MousePreprocessor to the glassPane once when the UI shows up.
    UiNotifyConnector.Once.installOn(mySplitter, new Activatable() {
      @Override
      public void showNotify() {
        IdeGlassPane glassPane = IdeGlassPaneUtil.find(mySplitter);
        glassPane.addMousePreprocessor(new MouseAdapter() {
          private int mySessionsUiWidth;

          @Override
          public void mousePressed(MouseEvent e) {
            mySessionsUiWidth = sessionsComponent.getWidth();
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            int width = sessionsComponent.getWidth();
            if (mySessionsUiWidth != width) {
              myProfiler.getIdeServices().getPersistentProfilerPreferences().setInt(SESSION_EXPANDED_WIDTH, width);
              myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelResized();
            }
          }
        }, StudioProfilersView.this);
      }
    });
  }

  private void initializeStageUi() {
    myToolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    myToolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    myToolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    myCommonToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myBack = new CommonButton(AllIcons.Actions.Back);
    myBack.addActionListener(action -> confirmExit("Go back?", () -> {
      myProfiler.setStage(myProfiler.getStage().getParentStage());
      myProfiler.getIdeServices().getFeatureTracker().trackGoBack();
    }));
    myCommonToolbar.add(myBack);
    myCommonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends Stage>> stageCombo = new FlatComboBox<>();
    Supplier<List<Class<? extends Stage>>> getSupportedStages = () -> getStudioProfilers().getDirectStages().stream()
      .filter(st -> getStudioProfilers().getSelectedSessionSupportLevel().isStageSupported((Class<? extends Stage<?>>)st))
      .collect(Collectors.toList());
    JComboBoxView stages = new JComboBoxView<>(stageCombo, myProfiler, ProfilerAspect.STAGE,
                                               getSupportedStages,
                                               myProfiler::getStageClass,
                                               stage -> confirmExit("Exit?", () -> {
                                                 // Track first, so current stage is sent with the event
                                                 myProfiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 myProfiler.setNewStage(stage);
                                               }),
                                               () -> myProfiler.getStage().getHomeStageClass());
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    myCommonToolbar.add(stageCombo);
    myCommonToolbar.add(new FlatSeparator());
    leftToolbar.add(myCommonToolbar);
    myToolbar.add(leftToolbar, BorderLayout.WEST);

    myRightToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myToolbar.add(myRightToolbar, BorderLayout.EAST);
    myRightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    myZoomOut = new CommonButton(AllIcons.General.ZoomOut);
    myZoomOut.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomOut));
    myZoomOut.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomOut();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    DefaultContextMenuItem zoomOutAction =
      new DefaultContextMenuItem.Builder(ZOOM_OUT).setContainerComponent(mySplitter).setActionRunnable(() -> myZoomOut.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();

    myZoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    myRightToolbar.add(myZoomOut);

    myZoomIn = new CommonButton(AllIcons.General.ZoomIn);
    myZoomIn.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomIn));
    myZoomIn.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomIn();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    DefaultContextMenuItem zoomInAction =
      new DefaultContextMenuItem.Builder(ZOOM_IN).setContainerComponent(mySplitter)
        .setActionRunnable(() -> myZoomIn.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER)).build();
    myZoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    myRightToolbar.add(myZoomIn);

    myResetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    myResetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    myResetZoom.addActionListener(event -> {
      myStageView.getStage().getTimeline().resetZoom();
      myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    DefaultContextMenuItem resetZoomAction =
      new DefaultContextMenuItem.Builder("Reset zoom").setContainerComponent(mySplitter)
        .setActionRunnable(() -> myResetZoom.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                       KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    myResetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    myRightToolbar.add(myResetZoom);

    myZoomToSelection = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    myZoomToSelection.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    myZoomToSelection.addActionListener(event -> {
      myStageView.getStage().getTimeline().frameViewToRange(myStageView.getStage().getTimeline().getSelectionRange());
      myProfiler.getIdeServices().getFeatureTracker().trackZoomToSelection();
    });
    myZoomToSelectionAction = new DefaultContextMenuItem.Builder("Zoom to Selection")
      .setContainerComponent(mySplitter)
      .setActionRunnable(() -> myZoomToSelection.doClick(0))
      .setEnableBooleanSupplier(() -> myStageView != null && !myStageView.getStage().getTimeline().getSelectionRange().isEmpty())
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_M, 0))
      .build();
    myZoomToSelection.setToolTipText(myZoomToSelectionAction.getDefaultToolTipText());
    myRightToolbar.add(myZoomToSelection);

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
      new DefaultContextMenuItem.Builder(ATTACH_LIVE).setContainerComponent(mySplitter)
        .setActionRunnable(() -> myGoLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> myGoLive.isEnabled() &&
                !myGoLive.isSelected() &&
                myStageView.supportsStreaming())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();
    DefaultContextMenuItem detachAction =
      new DefaultContextMenuItem.Builder(DETACH_LIVE).setContainerComponent(mySplitter)
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
    myRightToolbar.add(myGoLiveToolbar);

    ProfilerContextMenu.createIfAbsent(myStageComponent)
      .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    myProfiler.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, this::toggleTimelineButtons);
    toggleTimelineButtons();

    myStageToolbar = new JPanel(new BorderLayout());
    myToolbar.add(myStageToolbar, BorderLayout.CENTER);

    myStageComponent.add(myToolbar, BorderLayout.NORTH);
    myStageComponent.add(myStageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void confirmExit(String title, Runnable exit) {
    String msg = myProfiler.getStage().getConfirmExitMessage();
    if (msg != null) {
      getStudioProfilers().getIdeServices().openYesNoDialog(msg, title, exit, () -> {});
    } else {
      exit.run();
    }
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
        myZoomToSelection.setEnabled(false);
        myGoLive.setEnabled(false);
        myGoLive.setSelected(false);
      }
      else {
        myZoomOut.setEnabled(true);
        myZoomIn.setEnabled(true);
        myResetZoom.setEnabled(true);
        myZoomToSelection.setEnabled(myZoomToSelectionAction.isEnabled());
        myGoLive.setEnabled(true);
        myGoLive.setSelected(true);
      }
    }
    else {
      boolean isValidSession = !Common.Session.getDefaultInstance().equals(myProfiler.getSessionsManager().getSelectedSession());
      myZoomOut.setEnabled(isValidSession);
      myZoomIn.setEnabled(isValidSession);
      myResetZoom.setEnabled(isValidSession);
      myZoomToSelection.setEnabled(isValidSession && myZoomToSelectionAction.isEnabled());
      myGoLive.setEnabled(false);
      myGoLive.setSelected(false);
    }
  }

  private void toggleSessionsPanel(boolean isCollapsed) {
    if (isCollapsed) {
      mySplitter.setDividerMouseZoneSize(-1);
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(false));
      // Let the Sessions panel min size govern how much space to reserve on the left.
      mySplitter.setFirstSize(0);
    }
    else {
      mySplitter.setDividerMouseZoneSize(JBUIScale.scale(10));
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(true));
      mySplitter
        .setFirstSize(myProfiler.getIdeServices().getPersistentProfilerPreferences().getInt(SESSION_EXPANDED_WIDTH, 0));
    }

    mySplitter.revalidate();
    mySplitter.repaint();
  }

  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    Stage stage = myProfiler.getStage();
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    if (myStageView != null) {
      myStageView.getStage().getTimeline().getSelectionRange().removeDependencies(this);
    }
    myStageView = myBinder.build(this, stage);
    myStageView.getStage().getTimeline().getSelectionRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> {
      myZoomToSelection.setEnabled(myZoomToSelectionAction.isEnabled());
    });
    SwingUtilities.invokeLater(() -> {
      Component focussed = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (focussed == null || !SwingUtilities.isDescendingFrom(focussed, mySplitter)) {
        mySplitter.requestFocusInWindow();
      }
    });

    myStageCenterComponent.removeAll();
    myStageCenterComponent.add(myStageView.getComponent(), STAGE_VIEW_CARD);
    myStageCenterComponent.add(myStageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    myStageCenterComponent.revalidate();
    myStageToolbar.removeAll();
    myStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
    myStageToolbar.revalidate();
    myToolbar.setVisible(myStageView.isToolbarVisible());
    myGoLiveToolbar.setVisible(myStageView.supportsStreaming());

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myCommonToolbar.setVisible(!topLevel && myStageView.supportsStageNavigation());

    myRightToolbar.setVisible(stage.isInteractingWithTimeline());
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

  @NotNull
  public JLayeredPane getComponent() {
    return myLayeredPane;
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   *
   * @param component
   */
  public void installCommonMenuItems(@NotNull JComponent component) {
    ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    ProfilerContextMenu.createIfAbsent(myStageComponent).getContextMenuItems()
      .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }

  @VisibleForTesting
  final JPanel getStageComponent() {
    return myStageComponent;
  }

  @VisibleForTesting
  final JComponent getStageLoadingComponent() {
    return myStageLoadingPanel.getComponent();
  }

  @VisibleForTesting
  final JComponent getStageViewComponent() {
    return myStageView.getComponent();
  }

  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {
    private static final ImmutableMap<Class<? extends Stage>, String> CLASS_TO_NAME = ImmutableMap.of(
      CpuProfilerStage.class, "CPU",
      MainMemoryProfilerStage.class, "MEMORY",
      EnergyProfilerStage.class, "ENERGY",
      CustomEventProfilerStage.class, "CUSTOM EVENTS");

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }


  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }

  @VisibleForTesting
  public JPanel getCommonToolbar() {
    return myCommonToolbar;
  }
}
