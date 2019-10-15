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

import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.trackgroup.TrackGroupListPanel;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.ProfilerTrackRendererFactory;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.cpu.analysis.CpuAnalysisPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
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

  public CpuCaptureStageView(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    super(view, stage);
    myTrackGroupList = new TrackGroupListPanel(TRACK_RENDERER_FACTORY);
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
    }
    else {
      getComponent().add(createAnalyzingComponents());
      getComponent().revalidate();
    }
  }

  private JComponent createAnalyzingComponents() {
    myTrackGroupList.loadTrackGroups(getStage().getTrackGroupModels(), true);
    JPanel container = new JPanel(new BorderLayout());
    container.add(new CpuCaptureMinimapView(getStage().getMinimapModel()).getComponent(), BorderLayout.NORTH);
    container.add(
      new JBScrollPane(myTrackGroupList.getComponent(),
                       ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                       ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),
      BorderLayout.CENTER);

    JBSplitter splitter = new JBSplitter(false, 0.5f);
    splitter.setFirstComponent(container);
    splitter.setSecondComponent(myAnalysisPanel.getComponent());
    return splitter;
  }

  private void updateTrackGroupList() {
    // Force track group list to validate its children.
    myTrackGroupList.getComponent().updateUI();
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
