/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadTrackModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Analysis tab model for CPU threads.
 */
public class CpuThreadAnalysisSummaryTabModel extends CpuAnalysisSummaryTabModel<CpuThreadTrackModel> {
  @NotNull private final Range mySelectionRange;

  public CpuThreadAnalysisSummaryTabModel(@NotNull Range captureRange, @NotNull Range selectionRange) {
    super(captureRange);
    mySelectionRange = selectionRange;
  }

  /**
   * @return up to k capture nodes in this thread that intersects with the selection range, sorted by duration. Returns an empty list if the
   * model contains more than 1 threads.
   */
  @NotNull
  public List<CaptureNode> getTopNodesInSelectionRange(int k) {
    // Only show selected nodes when 1 thread is selected.
    if (getDataSeries().size() != 1) {
      return new ArrayList<>();
    }
    CaptureNode rootNode = getDataSeries().get(0).getCallChartModel().getNode();
    if (rootNode != null) {
      return rootNode.getDescendantsStream()
        .filter(node -> node.getDepth() > 0 && mySelectionRange.intersectsWith(node.getStartGlobal(), node.getEndGlobal()))
        .sorted(Comparator.comparing(CaptureNode::getDuration).reversed())
        .limit(k)
        .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  @NotNull
  @Override
  public String getLabel() {
    return "Thread";
  }

  @NotNull
  @Override
  public Range getSelectionRange() {
    return mySelectionRange;
  }
}
