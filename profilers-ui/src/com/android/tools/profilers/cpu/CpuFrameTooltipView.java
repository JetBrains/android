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
import com.intellij.util.ui.JBUI;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class CpuFrameTooltipView extends ProfilerTooltipView {
  @NotNull private final CpuFrameTooltip myTooltip;
  @NotNull private final JPanel myContent;
  @NotNull private final JLabel myCpuText;
  @NotNull private final JLabel myTotalTimeText;

  protected CpuFrameTooltipView(@NotNull CpuProfilerStageView view, @NotNull CpuFrameTooltip tooltip) {
    super(view.getTimeline());
    myTooltip = tooltip;
    myContent = new JPanel(new TabularLayout("*").setVGap(JBUI.scale(8)));
    myCpuText = createTooltipLabel();
    myContent.add(myCpuText, new TabularLayout.Constraint(0, 0));
    myTotalTimeText = createTooltipLabel();
    myContent.add(myTotalTimeText, new TabularLayout.Constraint(2, 0));
    tooltip.addDependency(this).onChange(CpuFrameTooltip.Aspect.FRAME_CHANGED, this::timeChanged);
  }

  protected void timeChanged() {
    AtraceFrame frame = myTooltip.getFrame();
    if (frame == null || frame == AtraceFrame.EMPTY) {
      myContent.setVisible(false);
      return;
    }
    myContent.setVisible(true);
    myCpuText.setText(String.format("CPU Time: %s", TimeFormatter
      .getSingleUnitDurationString((long)(TimeUnit.SECONDS.toMicros(1) * frame.getCpuTimeSeconds()))));
    myTotalTimeText.setText(String.format("Total Time: %s", TimeFormatter.getSingleUnitDurationString(frame.getDurationUs())));
  }

  @NotNull
  @Override
  protected JComponent createTooltip() {
    return myContent;
  }
}
