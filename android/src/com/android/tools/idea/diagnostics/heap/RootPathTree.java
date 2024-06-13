/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.ExtendedReportStatistics.NOMINATED_CLASSES_NUMBER_IN_SECTION;
import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RootPathTree {
  // Nominated node types are:
  // 1) all the nominated classes are separate types (3 sections, NOMINATED_CLASSES_NUMBER_IN_SECTION nominated classes in each at max)
  // 2) disposed but referenced objects
  // 3) Objects that were loaded with a regular ClassLoader but refer to objects loaded with `StudioModuleClassLoader`
  // 4) Heap Summary node type that contains total number of object and subtree sizes for objects in paths of the all above types
  static final int MAX_NUMBER_OF_NOMINATED_NODE_TYPES = NOMINATED_CLASSES_NUMBER_IN_SECTION * 3 + 3;
  static final int DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE = NOMINATED_CLASSES_NUMBER_IN_SECTION * 3;
  static final int OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE = NOMINATED_CLASSES_NUMBER_IN_SECTION * 3 + 1;
  static final int HEAP_SUMMARY_NODE_TYPE = NOMINATED_CLASSES_NUMBER_IN_SECTION * 3 + 2;

  // Nodes of essential nominated types will be included in the memory reports with no reduction. Also the top-most nodes of the essential
  // nominated types will be included in the heapSummary trees.
  static final IntSet ESSENTIAL_NOMINATED_NODE_TYPES =
    IntSet.of(DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE, OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE);
  @NotNull final Map<ExtendedStackNode, RootPathTreeNode> roots = Maps.newHashMap();
  @NotNull final ObjectsStatistics totalNominatedLoadersReferringObjectsStatistics = new ObjectsStatistics();
  @NotNull final ObjectsStatistics totalRetainedDisposedObjectsStatistics = new ObjectsStatistics();

  private int numberOfRootPathTreeNodes = 0;

  private final ExtendedReportStatistics extendedReportStatistics;
  private static final int ROOT_PATH_TREE_MAX_OBJECT_DEPTH = 200;

  public RootPathTree(@NotNull final ExtendedReportStatistics extendedReportStatistics) {
    this.extendedReportStatistics = extendedReportStatistics;
  }

  public void addDisposedReferencedObjectWithPathToRoot(@NotNull final Stack<RootPathElement> rootPath,
                                                        @NotNull final ExceededClusterStatistics exceededClusterStatistics) {
    addObjectWithPathToRoot(rootPath, exceededClusterStatistics, DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE);
    totalRetainedDisposedObjectsStatistics.addObject(rootPath.lastElement().size);
  }

  public void addClassLoaderPath(@NotNull final Stack<RootPathElement> rootPath,
                                 @NotNull final ExceededClusterStatistics exceededClusterStatistics) {
    addObjectWithPathToRoot(rootPath, exceededClusterStatistics, OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE);
    totalNominatedLoadersReferringObjectsStatistics.addObject(rootPath.lastElement().size);
  }

  public void addObjectWithPathToRoot(@NotNull final Stack<RootPathElement> rootPath,
                                      @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                      int nominatedNodeTypeId) {
    if (rootPath.size() > ROOT_PATH_TREE_MAX_OBJECT_DEPTH && !ESSENTIAL_NOMINATED_NODE_TYPES.contains(nominatedNodeTypeId)) {
      return;
    }

    ListIterator<RootPathElement> rootPathIterator = rootPath.listIterator(rootPath.size());
    int exceededClusterId = exceededClusterStatistics.exceededClusterIndex;

    RootPathTreeNode rootPathTreeNode = null;
    RootPathElement pathElement = null;

    // Iterate in reverse.
    while (rootPathIterator.hasPrevious()) {
      pathElement = rootPathIterator.previous();
      if (pathElement.rootPathTreeNode != null && pathElement.isRootPathTreeNodeWasPropagated(exceededClusterId, nominatedNodeTypeId)) {
        rootPathIterator.next();
        rootPathTreeNode = pathElement.rootPathTreeNode;
        break;
      }
    }

    if (rootPathTreeNode == null) {
      pathElement = rootPathIterator.next();
      if (pathElement.rootPathTreeNode != null) {
        rootPathTreeNode = pathElement.rootPathTreeNode;
      } else {
        rootPathTreeNode = roots.getOrDefault(pathElement.extendedStackNode, createRootPathTreeNode(pathElement, extendedReportStatistics));
        roots.put(pathElement.extendedStackNode, rootPathTreeNode);
      }
      rootPathTreeNode.incrementNumberOfInstances(exceededClusterId, nominatedNodeTypeId, pathElement.subtreeSize);
      pathElement.setRootPathTreeNode(rootPathTreeNode, exceededClusterId, nominatedNodeTypeId);
    }

    addPathElementIteration(rootPathTreeNode, pathElement, rootPathIterator, exceededClusterId, nominatedNodeTypeId);
  }

  private RootPathTreeNode createRootPathTreeNode(@NotNull final RootPathElement rootPathElement,
                                                  @NotNull final ExtendedReportStatistics extendedReportStatistics) {
    numberOfRootPathTreeNodes++;
    if (rootPathElement.isArray) {
      return new RootPathTreeNode.RootPathArrayTreeNode(rootPathElement.getLabel(), rootPathElement.getClassName(),
                                                        rootPathElement.isDisposedButReferenced(),
                                                        rootPathElement.isLoadedWithNominatedLoader(),
                                                        extendedReportStatistics);
    }
    return new RootPathTreeNode(rootPathElement.getLabel(), rootPathElement.getClassName(), rootPathElement.isDisposedButReferenced(),
                                rootPathElement.isLoadedWithNominatedLoader(),
                                extendedReportStatistics);
  }

  private void addPathElementIteration(@NotNull final RootPathTreeNode node,
                                       @NotNull final RootPathElement currentRootPathElement,
                                       @NotNull final ListIterator<RootPathElement> iterator,
                                       int exceededClusterId,
                                       int nominatedNodeTypeId) {
    if (!iterator.hasNext()) {
      node.markNodeAsNominated(exceededClusterId, nominatedNodeTypeId, currentRootPathElement.size);
      return;
    }
    RootPathElement rootPathElement = iterator.next();

    if (node.label.equals(rootPathElement.getLabel()) && node.className.equals(rootPathElement.getClassName())) {
      node.isRepeated = true;
      assert rootPathElement.rootPathTreeNode == null || rootPathElement.rootPathTreeNode == node;
      rootPathElement.setRootPathTreeNode(node, exceededClusterId, nominatedNodeTypeId);
      addPathElementIteration(node, rootPathElement, iterator, exceededClusterId, nominatedNodeTypeId);
      return;
    }

    RootPathTreeNode childNode = node.children.get(rootPathElement.extendedStackNode);
    if (childNode == null) {
      childNode = Objects.requireNonNullElseGet(rootPathElement.rootPathTreeNode,
                                                () -> createRootPathTreeNode(rootPathElement, extendedReportStatistics));
      node.children.put(rootPathElement.extendedStackNode, childNode);
    }

    childNode.incrementNumberOfInstances(exceededClusterId, nominatedNodeTypeId, rootPathElement.subtreeSize);

    assert rootPathElement.rootPathTreeNode == null || rootPathElement.rootPathTreeNode == childNode;
    rootPathElement.setRootPathTreeNode(childNode, exceededClusterId, nominatedNodeTypeId);
    addPathElementIteration(childNode, rootPathElement, iterator, exceededClusterId, nominatedNodeTypeId);
  }

  public int getNumberOfRootPathTreeNodes() {
    return numberOfRootPathTreeNodes;
  }

  public static class RootPathElement {
    @NotNull
    final ExtendedStackNode extendedStackNode;
    private final long size;
    private long subtreeSize;
    private final boolean isArray;

    @Nullable
    private RootPathTreeNode rootPathTreeNode;
    private final short[] rootPathTreeNodeWasPropagated;


    public RootPathElement(@NotNull ExtendedStackNode node,
                           long size,
                           @NotNull final ExtendedReportStatistics extendedReportStatistics,
                           boolean isArray) {
      extendedStackNode = node;
      rootPathTreeNodeWasPropagated = new short[extendedReportStatistics.componentToExceededClustersStatistics.size()];
      this.size = size;
      this.subtreeSize = size;
      this.isArray = isArray;
    }

    boolean isRootPathTreeNodeWasPropagated(int exceededClusterId,
                                            int nominatedNodeTypeId) {
      return (rootPathTreeNodeWasPropagated[exceededClusterId] & (1 << nominatedNodeTypeId)) != 0;
    }

    public String getClassName() {
      return extendedStackNode.getClassName();
    }

    @NotNull
    public String getLabel() {
      return extendedStackNode.getLabel();
    }

    public boolean isDisposedButReferenced() {
      return extendedStackNode.isDisposedButReferenced();
    }

    public boolean isLoadedWithNominatedLoader() {
      return extendedStackNode.isLoadedWithNominatedLoader();
    }

    public void setRootPathTreeNode(@Nullable RootPathTreeNode rootPathTreeNode,
                                    int exceededClusterId,
                                    int nominatedNodeTypeId) {
      this.rootPathTreeNode = rootPathTreeNode;
      rootPathTreeNodeWasPropagated[exceededClusterId] |= 1 << nominatedNodeTypeId;
    }

    public long getSize() {
      return size;
    }

    public long getSubtreeSize() {
      return subtreeSize;
    }

    public void addSubtreeSize(long size) {
      subtreeSize += size;

      if (rootPathTreeNode == null) {
        return;
      }
      // When we add subtree size to the node it may already participate in some object paths - we need to reflect the subtree size
      // change there as well to avoid out of sync.
      for (int i = 0; i < rootPathTreeNodeWasPropagated.length; i++) {
        int finalI = i;
        processMask(rootPathTreeNodeWasPropagated[i], j -> rootPathTreeNode.instancesStatistics[finalI][j].addStats(0, size));
      }
    }

    public void update() {
      if (rootPathTreeNode == null) {
        return;
      }
      for (int i = 0; i < rootPathTreeNodeWasPropagated.length; i++) {
        if (rootPathTreeNodeWasPropagated[i] == 0) continue;

        if (rootPathTreeNode.instancesStatistics[i][HEAP_SUMMARY_NODE_TYPE] == null) {
          rootPathTreeNode.instancesStatistics[i][HEAP_SUMMARY_NODE_TYPE] = new ObjectsStatistics();
        }
        rootPathTreeNode.instancesStatistics[i][HEAP_SUMMARY_NODE_TYPE].addObject(subtreeSize);
      }
    }
  }
}
