/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.trackgroup.Track;
import com.android.tools.adtui.trackgroup.TrackGroupListPanel;
import com.android.tools.adtui.ui.AdtUiCursors;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.ProfilerTrackRendererFactory;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.analysis.CaptureNodeAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisPanel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip;
import com.android.tools.profilers.cpu.atrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltip;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltipView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of a capture taken from within the {@link CpuProfilerStageView}.
 * all captures of type {@link Cpu.CpuTraceType} are supported.
 */
public class CpuCaptureStageView extends StageView<CpuCaptureStage> {
  /**
   * The percentage of the current view range's length to pan.
   */
  private static final double TIMELINE_PAN_FACTOR = 0.1;
  private static final double TIMELINE_DRAG_FACTOR = 0.001;
  private static final ProfilerTrackRendererFactory TRACK_RENDERER_FACTORY = new ProfilerTrackRendererFactory();

  private final TrackGroupListPanel myTrackGroupList;
  private final CpuAnalysisPanel myAnalysisPanel;
  private final JScrollPane myScrollPane;

  /**
   * To avoid conflict with drag-and-drop, we need a keyboard modifier (e.g. VK_SPACE) to toggle panning mode.
   */
  private boolean myIsPanningMode = false;

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    myTrackGroupList = createTrackGroupListPanel();
    myScrollPane = new JBScrollPane(myTrackGroupList.getComponent(),
                                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myAnalysisPanel = new CpuAnalysisPanel(view, stage);

    // Tooltip used in the stage
    getTooltipBinder().bind(CpuCaptureStageCpuUsageTooltip.class, CpuCaptureStageCpuUsageTooltipView::new);

    // Tooltips used in the track groups
    myTrackGroupList.getTooltipBinder().bind(CpuFrameTooltip.class, CpuFrameTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuCaptureNodeTooltip.class, CpuCaptureNodeTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuKernelTooltip.class, CpuKernelTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(UserEventTooltip.class, UserEventTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(LifecycleTooltip.class, LifecycleTooltipView::new);

    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.STATE, this::updateComponents);
    stage.getMultiSelectionModel().addDependency(this)
      .onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, this::onTrackGroupSelectionChange);
    updateComponents();
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean shouldEnableZoomToSelection() {
    // Zoom to Selection only works for trace event (i.e. CaptureNodeAnalysisModel) selection.
    ImmutableList<CpuAnalyzable> selection = getStage().getMultiSelectionModel().getSelection();
    return !selection.isEmpty() && selection.get(0) instanceof CaptureNodeAnalysisModel;
  }

  @NotNull
  @Override
  public Range getZoomToSelectionRange() {
    assert shouldEnableZoomToSelection();
    // Zoom to Selection works on the range of the selected trace event.
    CaptureNodeAnalysisModel selectedNode = (CaptureNodeAnalysisModel)getStage().getMultiSelectionModel().getSelection().get(0);
    return selectedNode.getNodeRange();
  }

  @Override
  public void addTimelineControlUpdater(@NotNull Runnable timelineControlUpdater) {
    getStage().getMultiSelectionModel().addDependency(getProfilersView()).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION,
                                                                                   timelineControlUpdater);
  }

  @Override
  public boolean shouldShowDeselectAllLabel() {
    return !getStage().getMultiSelectionModel().getSelection().isEmpty();
  }

  @Override
  public void onDeselectAllAction() {
    getStage().getMultiSelectionModel().clearSelection();
  }

  private void updateComponents() {
    getComponent().removeAll();
    if (getStage().getState() == CpuCaptureStage.State.PARSING) {
      getComponent().add(new StatusPanel(getStage().getCaptureHandler(), "Parsing", "Abort"));
    }
    else {
      // If we had any previously registered analyzing events we unregister them first.
      // Note: This should only be done in the analyzing state since objects may not be created/setup before the model has transitioned
      // to this state.
      unregisterAnalyzingEvents();
      registerAnalyzingEvents();
      getComponent().add(createAnalyzingComponents());
      getComponent().revalidate();
      // Request focus to enable keyboard shortcuts once loading finishes.
      myTrackGroupList.getComponent().requestFocusInWindow();
    }
  }

