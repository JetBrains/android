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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.trackgroup.SelectableTrackModel;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

public class CaptureNodeAnalysisModel implements CpuAnalyzable<CaptureNodeAnalysisModel> {
  @NotNull private final CaptureNode myNode;
  @NotNull private final CpuCapture myCapture;

  public CaptureNodeAnalysisModel(@NotNull CaptureNode node, @NotNull CpuCapture capture) {
    myNode = node;
    myCapture = capture;
  }

  @NotNull
  public CaptureNode getNode() {
    return myNode;
  }

  @NotNull
  public Range getNodeRange() {
    return new Range(myNode.getStart(), myNode.getEnd());
  }

  @NotNull
  @Override
  public CpuAnalysisModel<CaptureNodeAnalysisModel> getAnalysisModel() {
    Range nodeRange = getNodeRange();
    Collection<CaptureNode> nodes = Collections.singleton(myNode);
    CpuAnalysisChartModel<CaptureNodeAnalysisModel> flameChart =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.FLAME_CHART, nodeRange, myCapture, unused -> nodes);
    CpuAnalysisChartModel<CaptureNodeAnalysisModel> topDown =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.TOP_DOWN, nodeRange, myCapture, unused -> nodes);
    CpuAnalysisChartModel<CaptureNodeAnalysisModel> bottomUp =
      new CpuAnalysisChartModel<>(CpuAnalysisTabModel.Type.BOTTOM_UP, nodeRange, myCapture, unused -> nodes);
    flameChart.getDataSeries().add(this);
    topDown.getDataSeries().add(this);
    bottomUp.getDataSeries().add(this);

    CpuAnalysisModel<CaptureNodeAnalysisModel> model = new CpuAnalysisModel<>(myNode.getData().getName(), "%d events");
    model.addTabModel(flameChart);
    model.addTabModel(topDown);
    model.addTabModel(bottomUp);
    return model;
  }

  @Override
  public boolean isCompatibleWith(@NotNull SelectableTrackModel otherObj) {
    return otherObj instanceof CaptureNodeAnalysisModel;
  }
}
