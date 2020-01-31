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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.*;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

public class CpuCaptureView {
  @NotNull
  private final CpuProfilerStageView myStageView;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private final JPanel myPanel;

  @NotNull
  private CapturePane myCapturePane;

  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private final AspectObserver myObserver;

  public CpuCaptureView(@NotNull CpuProfilerStageView stageView) {
    myStageView = stageView;
    myStage = stageView.getStage();
    myPanel = new JPanel(new BorderLayout());
    myObserver = new AspectObserver();
    myCapturePane = createCapturePane();

    myStage.getAspect().addDependency(myObserver)
      .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
      .onChange(CpuProfilerAspect.CAPTURE_STATE, this::onCaptureStateChanged)
      .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCapturePane);
    myStage.getCaptureParser().getAspect().addDependency(myObserver).onChange(CpuProfilerAspect.CAPTURE_PARSING, this::updateCapturePane);
    updateCapturePane();
  }

  private void updateCaptureDetails() {
    myCapturePane.updateView();
  }

  private void onCaptureStateChanged() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.STARTING
        || myStage.getCaptureState() == CpuProfilerStage.CaptureState.STOPPING) {
      // STARTING and STOPPING shouldn't change the panel displayed, so we return early.
      return;
    }

    updateCapturePane();
  }

  private void updateCapturePane() {
    myPanel.removeAll();
    myCapturePane = createCapturePane();
    myPanel.add(myCapturePane, BorderLayout.CENTER);
    myPanel.revalidate();
  }

  @NotNull
  private CapturePane createCapturePane() {
    if (myStage.getCaptureParser().isParsing()) {
      return new ParsingPane(myStageView);
    }

    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      return new RecordingPane(myStageView);
    }

    if (myStage.getCapture() == null) {
      return new RecordingInitiatorPane(myStageView);
    }
    else {
      return new DetailsCapturePane(myStageView);
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  /**
   * A {@link StatusPane} representing the {@link CpuProfilerStage.CaptureState#CAPTURING} state.
   */
  @VisibleForTesting
  static class RecordingPane extends CapturePane {

    private final StatusPanel myPanel;
    private final AspectObserver myObserver = new AspectObserver();

    RecordingPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView);
      myPanel = new StatusPanel(new CpuCaptureViewStatusModel(stageView.getStage()), "Recording", CpuProfilerToolbar.STOP_TEXT);
      myPanel.setAbortButtonEnabled(!stageView.getStage().isApiInitiatedTracingInProgress());
      // Disable the stop recording button on state transition.
      stageView.getStage().getAspect().addDependency(myObserver)
        .onChange(CpuProfilerAspect.CAPTURE_STATE, () -> myPanel.setAbortButtonEnabled(false));
      disableInteraction();
      updateView();
    }

    @Override
    void populateContent(@NotNull JPanel panel) {
      panel.add(myPanel, BorderLayout.CENTER);
    }
  }

  /**
   * A {@link StatusPane} representing the {@link CpuCaptureParser#isParsing()} state.
   */
  @VisibleForTesting
  static class ParsingPane extends CapturePane {
    static final String ABORT_BUTTON_TEXT = "Abort";

    ParsingPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView);
      disableInteraction();
      updateView();
    }

    @Override
    void populateContent(@NotNull JPanel panel) {
      panel.add(new StatusPanel(new CpuParsingViewStatusModel(myStageView.getStage()), "Parsing", ABORT_BUTTON_TEXT), BorderLayout.CENTER);
    }
  }

  static class CpuParsingViewStatusModel extends CpuCaptureViewStatusModel {
    CpuParsingViewStatusModel(CpuProfilerStage stage) {
      super(stage);
    }

    @Override
    public void abort() {
      myStage.getCaptureParser().abortParsing();
      myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
    }

    @Override
    protected void updateDuration() {
      myRange.setMax(TimeUnit.MILLISECONDS.toNanos(myStage.getCaptureParser().getParsingElapsedTimeMs()));
    }
  }

  static class CpuCaptureViewStatusModel implements StatusPanelModel {
    protected Range myRange = new Range(0, 0);
    protected CpuProfilerStage myStage;
    private AspectObserver myObserver = new AspectObserver();

    CpuCaptureViewStatusModel(CpuProfilerStage stage) {
      myStage = stage;
      myStage.getAspect().addDependency(myObserver)
        .onChange(CpuProfilerAspect.CAPTURE_ELAPSED_TIME, this::updateDuration);
    }

    @NotNull
    @Override
    public String getConfigurationText() {
      return ProfilingTechnology.fromConfig(myStage.getProfilerConfigModel().getProfilingConfiguration()).getName();
    }

    @NotNull
    @Override
    public Range getRange() {
      return myRange;
    }

    @Override
    public void abort() {
      myStage.toggleCapturing();
    }

    protected void updateDuration() {
      myRange.setMax(TimeUnit.MICROSECONDS.toNanos(myStage.getCaptureElapsedTimeUs()));
    }
  }
}
