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

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.MultiSelectionModel;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.trackgroup.TrackGroupListPanel;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.ProfilerTrackRendererFactory;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisModel;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisPanel;
import com.android.tools.profilers.cpu.analysis.CpuAnalyzable;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of a capture taken from within the {@link CpuProfilerStageView}.
 * all captures of type {@link Cpu.CpuTraceType} are supported.
 */
public class CpuCaptureStageView extends StageView<CpuCaptureStage> {
  private static final ProfilerTrackRendererFactory TRACK_RENDERER_FACTORY = new ProfilerTrackRendererFactory();

  private final TrackGroupListPanel myTrackGroupList;
  private final CpuAnalysisPanel myAnalysisPanel;
  private final MultiSelectionModel<CpuAnalyzable> myMultiSelectionModel = new MultiSelectionModel<>();

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    myTrackGroupList = new TrackGroupListPanel(TRACK_RENDERER_FACTORY);
    myAnalysisPanel = new CpuAnalysisPanel(view, stage);
    getTooltipBinder().bind(CaptureCpuUsageTooltip.class, CaptureCpuUsageTooltipView::new);

    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.STATE, this::updateComponents);
    myMultiSelectionModel.addDependency(this).onChange(MultiSelectionModel.Aspect.CHANGE_SELECTION, this::onTrackGroupSelectionChange);
    updateComponents();
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
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
    CpuCaptureMinimapModel minimapModel = getStage().getMinimapModel();
    CpuCaptureMinimapView minimap = new CpuCaptureMinimapView(minimapModel);
    RangeTooltipComponent rangeTooltipComponent =
      new RangeTooltipComponent(getStage().getCaptureTimeline(), getTooltipPanel(), getProfilersView().getComponent(), () -> false);
    rangeTooltipComponent.registerListenersOn(minimap.getComponent());
    minimap.getComponent().addMouseListener(
      new ProfilerTooltipMouseAdapter(
        getStage(),
        () -> new CaptureCpuUsageTooltip(minimapModel.getCpuUsage(), getStage().getCaptureTimeline().getTooltipRange())));
    loadTrackGroupModels();

    JPanel container = new JPanel(new TabularLayout("*", "Fit-,*"));
    // The tooltip component should be first so it draws on top of all elements.
    container.add(rangeTooltipComponent, new TabularLayout.Constraint(0, 0, 2, 1));
    container.add(minimap.getComponent(), new TabularLayout.Constraint(0, 0));
    container.add(
      new JBScrollPane(myTrackGroupList.getComponent(),
                       ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                       ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
      new TabularLayout.Constraint(1, 0));

    JBSplitter splitter = new JBSplitter(false, 0.5f);
    splitter.setFirstComponent(container);
    splitter.setSecondComponent(myAnalysisPanel.getComponent());
    return splitter;
  }

  private void updateTrackGroupList() {
    // Force track group list to validate its children.
    myTrackGroupList.getComponent().updateUI();
  }

  private void loadTrackGroupModels() {
    myTrackGroupList.loadTrackGroups(getStage().getTrackGroupModels(), true);
    myTrackGroupList.registerMultiSelectionModel(myMultiSelectionModel);
  }

  private void onTrackGroupSelectionChange() {
    // Remove the last selection if any.
    if (getStage().getAnalysisModels().size() > 1) {
      getStage().getAnalysisModels().remove(getStage().getAnalysisModels().size() - 1);
    }

    // Merge all selected items' analysis models and provide one combined model to the analysis panel.
    myMultiSelectionModel.getSelection().stream()
      .map(CpuAnalyzable::getAnalysisModel)
      .reduce(CpuAnalysisModel::mergeWith)
      .ifPresent(getStage()::addCpuAnalysisModel);
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
