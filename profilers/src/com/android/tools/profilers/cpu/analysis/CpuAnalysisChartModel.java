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
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.PercentAxisFormatter;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.VisualNodeCaptureNode;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * This class is the base model for any {@link CpuAnalysisTabModel}'s that are backed by a {@link CaptureDetails}.
 * The title is used as both the tab title, and the tab tool tip.
 */
public class CpuAnalysisChartModel<T> extends CpuAnalysisTabModel<T> {
  private static final Map<Type, CaptureDetails.Type> TAB_TYPE_TO_DETAIL_TYPE = ImmutableMap.of(
    Type.FLAME_CHART, CaptureDetails.Type.FLAME_CHART,
    Type.TOP_DOWN, CaptureDetails.Type.TOP_DOWN,
    Type.BOTTOM_UP, CaptureDetails.Type.BOTTOM_UP
  );

  private final CpuCapture myCapture;
  private final Range mySelectionRange;
  private final CaptureDetails.Type myDetailsType;
  private final AxisComponentModel myAxisComponentModel;
  private final Function<T, Collection<CaptureNode>> myCaptureNodesExtractor;

  /**
   * @param captureNodesExtractor a function that extracts capture nodes from the analysis object.
   */
  public CpuAnalysisChartModel(@NotNull Type tabType,
                               @NotNull Range selectionRange,
                               @NotNull CpuCapture capture,
                               @NotNull Function<T, Collection<CaptureNode>> captureNodesExtractor) {
    super(tabType);
    assert TAB_TYPE_TO_DETAIL_TYPE.containsKey(tabType);
    myCapture = capture;
    mySelectionRange = selectionRange;
    myDetailsType = TAB_TYPE_TO_DETAIL_TYPE.get(tabType);
    myCaptureNodesExtractor = captureNodesExtractor;
    myAxisComponentModel = new ClampedAxisComponentModel.Builder(selectionRange, new PercentAxisFormatter(5, 10)).build();
  }

  @NotNull
  public CaptureDetails createDetails() {
    return myDetailsType.build(mySelectionRange, createRootNode(), myCapture);
  }

  @NotNull
  public CaptureDetails.Type getDetailsType() {
    return myDetailsType;
  }

  @NotNull
  public AxisComponentModel getAxisComponentModel() {
    return myAxisComponentModel;
  }

  /**
   * Returns a {@link VisualNodeCaptureNode} as the root node. This is done as element returned by the {@link #getDataSeries()}} is expected
   * to contain the thread root {@link CaptureNode}. When selecting multiple elements we do not show the thread node. This means we loop
   * each thread root and add all children to our new {@link VisualNodeCaptureNode} root.
   * Note: As we modify the selection model we may want to revisit this behavior for individually selected threads.
   */
  @NotNull
  private CaptureNode createRootNode() {
    VisualNodeCaptureNode rootNode = new VisualNodeCaptureNode(new SingleNameModel("Root"));
    rootNode.setStartGlobal((long)myCapture.getRange().getMin());
    rootNode.setEndGlobal((long)myCapture.getRange().getMax());
    // Data series contains each thread selected. For each thread we grab the children and add them to our root.
    getDataSeries().forEach(child -> myCaptureNodesExtractor.apply(child).forEach(rootNode::addChild));
    return rootNode;
  }
}
