/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.ProfilerTooltipView;
import com.android.tools.profilers.cpu.atrace.AtraceFrame;
import com.android.tools.profilers.cpu.atrace.CpuFrameTooltip;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.TimeUnit;

public class CpuFrameTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuFrameTooltip myTooltip;
  @NotNull private final JPanel myContent;

  @NotNull private final JPanel myMainFramePanel;
  @NotNull private final JLabel myMainFrameCpuText;
  @NotNull private final JLabel myMainFrameTotalTimeText;

  @NotNull private final JPanel myRenderFramePanel;
  @NotNull private final JLabel myRenderFrameCpuText;
  @NotNull private final JLabel myRenderTotalTimeText;

  @NotNull private final JLabel myTotalTimeText;

  protected CpuFrameTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuFrameTooltip tooltip) {
    super(view.getTimeline());
    myTooltip = tooltip;
    myContent = new JPanel(new TabularLayout("*").setVGap(12));

    myMainFramePanel = new JPanel(new TabularLayout("*").setVGap(JBUIScale.scale(4)));

    JLabel mainThreadLabel = createTooltipLabel();
    mainThreadLabel.setText("Main Thread");
    myMainFrameCpuText = createTooltipLabel();
    myMainFrameTotalTimeText = createTooltipLabel();
    myTotalTimeText = createTooltipLabel();

    myMainFramePanel.add(mainThreadLabel, new TabularLayout.Constraint(0, 0));
    myMainFramePanel.add(myMainFrameCpuText, new TabularLayout.Constraint(2, 0));
    myMainFramePanel.add(myMainFrameTotalTimeText, new TabularLayout.Constraint(4, 0));

    myRenderFramePanel = new JPanel(new TabularLayout("*").setVGap(JBUIScale.scale(4)));

    JLabel renderThreadLabel = createTooltipLabel();
    renderThreadLabel.setText("Render Thread");
    myRenderFrameCpuText = createTooltipLabel();
    myRenderTotalTimeText = createTooltipLabel();

    myRenderFramePanel.add(renderThreadLabel, new TabularLayout.Constraint(0, 0));
    myRenderFramePanel.add(myRenderFrameCpuText, new TabularLayout.Constraint(2, 0));
    myRenderFramePanel.add(myRenderTotalTimeText, new TabularLayout.Constraint(4, 0));

    myContent.add(myTotalTimeText, new TabularLayout.Constraint(0, 0));
    myContent.add(myMainFramePanel, new TabularLayout.Constraint(1, 0));
    myContent.add(myRenderFramePanel, new TabularLayout.Constraint(2, 0));

    tooltip.addDependency(this).onChange(CpuFrameTooltip.Aspect.FRAME_CHANGED, this::timeChanged);
  }

  private static void setLabelText(AtraceFrame frame, JLabel cpuText, JLabel totalTimeText) {
    cpuText.setText(String.format("CPU Time: %s", TimeFormatter
      .getSingleUnitDurationString((long)(TimeUnit.SECONDS.toMicros(1) * frame.getCpuTimeSeconds()))));
    totalTimeText.setText(String.format("Wall Time: %s", TimeFormatter.getSingleUnitDurationString(frame.getDurationUs())));
  }

  protected void timeChanged() {
    // hide everything then show the necessary fields later
    myContent.setVisible(false);
    myMainFramePanel.setVisible(false);
    myRenderFramePanel.setVisible(false);
    myTotalTimeText.setVisible(false);

    AtraceFrame frame = myTooltip.getFrame();
    if (frame == null || frame == AtraceFrame.EMPTY) {
      return;
    }
    myContent.setVisible(true);
    if (frame.getThread() == AtraceFrame.FrameThread.MAIN) {
      myMainFramePanel.setVisible(true);
      setLabelText(frame, myMainFrameCpuText, myMainFrameTotalTimeText);
      if (frame.getAssociatedFrame() != null) {
        myRenderFramePanel.setVisible(true);
        setLabelText(frame.getAssociatedFrame(), myRenderFrameCpuText, myRenderTotalTimeText);
      }
    }
    else if (frame.getThread() == AtraceFrame.FrameThread.RENDER) {
      myRenderFramePanel.setVisible(true);
      setLabelText(frame, myRenderFrameCpuText, myRenderTotalTimeText);
      if (frame.getAssociatedFrame() != null) {
        myMainFramePanel.setVisible(true);
        setLabelText(frame.getAssociatedFrame(), myMainFrameCpuText, myMainFrameTotalTimeText);
      }
    }
    if (frame.getAssociatedFrame() != null) {
      myTotalTimeText.setVisible(true);
      long associatedFrameLength = frame.getAssociatedFrame().getDurationUs();
      myTotalTimeText.setText("Total Time: " + TimeFormatter.getSingleUnitDurationString(frame.getDurationUs() + associatedFrameLength));
    }
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    return myContent;
  }
}
