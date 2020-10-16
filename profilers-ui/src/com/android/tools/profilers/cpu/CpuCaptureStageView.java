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
import com.android.tools.adtui.common.AdtUiCursorType;
import com.android.tools.adtui.common.AdtUiCursorsProvider;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.trackgroup.Track;
import com.android.tools.adtui.trackgroup.TrackGroupListPanel;
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
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltip;
import com.android.tools.profilers.cpu.capturedetails.CpuCaptureNodeTooltipView;
import com.android.tools.profilers.cpu.systemtrace.BufferQueueTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFrameTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuFrequencyTooltip;
import com.android.tools.profilers.cpu.systemtrace.CpuKernelTooltip;
import com.android.tools.profilers.cpu.systemtrace.RssMemoryTooltip;
import com.android.tools.profilers.cpu.systemtrace.SurfaceflingerTooltip;
import com.android.tools.profilers.cpu.systemtrace.VsyncTooltip;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
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

  /**
   * Key binding keys.
   */
  private static final String ZOOM_IN_KEY = "zoom_in";
  private static final String ZOOM_OUT_KEY = "zoom_out";
  private static final String PAN_LEFT_KEY = "pan_left";
  private static final String PAN_RIGHT_KEY = "pan_right";
  private static final String PANNING_MODE_ON_KEY = "panning_mode_on";
  private static final String PANNING_MODE_OFF_KEY = "panning_mode_off";

  private final ProfilerTrackRendererFactory myTrackRendererFactory;
  private final TrackGroupListPanel myTrackGroupList;
  private final CpuAnalysisPanel myAnalysisPanel;
  private final JScrollPane myScrollPane;
  private final LinkLabel<?> myDeselectAllLabel;
  private final JPanel myDeselectAllToolbar;

  /**
   * To avoid conflict with drag-and-drop, we need a keyboard modifier (e.g. VK_SPACE) to toggle panning mode.
   */
  private boolean myIsPanningMode = false;

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    myTrackRendererFactory = new ProfilerTrackRendererFactory(getProfilersView());
    myTrackGroupList = createTrackGroupListPanel();
    myScrollPane = new JBScrollPane(myTrackGroupList.getComponent(),
                                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setBorder(JBUI.Borders.empty());
    myAnalysisPanel = new CpuAnalysisPanel(view, stage);
    myDeselectAllToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myDeselectAllLabel = createDeselectAllLabel();

    // Tooltip used in the stage
    getTooltipBinder().bind(CpuCaptureStageCpuUsageTooltip.class, CpuCaptureStageCpuUsageTooltipView::new);

    // Tooltips used in the track groups
    myTrackGroupList.getTooltipBinder().bind(CpuFrameTooltip.class, CpuFrameTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuThreadsTooltip.class, CpuThreadsTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuCaptureNodeTooltip.class, CpuCaptureNodeTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuKernelTooltip.class, CpuKernelTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(UserEventTooltip.class, UserEventTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(LifecycleTooltip.class, LifecycleTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(SurfaceflingerTooltip.class, SurfaceflingerTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(VsyncTooltip.class, VsyncTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(BufferQueueTooltip.class, BufferQueueTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(RssMemoryTooltip.class, RssMemoryTooltipView::new);
    myTrackGroupList.getTooltipBinder().bind(CpuFrequencyTooltip.class, CpuFrequencyTooltipView::new);

    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.STATE, this::updateComponents);
    stage.getMultiSelectionModel().addDependency(this)
      .onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, this::onTrackGroupSelectionChange);
    updateComponents();
  }

  @Override
  public JComponent getToolbar() {
    // The Deselect All toolbar is only visible when something is selected.
    JPanel panel = new JPanel(new BorderLayout());
    myDeselectAllToolbar.add(myDeselectAllLabel);
    myDeselectAllToolbar.add(new FlatSeparator());
    myDeselectAllToolbar.setVisible(false);
    panel.add(myDeselectAllToolbar, BorderLayout.EAST);
    return panel;
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

    // Repaint track groups when the root nodes' filters changed.
    getStage().getCapture().getCaptureNodes().forEach(node -> node.getAspectModel().addDependency(this)
      .onChange(CaptureNode.Aspect.FILTER_APPLIED, this::updateTrackGroupList));
  }

  /**
   * Helper function for unregistering listeners that get set when we enter the analyzing state for a capture.
   */
  private void unregisterAnalyzingEvents() {
    getStage().getMinimapModel().getRangeSelectionModel().removeDependencies(this);
    getStage().getCapture().getCaptureNodes().forEach(node -> node.getAspectModel().removeDependencies(this));
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
    initKeyBindings(container);

    JBSplitter splitter = new JBSplitter(false, 0.5f);
    splitter.setFirstComponent(container);
    splitter.setSecondComponent(myAnalysisPanel.getComponent());
    splitter.getDivider().setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 1, 0, 1));
    return splitter;
  }

  private void initKeyBindings(@NotNull JComponent container) {
    InputMap inputMap = container.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    ActionMap actionMap = container.getActionMap();

    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), ZOOM_IN_KEY);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), ZOOM_OUT_KEY);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), PAN_LEFT_KEY);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), PAN_RIGHT_KEY);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), PANNING_MODE_ON_KEY);
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), PANNING_MODE_OFF_KEY);

    actionMap.put(ZOOM_IN_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getStage().getTimeline().zoomIn();
        getProfilersView().getStudioProfilers().getIdeServices().getFeatureTracker().trackZoomIn();
      }
    });
    actionMap.put(ZOOM_OUT_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getStage().getTimeline().zoomOut();
        getProfilersView().getStudioProfilers().getIdeServices().getFeatureTracker().trackZoomOut();
      }
    });
    actionMap.put(PAN_LEFT_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getStage().getTimeline().panView(-getStage().getTimeline().getViewRange().getLength() * TIMELINE_PAN_FACTOR);
      }
    });
    actionMap.put(PAN_RIGHT_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getStage().getTimeline().panView(getStage().getTimeline().getViewRange().getLength() * TIMELINE_PAN_FACTOR);
      }
    });
    actionMap.put(PANNING_MODE_ON_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setPanningMode(true, myTrackGroupList);
      }
    });
    actionMap.put(PANNING_MODE_OFF_KEY, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setPanningMode(false, myTrackGroupList);
      }
    });
  }

  @NotNull
  private TrackGroupListPanel createTrackGroupListPanel() {
    TrackGroupListPanel trackGroupListPanel = new TrackGroupListPanel(myTrackRendererFactory);
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
            getProfilersView().getStudioProfilers().getIdeServices().getFeatureTracker().trackZoomOut();
          }
          else {
            getStage().getTimeline().zoomIn();
            getProfilersView().getStudioProfilers().getIdeServices().getFeatureTracker().trackZoomIn();
          }
        }
        else {
          // Ctrl/Cmd key is not pressed, dispatch the wheel event to the scroll pane for scrolling to work.
          e.setSource(myScrollPane);
          myScrollPane.dispatchEvent(e);
        }
      }
    });
    return trackGroupListPanel;
  }

  private void setPanningMode(boolean isPanningMode, @NotNull TrackGroupListPanel trackGroupListPanel) {
    myIsPanningMode = isPanningMode;
    trackGroupListPanel.setEnabled(!isPanningMode);
    getProfilersView().getComponent().setCursor(isPanningMode ? AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING) : null);
  }

  private static JComponent createBottomAxisPanel(@NotNull Range range) {
    // Match track's column sizing.
    JPanel axisPanel = new JPanel(new TabularLayout(Track.COL_SIZES));
    axisPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    AxisComponent timeAxis = new AxisComponent(new ResizingAxisComponentModel.Builder(range, TimeAxisFormatter.DEFAULT).build(),
                                               AxisComponent.AxisOrientation.BOTTOM);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    // Align with track content.
    axisPanel.add(timeAxis, new TabularLayout.Constraint(0, 1));
    return axisPanel;
  }

  private LinkLabel<?> createDeselectAllLabel() {
    LinkLabel<?> label = LinkLabel.create("Clear thread/event selection", () -> getStage().getMultiSelectionModel().clearSelection());
    label.setBorder(new JBEmptyBorder(0, 0, 0, 4));
    label.setToolTipText("Click to deselect all threads/events");
    return label;
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
    ImmutableList<CpuAnalyzable> selection = getStage().getMultiSelectionModel().getSelection();
    getStage().getMultiSelectionModel().getSelection().stream()
      .map(CpuAnalyzable::getAnalysisModel)
      .reduce(CpuAnalysisModel::mergeWith)
      .ifPresent(getStage()::addCpuAnalysisModel);

    // Now update track groups.
    updateTrackGroupList();

    // Update selection range
    if (selection.isEmpty()) {
      getStage().getTimeline().getSelectionRange().clear();
    }
    else if (selection.get(0) instanceof CaptureNodeAnalysisModel) {
      // Use the first item to determine selection type as all items in the selection model are of the same type.
      Range selectedNodeRange = ((CaptureNodeAnalysisModel)selection.get(0)).getNodeRange();
      getStage().getTimeline().getSelectionRange().set(selectedNodeRange);
    }

    // Show/hide "Deselect All" label.
    myDeselectAllToolbar.setVisible(!selection.isEmpty());
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

  @VisibleForTesting
  LinkLabel<?> getDeselectAllLabel() {
    return myDeselectAllLabel;
  }

  @VisibleForTesting
  JPanel getDeselectAllToolbar() {
    return myDeselectAllToolbar;
  }
}