  /**
   * Helper function for registering listeners on objects that may not be initialized until the capture has been parsed.
   */
  private void registerAnalyzingEvents() {
    getStage().getMinimapModel().getRangeSelectionModel().addDependency(this)
      .onChange(RangeSelectionModel.Aspect.SELECTION, this::updateTrackGroupList);
  }

  /**
   * Helper function for unregistering listeners that get set when we enter the analyzing state for a capture.
   */
  private void unregisterAnalyzingEvents() {
    getStage().getMinimapModel().getRangeSelectionModel().removeDependencies(this);
  }

  private JComponent createAnalyzingComponents() {
    // Minimap
    CpuCaptureMinimapModel minimapModel = getStage().getMinimapModel();
    CpuCaptureMinimapView minimap = new CpuCaptureMinimapView(minimapModel);
    // Minimap tooltip uses the capture timeline
    RangeTooltipComponent minimapTooltipComponent =
      new RangeTooltipComponent(getStage().getCaptureTimeline(),
                                getTooltipPanel(),
                                getProfilersView().getComponent(),
                                () -> false);
    minimap.registerRangeTooltipComponent(minimapTooltipComponent);
    minimap.addMouseListener(
      new ProfilerTooltipMouseAdapter(
        getStage(),
        () -> new CpuCaptureStageCpuUsageTooltip(minimapModel.getCpuUsage(), getStage().getCaptureTimeline().getTooltipRange())));

    // Track Groups
    loadTrackGroupModels();

    JPanel container = new JPanel(new TabularLayout("*", "Fit-,*"));
    // The tooltip component should be first so it draws on top of all elements. It's only responsible for showing tooltip in the minimap.
    container.add(minimapTooltipComponent, new TabularLayout.Constraint(0, 0));
    container.add(minimap.getComponent(), new TabularLayout.Constraint(0, 0));
    container.add(myScrollPane, new TabularLayout.Constraint(1, 0));
    container.add(createBottomAxisPanel(minimapModel.getRangeSelectionModel().getSelectionRange()), new TabularLayout.Constraint(2, 0));

    JBSplitter splitter = new JBSplitter(false, 0.5f);
    splitter.setFirstComponent(container);
    splitter.setSecondComponent(myAnalysisPanel.getComponent());
    splitter.getDivider().setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 1, 0, 1));
    return splitter;
  }

  @NotNull
  private TrackGroupListPanel createTrackGroupListPanel() {
    TrackGroupListPanel trackGroupListPanel = new TrackGroupListPanel(TRACK_RENDERER_FACTORY);
    MouseAdapter mouseListener = new MouseAdapter() {
      private int myLastX = 0;

      @Override
      public void mousePressed(MouseEvent e) {
        if (myIsPanningMode) {
          myLastX = e.getX();
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (myIsPanningMode) {
          // Panning is opposite direction (i.e. pan left to shift range right).
          int deltaX = myLastX - e.getX();
          myLastX = e.getX();
          getStage().getTimeline().panView(getStage().getTimeline().getViewRange().getLength() * deltaX * TIMELINE_DRAG_FACTOR);
        }
      }
    };
    trackGroupListPanel.getComponent().addMouseListener(mouseListener);
    trackGroupListPanel.getComponent().addMouseMotionListener(mouseListener);
    trackGroupListPanel.getComponent().addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        if (AdtUiUtils.isActionKeyDown(e)) {
          if (e.getWheelRotation() > 0) {
            getStage().getTimeline().zoomOut();
          }
          else {
            getStage().getTimeline().zoomIn();
          }
        }
        else {
          // Ctrl/Cmd key is not pressed, dispatch the wheel event to the scroll pane for scrolling to work.
          e.setSource(myScrollPane);
          myScrollPane.dispatchEvent(e);
        }
      }
    });
    trackGroupListPanel.getComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
          case KeyEvent.VK_W:
            // VK_UP is used by JList to move selection up by default.
            getStage().getTimeline().zoomIn();
            break;
          case KeyEvent.VK_S:
            // VK_DOWN is used by JList to move selection down by default.
            getStage().getTimeline().zoomOut();
            break;
          case KeyEvent.VK_A:
          case KeyEvent.VK_LEFT:
            getStage().getTimeline().panView(-getStage().getTimeline().getViewRange().getLength() * TIMELINE_PAN_FACTOR);
            break;
          case KeyEvent.VK_D:
          case KeyEvent.VK_RIGHT:
            getStage().getTimeline().panView(getStage().getTimeline().getViewRange().getLength() * TIMELINE_PAN_FACTOR);
            break;
          case KeyEvent.VK_SPACE:
            setPanningMode(true, trackGroupListPanel);
            break;
        }
      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
          setPanningMode(false, trackGroupListPanel);
        }
      }
    });
    return trackGroupListPanel;
  }

  private void setPanningMode(boolean isPanningMode, @NotNull TrackGroupListPanel trackGroupListPanel) {
    myIsPanningMode = isPanningMode;
    trackGroupListPanel.setEnabled(!isPanningMode);
    getProfilersView().getComponent().setCursor(isPanningMode ? AdtUiCursors.GRABBING : null);
  }

  private static JComponent createBottomAxisPanel(@NotNull Range range) {
    // Match track's column sizing.
    JPanel axisPanel = new JPanel(new TabularLayout(Track.COL_SIZES));
    axisPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    AxisComponent timeAxis = new AxisComponent(new ResizingAxisComponentModel.Builder(range, TimeAxisFormatter.DEFAULT).build(),
                                               AxisComponent.AxisOrientation.BOTTOM);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    // Hide the axis line so it doesn't stack with panel border.
    timeAxis.setShowAxisLine(false);
    // Align with track content.
    axisPanel.add(timeAxis, new TabularLayout.Constraint(0, 1));
    return axisPanel;
  }

  private void updateTrackGroupList() {
    // Force track group list to validate its children.
    myTrackGroupList.getComponent().updateUI();
  }

  private void loadTrackGroupModels() {
    // Track groups tooltip uses the track group timeline (based on minimap selection)
    myTrackGroupList.setRangeTooltipComponent(
      new RangeTooltipComponent(getStage().getTimeline(),
                                myTrackGroupList.getTooltipPanel(),
                                getProfilersView().getComponent(),
                                () -> false));
    myTrackGroupList.loadTrackGroups(getStage().getTrackGroupModels(), true);
    myTrackGroupList.registerMultiSelectionModel(getStage().getMultiSelectionModel());
  }

  private void onTrackGroupSelectionChange() {
    // Remove the last selection if any.
    if (getStage().getAnalysisModels().size() > 1) {
      getStage().removeCpuAnalysisModel(getStage().getAnalysisModels().size() - 1);
    }

    // Merge all selected items' analysis models and provide one combined model to the analysis panel.
    getStage().getMultiSelectionModel().getSelection().stream()
      .map(CpuAnalyzable::getAnalysisModel)
      .reduce(CpuAnalysisModel::mergeWith)
      .ifPresent(getStage()::addCpuAnalysisModel);

    // Now update track groups.
    updateTrackGroupList();
  }

  @VisibleForTesting
  @NotNull
  protected final TrackGroupListPanel getTrackGroupList() {
    return myTrackGroupList;
  }

  @VisibleForTesting
  @NotNull
  protected final CpuAnalysisPanel getAnalysisPanel() {
    return myAnalysisPanel;
  }
}
