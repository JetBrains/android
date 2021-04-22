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
import com.android.tools.profilers.cpu.VisualNodeCaptureNode;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An abstract class that represents a capture details, e.g it can be {@link TopDown}, {@link BottomUp},
 * {@link CallChart} or {@link FlameChart}.
 */
public abstract class CaptureDetails {
  public enum Type {
    TOP_DOWN(TopDown::new),
    BOTTOM_UP(BottomUp::new),
    CALL_CHART(CallChart::new),
    FLAME_CHART(FlameChart::new);

    @NotNull
    private final CaptureDetailsBuilder myBuilder;

    Type(@NotNull CaptureDetailsBuilder builder) {
      myBuilder = builder;
    }

    public CaptureDetails build(@NotNull Range range, @NotNull List<CaptureNode> node, @NotNull CpuCapture cpuCapture) {
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
    /**
     * @param range The time range to filter the incoming list of {@link CaptureNode}s to.
     * @param captureNodes The list of nodes we want to visualize. For groups of nodes a new parent node will be created and all capture
     *                     nodes will be reparented to this new node.
     *                     Note: This reparenting is done via the {@link VisualNodeCaptureNode} so no CaptureNode data is mutated.
     * @param cpuCapture The capture which the captureNodes were referenced from.
     */
    CaptureDetails build(Range range, List<CaptureNode> captureNodes, CpuCapture cpuCapture);
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

    TopDown(@NotNull Range range, @NotNull List<CaptureNode> nodes, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      if (nodes.isEmpty()) {
        myModel = null;
        return;
      }
      Range captureRange = cpuCapture.getRange();
      VisualNodeCaptureNode visual = new VisualNodeCaptureNode(new SingleNameModel(""));
      nodes.forEach(visual::addChild);
      visual.setStartGlobal((long)captureRange.getMin());
      visual.setEndGlobal((long)captureRange.getMax());
      myModel = new TopDownTreeModel(range, new TopDownNode(visual));
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

    BottomUp(@NotNull Range range, @NotNull List<CaptureNode> nodes, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      if (nodes.isEmpty()) {
        myModel = null;
        return;
      }
      Range captureRange = cpuCapture.getRange();
      VisualNodeCaptureNode visual = new VisualNodeCaptureNode(new SingleNameModel(""));
      nodes.forEach(visual::addChild);
      visual.setStartGlobal((long)captureRange.getMin());
      visual.setEndGlobal((long)captureRange.getMax());
      BottomUpNode buNode = new BottomUpNode(visual);
      buNode.update(range);
      myModel = new BottomUpTreeModel(range, buNode);
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

    public CallChart(@NotNull Range range, @NotNull List<CaptureNode> nodes, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      myRange = range;
      if (nodes.isEmpty()) {
        myNode = null;
        return;
      }
      // TODO: Add support for multi-select CallChart nodes if we change the UI.
      myNode = nodes.get(0);

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

  public static class FlameChart extends ChartDetails {
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

    FlameChart(@NotNull Range selectionRange, @NotNull List<CaptureNode> captureNodes, @NotNull CpuCapture cpuCapture) {
      super(cpuCapture);
      mySelectionRange = selectionRange;
      myFlameRange = new Range();
      myAspectModel = new AspectModel<>();

      if (captureNodes.isEmpty()) {
        myFlameNode = null;
        myTopDownNode = null;
        return;
      }
      VisualNodeCaptureNode visual = new VisualNodeCaptureNode(new SingleNameModel(""));
      captureNodes.sort(Comparator.comparingLong(CaptureNode::getStartGlobal));
      captureNodes.forEach(visual::addChild);
      // This needs to be the start of the earliest node to have an accurate range for multi-selected items.
      visual.setStartGlobal(captureNodes.get(0).getStartGlobal());

      // Update the node to compute the total children time.
      myTopDownNode = new TopDownNode(visual);
      myTopDownNode.update(new Range(0, Double.MAX_VALUE));

      // This gets mapped to the sum of all children. this makes an assumption that this node has 0 self time.
      // Because we are creating this node that is a valid assumption.
      // We map to the sum of all children because when multiple nodes are selected nodes with the same Id are merged.
      // When they are merged the sum of time is less than or equal to the total time of each node. We need the time to
      // be accurate as when we compute the capture space to screen space calculations for the graph we need to know what
      // 100% is.
      visual.setEndGlobal(visual.getStartGlobal() + (long)myTopDownNode.getGlobalChildrenTotal());
      selectionRange.addDependency(myAspectModel).onChange(Range.Aspect.RANGE, this::selectionRangeChanged);
      selectionRangeChanged();
    }

    private void selectionRangeChanged() {
      assert myTopDownNode != null;
      // This range needs to account for the multiple children,
      // does it need to account for the merged children?
      myTopDownNode.update(mySelectionRange);
      // If the new selection range intersects the root node, we should reconstruct the flame chart node.
      // Otherwise, clear the flame chart node and show an empty chart.
      if (myTopDownNode.getGlobalTotal() > 0) {
        double start = Math.max(myTopDownNode.getNodes().get(0).getStart(), mySelectionRange.getMin());
        myFlameNode = convertToFlameChart(myTopDownNode, start, 0);
        // The intersection check (root.getGlobalTotal() > 0) may be a false positive because the root's global total is the
        // sum of all its children for the purpose of mapping a multi-node tree to flame chart space. Thus we need to look at
        // its children to find out the actual intersection.
        if (myFlameNode.getLastChild() != null) {
          // At least one child intersects the selection rage, use the last child as the real intersection end.
          myFlameNode.setEndGlobal(myFlameNode.getLastChild().getEndGlobal());
          // This is the range used by the HTreeChart to determine if a node is in the range or out of the range.
          // Because the node is already filtered to the selection we can use the length of the node.
          myFlameRange.set(myFlameNode.getStart(), myFlameNode.getEnd());
        }
        else {
          // No child intersects the selection range, so effectively there's no intersection at all.
          myFlameNode = null;
        }
      }
      else {
        myFlameNode = null;
      }

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
}
