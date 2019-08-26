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

import com.android.tools.adtui.DragAndDropList;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupListModel;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.trackgroup.TrackGroup;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.ProfilerTrackRendererFactory;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.google.common.annotations.VisibleForTesting;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisPanel;
import com.intellij.ui.JBSplitter;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of a capture taken from within the {@link CpuProfilerStageView}.
 * all captures of type {@link Cpu.CpuTraceType} are supported.
 */
public class CpuCaptureStageView extends StageView<CpuCaptureStage> {
  private static final ProfilerTrackRendererFactory TRACK_RENDERER_FACTORY = new ProfilerTrackRendererFactory();

  private final JList<TrackGroupModel> myTrackGroupList;
  private final CpuAnalysisPanel myAnalysisPanel;
  private final JBSplitter mySplitter = new JBSplitter(false, 0.5f);

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    myTrackGroupList = createTrackGroups(stage.getTrackGroupListModel());
    myAnalysisPanel = new CpuAnalysisPanel(stage);
    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.STATE, this::updateComponents);
    stage.getMinimapModel().getRangeSelectionModel().addDependency(this)
      .onChange(RangeSelectionModel.Aspect.SELECTION, this::updateTrackGroupList);
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
    } else {
      getComponent().add(createAnalyzingComponents());
    }
  }

  private JComponent createAnalyzingComponents() {
    JPanel container = new JPanel(new BorderLayout());
    container.add(new CpuCaptureMinimapView(getStage().getMinimapModel()).getComponent(), BorderLayout.NORTH);
    container.add(myTrackGroupList, BorderLayout.CENTER);
    mySplitter.setFirstComponent(container);
    mySplitter.setSecondComponent(myAnalysisPanel.getComponent());
    return mySplitter;
  }

  private void updateTrackGroupList() {
    // Force JList cell renderer to validate.
    myTrackGroupList.updateUI();
  }

  /**
   * Creates a JList containing all the track groups in this stage.
   */
  private static JList<TrackGroupModel> createTrackGroups(@NotNull TrackGroupListModel trackGroupListModel) {
    // Caches TrackGroupViews for the list cell renderer.
    Map<Integer, TrackGroup> trackGroupMap = new HashMap<>();

    DragAndDropList<TrackGroupModel> trackGroupList = new DragAndDropList<>(trackGroupListModel);
    trackGroupList.setCellRenderer(new ListCellRenderer<TrackGroupModel>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends TrackGroupModel> list,
                                                    TrackGroupModel value,
                                                    int index,
                                                    boolean isSelected,
                                                    boolean cellHasFocus) {
        return trackGroupMap.computeIfAbsent(value.getId(), id -> new TrackGroup(value, TRACK_RENDERER_FACTORY)).getComponent();
      }
    });
    return trackGroupList;
  }

  @VisibleForTesting
  @NotNull
  protected final JList<TrackGroupModel> getTrackGroupList() {
    return myTrackGroupList;
  }

  @VisibleForTesting
  @NotNull
  protected final CpuAnalysisPanel getAnalysisPanel() {
    return myAnalysisPanel;
  }
}
