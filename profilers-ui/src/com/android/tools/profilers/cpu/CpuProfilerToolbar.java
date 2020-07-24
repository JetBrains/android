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
package com.android.tools.profilers.cpu;

import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Cpu.CpuTraceType;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.cpu.config.CpuProfilingConfigurationView;
import com.intellij.util.ui.JBDimension;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * The toolbar component of {@link CpuProfilerStage}.
 */
public abstract class CpuProfilerToolbar {
  public static final String RECORD_TEXT = "Record";
  public static final String STOP_TEXT = "Stop";

  @NotNull protected final CpuProfilerStage myStage;
  @NotNull protected final JPanel myPanel;

  protected CpuProfilerToolbar(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myPanel = new JPanel();
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  /**
   * Updates this toolbar so that its components are UI reflections of states of {@link CpuProfilerStage}.
   */
  abstract void update();

  /**
   * The toolbar component when {@link CpuProfilerStage} is in normal mode,
   * i.e {@link CpuProfilerStage#isImportTraceMode()} is false.
   *
   * @see CpuProfilerStage#myIsImportTraceMode
   */
  static final class NormalMode extends CpuProfilerToolbar {
    @NotNull private final CpuProfilingConfigurationView myProfilingConfigurationView;
    @NotNull private final JButton myCaptureButton;

    public NormalMode(@NotNull CpuProfilerStage stage, @NotNull IdeProfilerComponents ideComponents) {
      super(stage);
      myProfilingConfigurationView = new CpuProfilingConfigurationView(myStage, ideComponents);

      // Set to the longest text this button will show as to initialize the persistent size properly.
      // Call setPreferredSize to avoid the initialized size being overwritten.
      // TODO: b/80546414 Use common button instead.
      myCaptureButton = new JButton(RECORD_TEXT);
      // Make the record button's height same with myProfilingConfigurationView.
      myCaptureButton.setPreferredSize(JBDimension.create(myCaptureButton.getPreferredSize()).withHeight(
        (int)myProfilingConfigurationView.getComponent().getPreferredSize().getHeight()));
      myCaptureButton.addActionListener(event -> myStage.toggleCapturing());

      JPanel toolbar = new JPanel(createToolbarLayout());
      toolbar.add(myProfilingConfigurationView.getComponent());
      toolbar.add(myCaptureButton);
      myPanel.setLayout(new BorderLayout());
      myPanel.add(toolbar, BorderLayout.WEST);

      update();
    }

    @Override
    void update() {
      switch (myStage.getCaptureState()) {
        case IDLE:
          myCaptureButton.setEnabled(shouldEnableCaptureButton());
          myCaptureButton.setText(RECORD_TEXT);
          myProfilingConfigurationView.getComponent().setEnabled(true);
          break;
        case CAPTURING:
          if (myStage.getCaptureInitiationType().equals(Cpu.TraceInitiationType.INITIATED_BY_API)) {
            myCaptureButton.setEnabled(false);
          }
          else {
            myCaptureButton.setEnabled(shouldEnableCaptureButton());
          }
          myCaptureButton.setText(STOP_TEXT);
          myProfilingConfigurationView.getComponent().setEnabled(false);
          break;
        case STARTING:
          myCaptureButton.setEnabled(false);
          myCaptureButton.setToolTipText("");
          myProfilingConfigurationView.getComponent().setEnabled(false);
          break;
        case STOPPING:
          myCaptureButton.setEnabled(false);
          myCaptureButton.setToolTipText("");
          myProfilingConfigurationView.getComponent().setEnabled(false);
          break;
      }
    }

    /**
     * Should enable the capture button for recording and stopping only when session is alive and no API-initiated tracing is
     * in progress.
     */
    private boolean shouldEnableCaptureButton() {
      return myStage.getStudioProfilers().getSessionsManager().isSessionAlive() && !myStage.isApiInitiatedTracingInProgress();
    }
  }

  /**
   * The toolbar component when {@link CpuProfilerStage} is in import trace mode,
   * i.e {@link CpuProfilerStage#isImportTraceMode()} is true.
   *
   * @see CpuProfilerStage#myIsImportTraceMode
   */
  static final class ImportMode extends CpuProfilerToolbar {
    @NotNull private final JLabel mySelectedProcessLabel;

    ImportMode(@NotNull CpuProfilerStage stage) {
      super(stage);

      mySelectedProcessLabel = new JLabel();
      myPanel.setLayout(new TabularLayout("8px,*", "*"));
      myPanel.add(mySelectedProcessLabel, new TabularLayout.Constraint(0, 1));

      update();
    }

    /**
     * Sets the main process name to {@link #mySelectedProcessLabel} when the current capture is imported and ATrace.
     * Otherwise, sets an empty text.
     */
    @Override
    void update() {
      assert myStage.isImportTraceMode();
      mySelectedProcessLabel.setText("");
      CpuCapture capture = myStage.getCapture();
      if (capture == null) {
        return;
      }

      if (capture.getType() == CpuTraceType.ATRACE) {
        CaptureNode node = capture.getCaptureNode(capture.getMainThreadId());
        assert node != null;
        mySelectedProcessLabel.setText("Process: " + node.getData().getName());
      }
    }
  }
}
