/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.ui.memory.view;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AllocationsParser;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.memory.model.AllocationTrackingSample;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.idea.monitor.ui.memory.model.MemoryInfoTreeNode;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.List;

/**
 * This represents the histogram view of the memory monitor detail view.
 */
public class ClassHistogramView extends BaseSegment implements Disposable {
  private static final String NAME = "Memory Details";
  private static final Color NEGATIVE_COLOR = new JBColor(new Color(0x33FF0000, true), new Color(0x33FF6464, true));
  private static final Color POSITIVE_COLOR = new JBColor(new Color(0x330000FF, true), new Color(0x33589df6, true));
  @NotNull
  private final JPanel myParent;
  @NotNull
  private MemoryInfoTreeNode myRoot;
  @Nullable
  private HeapDump myMainHeapDump;
  @Nullable
  private HeapDump myDiffHeapDump;

  private JComponent myColumnTree;

  private JTree myTree;

  private DefaultTreeModel myTreeModel;

  ClassHistogramView(@NotNull Disposable parentDisposable,
                     @NotNull JPanel parentPanel,
                     @NotNull Range timeCurrentRangeUs,
                     @NotNull Choreographer choreographer,
                     @NotNull EventDispatcher<ProfilerEventListener> profilerEventDispatcher) {
    super(NAME, timeCurrentRangeUs, profilerEventDispatcher);
    Disposer.register(parentDisposable, this);

    myParent = parentPanel;
    myRoot = new MemoryInfoTreeNode("Root");

    List<Animatable> animatables = new ArrayList<>();
    createComponentsList(animatables);
    choreographer.register(animatables);
    initializeComponents();

    myParent.add(this, BorderLayout.CENTER);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ClassHistogramView.class);
  }

  void generateClassHistogramFromHeapDumpInfos(@NotNull MemoryDataCache dataCache,
                                               @Nullable HeapDumpInfo mainHeapDumpInfo,
                                               @Nullable HeapDumpInfo diffHeapDumpInfo) {
    // TODO make this method asynchronous
    if (myMainHeapDump == null || myMainHeapDump.getInfo() != mainHeapDumpInfo) {
      if (myMainHeapDump != null) {
        myMainHeapDump.dispose();
        myMainHeapDump = null;
      }

      if (mainHeapDumpInfo != null) {
        try {
          myMainHeapDump = new HeapDump(dataCache, mainHeapDumpInfo);
        }
        catch (IOException exception) {
          getLog().info("Error generating Snapshot from heap dump file.", exception);
          return;
        }
      }
    }

    if (myDiffHeapDump == null || myDiffHeapDump.getInfo() != diffHeapDumpInfo) {
      if (myDiffHeapDump != null) {
        myDiffHeapDump.dispose();
        myDiffHeapDump = null;
      }

      if (diffHeapDumpInfo != null) {
        try {
          myDiffHeapDump = new HeapDump(dataCache, diffHeapDumpInfo);
        }
        catch (IOException exception) {
          getLog().info("Error generating Snapshot from heap dump file.", exception);
          return;
        }
      }
    }

    HeapDump positiveHeapDump = myDiffHeapDump != null ? myDiffHeapDump : myMainHeapDump;
    HeapDump negativeHeapDump = myDiffHeapDump != null ? myMainHeapDump : null;

    Map<String, Integer> instanceMap = new HashMap<>();
    // Compute the positive delta from the next heap dump
    if (positiveHeapDump != null) {
      for (Heap heap : positiveHeapDump.mySnapshot.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = classObj.getInstanceCount() + instanceMap.getOrDefault(className, 0);
          instanceMap.put(className, instanceCount);
        }
      }
    }

    // Subtract the negative delta from the main heap dump
    if (negativeHeapDump != null) {
      for (Heap heap : negativeHeapDump.mySnapshot.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = instanceMap.getOrDefault(className, 0) - classObj.getInstanceCount();
          instanceMap.put(className, instanceCount);
        }
      }
    }

    generateClassHistogram(instanceMap);
  }

  boolean generateClassHistogramFromAllocationTracking(@NotNull AllocationTrackingSample sample) {
    // TODO move/implement detection + fixup of .alloc file into addAllocationTracking, and make this asynchronous

    // Dispose loaded hprof files as we're in allocation tracking mode.
    if (myMainHeapDump != null) {
      myMainHeapDump.dispose();
      myMainHeapDump = null;
    }

    if (myDiffHeapDump != null) {
      myDiffHeapDump.dispose();
      myDiffHeapDump = null;
    }

    ByteBuffer data = ByteBuffer.wrap(sample.getData());
    data.order(ByteOrder.BIG_ENDIAN);
    if (AllocationsParser.hasOverflowedNumEntriesBug(data)) {
      getLog().info("Allocations file has overflow bug.");
      return false;
    }

    AllocationInfo[] allocationInfos = AllocationsParser.parse(data);
    Map<String, Integer> instanceMap = new HashMap<>();
    for (AllocationInfo info : allocationInfos) {
      instanceMap.put(info.getAllocatedClass(), instanceMap.getOrDefault(info.getAllocatedClass(), 0) + 1);
    }

    generateClassHistogram(instanceMap);

    return true;
  }

  /**
   * Updates a {@link MemoryDetailSegment} to show the allocations (and changes)
   */
  private void generateClassHistogram(@NotNull Map<String, Integer> instanceMap) {
    myRoot.setCount(0);
    myRoot.removeAllChildren();

    int maxInstanceCount = Integer.MIN_VALUE;
    for (Map.Entry<String, Integer> entry : instanceMap.entrySet()) {
      int instanceCount = entry.getValue();
      if (instanceCount != 0) {
        MemoryInfoTreeNode child = new MemoryInfoTreeNode(entry.getKey());
        child.setCount(instanceCount);
        insertNode(myRoot, child);
        maxInstanceCount = Math.max(maxInstanceCount, Math.abs(instanceCount));
      }
    }

    myRoot.setCount(maxInstanceCount);
    refreshNode(myRoot);
  }

  @Override
  public void dispose() {
    if (myMainHeapDump != null) {
      myMainHeapDump.dispose();
      myMainHeapDump = null;
    }
    if (myDiffHeapDump != null) {
      myDiffHeapDump.dispose();
      myDiffHeapDump = null;
    }
    myParent.remove(this);
  }

  /**
   * Requests the tree model to perform a reload on the input node.
   */
  @VisibleForTesting
  public void refreshNode(@NotNull MemoryInfoTreeNode node) {
    myTreeModel.reload(node);
  }

  @VisibleForTesting
  public boolean getExpandState(@NotNull MemoryInfoTreeNode node) {
    return myTree.isExpanded(new TreePath(myTreeModel.getPathToRoot(node)));
  }

  @VisibleForTesting
  public void setExpandState(@NotNull MemoryInfoTreeNode node, boolean expand) {
    if (expand) {
      myTree.expandPath(new TreePath(myTreeModel.getPathToRoot(node)));
    }
    else {
      myTree.collapsePath(new TreePath(myTreeModel.getPathToRoot(node)));
    }
  }

  public void insertNode(@NotNull MemoryInfoTreeNode parent, @NotNull MemoryInfoTreeNode child) {
    myTreeModel.insertNodeInto(child, parent, parent.getChildCount());
  }

  @Override
  protected boolean hasLeftContent() {
    return false;
  }

  @Override
  protected boolean hasRightContent() {
    return false;
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    panel.add(myColumnTree, BorderLayout.CENTER);
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Class")
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MemoryInfoColumnRenderer(0, myRoot)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Count")
                   .setHeaderAlignment(SwingConstants.LEFT)
                   .setRenderer(new MemoryInfoColumnRenderer(1, myRoot))
                   .setInitialOrder(SortOrder.DESCENDING)
                   .setComparator((MemoryInfoTreeNode a, MemoryInfoTreeNode b) -> a.getCount() - b.getCount()));

    builder.setTreeSorter((Comparator<MemoryInfoTreeNode> comparator, SortOrder sortOrder) -> {
      myRoot.sort(comparator);
      myTreeModel.nodeStructureChanged(myRoot);
    });

    myColumnTree = builder.build();
  }

  /**
   * A simple cell renderer for columns that renders a bar indicating the deltas along with the node's content.
   */
  private static class MemoryInfoColumnRenderer extends ColoredTreeCellRenderer {
    @NotNull
    private final MemoryInfoHealthBar mHealthBar;

    @NotNull
    private final MemoryInfoTreeNode mRoot;

    private final int mColumnIndex;

    private MemoryInfoColumnRenderer(int index, @NotNull MemoryInfoTreeNode root) {
      mHealthBar = new MemoryInfoHealthBar();
      mColumnIndex = index;
      mRoot = root;

      if (mColumnIndex > 0) {
        setLayout(new BorderLayout());
        add(mHealthBar, BorderLayout.CENTER);
      }
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MemoryInfoTreeNode) {
        MemoryInfoTreeNode node = (MemoryInfoTreeNode)value;
        switch (mColumnIndex) {
          case 0:
            append(node.getName());
            break;
          case 1:
            append(String.valueOf(node.getCount()));
            break;
        }

        mHealthBar.setDelta((float)node.getCount() / mRoot.getCount());
      }
    }
  }

  private static class MemoryInfoHealthBar extends JComponent {
    private float mDelta;

    private void setDelta(float delta) {
      mDelta = delta;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      Dimension dim = getSize();
      if (mDelta > 0) {
        g.setColor(NEGATIVE_COLOR);
      }
      else {
        g.setColor(POSITIVE_COLOR);
      }

      g.fillRect(0, 0, (int)(dim.width * Math.abs(mDelta)), dim.height);
    }
  }

  private static class HeapDump {
    @NotNull private final HeapDumpInfo myInfo;
    @NotNull private final Snapshot mySnapshot;

    public HeapDump(@NotNull MemoryDataCache dataCache, @NotNull HeapDumpInfo info) throws IOException {
      myInfo = info;
      mySnapshot = Snapshot.createSnapshot(new InMemoryBuffer(dataCache.getHeapDumpData(info).asReadOnlyByteBuffer()));
    }

    @NotNull
    public HeapDumpInfo getInfo() {
      return myInfo;
    }

    public void dispose() {
      mySnapshot.dispose();
    }
  }
}
