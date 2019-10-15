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

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.android.tools.profilers.cpu.audits.RenderAuditModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract class that represents a capture details, e.g it can be {@link TopDown}, {@link BottomUp},
 * {@link CallChart} or {@link FlameChart}.
 */
public abstract class CaptureDetails {
  public enum Type {
    TOP_DOWN(TopDown::new),
    BOTTOM_UP(BottomUp::new),
    CALL_CHART(CallChart::new),
    FLAME_CHART(FlameChart::new),
    RENDER_AUDIT(RenderAuditCaptureDetails::new);

    @NotNull
    private final CaptureDetailsBuilder myBuilder;

    Type(@NotNull CaptureDetailsBuilder builder) {
      myBuilder = builder;
    }

    public CaptureDetails build(@NotNull Range range, @Nullable CaptureNode node, @NotNull CpuCapture cpuCapture) {
      return myBuilder.build(range, node, cpuCapture);
    }
  }

  @NotNull
  private final CpuCapture myCapture;

  protected CaptureDetails(@NotNull CpuCapture cpuCapture) {
    myCapture = cpuCapture;
  }

  @NotNull
  public CpuCapture getCapture() {
    return myCapture;
  }

  @NotNull
  public abstract Type getType();

  interface CaptureDetailsBuilder {
    CaptureDetails build(Range range, CaptureNode captureNode, CpuCapture cpuCapture);
  }

  static abstract class ChartDetails extends CaptureDetails {
    protected ChartDetails(@NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
    }
    @Nullable
    abstract CaptureNode getNode();
  }

  public static class TopDown extends CaptureDetails {
    @Nullable private final TopDownTreeModel myModel;

    TopDown(@NotNull Range range, @Nullable CaptureNode node, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      myModel = node == null ? null : new TopDownTreeModel(range, new TopDownNode(node));
    }

    @Nullable
    public TopDownTreeModel getModel() {
      return myModel;
    }

    @Override
    @NotNull
    public Type getType() {
      return Type.TOP_DOWN;
    }
  }

  public static class BottomUp extends CaptureDetails {
    @Nullable private BottomUpTreeModel myModel;

    BottomUp(@NotNull Range range, @Nullable CaptureNode node, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      myModel = node == null ? null : new BottomUpTreeModel(range, new BottomUpNode(node));
    }

    @Nullable
    public BottomUpTreeModel getModel() {
      return myModel;
    }

    @Override
    @NotNull
    public Type getType() {
      return Type.BOTTOM_UP;
    }
  }

  public static class CallChart extends ChartDetails {
    @NotNull private final Range myRange;
    @Nullable private CaptureNode myNode;

    public CallChart(@NotNull Range range, @Nullable CaptureNode node, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      myRange = range;
      myNode = node;
    }

    @NotNull
    public Range getRange() {
      return myRange;
    }

    @Override
    @Nullable
    public CaptureNode getNode() {
      return myNode;
    }

    @Override
    @NotNull
    public Type getType() {
      return Type.CALL_CHART;
    }
  }

  static class FlameChart extends ChartDetails {
    public enum Aspect {
      /**
       * When the root changes.
       */
      NODE
    }

    @NotNull private final Range myFlameRange;
    @Nullable private CaptureNode myFlameNode;
    @Nullable private final TopDownNode myTopDownNode;

    @NotNull private final Range mySelectionRange;
    @NotNull private final AspectModel<Aspect> myAspectModel;

    FlameChart(@NotNull Range selectionRange, @Nullable CaptureNode captureNode, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      mySelectionRange = selectionRange;
      myFlameRange = new Range();
      myAspectModel = new AspectModel<>();

      if (captureNode == null) {
        myFlameNode = null;
        myTopDownNode = null;
        return;
      }
      myTopDownNode = new TopDownNode(captureNode);

      selectionRange.addDependency(myAspectModel).onChange(Range.Aspect.RANGE, this::selectionRangeChanged);
      selectionRangeChanged();
    }

    private void selectionRangeChanged() {
      assert myTopDownNode != null;
      myTopDownNode.update(mySelectionRange);
      if (myTopDownNode.getGlobalTotal() > 0) {
        double start = Math.max(myTopDownNode.getNodes().get(0).getStart(), mySelectionRange.getMin());
        myFlameNode = convertToFlameChart(myTopDownNode, start, 0);
      }
      else {
        myFlameNode = null;
      }

      myFlameRange.set(mySelectionRange);
      myAspectModel.changed(Aspect.NODE);
    }

    @NotNull
    public Range getRange() {
      return myFlameRange;
    }

    @Override
    @Nullable
    public CaptureNode getNode() {
      return myFlameNode;
    }

    @NotNull
    public AspectModel<Aspect> getAspect() {
      return myAspectModel;
    }

    @Override
    @NotNull
    public Type getType() {
      return Type.FLAME_CHART;
    }

    /**
     * Produces a flame chart that is similar to {@link CallChart}, but the identical methods with the same sequence of callers
     * are combined into one wider bar. It converts it from {@link TopDownNode} as it's similar to FlameChart and
     * building a {@link TopDownNode} instance only on creation gives a performance improvement in every update.
     */
    private CaptureNode convertToFlameChart(@NotNull TopDownNode topDown, double start, int depth) {
      assert topDown.getGlobalTotal() > 0;

      CaptureNode node = new CaptureNode(topDown.getNodes().get(0).getData());
      node.setFilterType(topDown.getNodes().get(0).getFilterType());
      node.setStartGlobal((long)start);
      node.setStartThread((long)start);
      node.setEndGlobal((long)(start + topDown.getGlobalTotal()));
      node.setEndThread((long)(start + topDown.getThreadTotal()));

      node.setDepth(depth);

      for (TopDownNode child : topDown.getChildren()) {
        child.update(mySelectionRange);
      }

      List<TopDownNode> sortedChildren = new ArrayList<>(topDown.getChildren());
      // When we display a topdown node in the ui, its sorting handled by the table's sorting mechanism.
      // Conversely, in the flame chart we take care of sorting.
      // List#sort api is stable, i.e it keeps order of the appearance if sorting arguments are equal.
      sortedChildren.sort((o1, o2) -> {
        int cmp = Boolean.compare(o1.isUnmatched(), o2.isUnmatched());
        return cmp == 0 ? Double.compare(o2.getGlobalTotal(), o1.getGlobalTotal()) : cmp;
      });

      for (TopDownNode child : sortedChildren) {
        if (child.getGlobalTotal() == 0) {
          // Sorted in descending order, so starting from now every child's total is zero.
          continue;
        }
        node.addChild(convertToFlameChart(child, start, depth + 1));
        start += child.getGlobalTotal();
      }

      return node;
    }
  }

  public static class RenderAuditCaptureDetails extends CaptureDetails {

    private final RenderAuditModel myRenderAuditModel;

    RenderAuditCaptureDetails(@NotNull Range range, @Nullable CaptureNode node, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      // The Render Audit tab is only added in the CapturePane is the capture is an AtraceCpuCapture
      assert cpuCapture instanceof AtraceCpuCapture;
      myRenderAuditModel = new RenderAuditModel((AtraceCpuCapture)cpuCapture);
    }

    @Override
    @NotNull
    public Type getType() {
      return Type.RENDER_AUDIT;
    }

    public RenderAuditModel getRenderAuditModel() {
      return myRenderAuditModel;
    }
  }
}
