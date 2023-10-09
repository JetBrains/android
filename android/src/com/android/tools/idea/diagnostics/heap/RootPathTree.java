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
import static com.google.common.base.Strings.padStart;

import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RootPathTree {
  // Nominated node types are:
  // 1) all the nominated classes are separate types (3 sections, NOMINATED_CLASSES_NUMBER_IN_SECTION nominated classes in each at max)
  // 2) disposed but referenced objects
  // 3) Objects that were loaded with a regular ClassLoader but refer to objects loaded with `StudioModuleClassLoader`
  private static final int MAX_NUMBER_OF_NOMINATED_NODE_TYPES = NOMINATED_CLASSES_NUMBER_IN_SECTION * 3 + 2;
  private static final int DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE = 9;
  private static final int OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE = 10;
  private static final IntSet NOMINATED_NODE_TYPES_NO_PRINTING_OPTIMIZATION =
    IntSet.of(DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE, OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE);
  @NotNull
  private final List<RootPathTreeNode> roots = Lists.newArrayList();
  @NotNull
  private final ObjectsStatistics totalNominatedLoadersReferringObjectsStatistics = new ObjectsStatistics();
  private int numberOfRootPathTreeNodes = 0;

  private final ExtendedReportStatistics extendedReportStatistics;
  private static final int ROOT_PATH_TREE_MAX_OBJECT_DEPTH = 200;
  private static final int NODE_SUBTREE_SIZE_PERCENTAGE_REQUIREMENT = 2;
  private static final int NODE_SUBTREE_OBJECTS_SIZE_REQUIREMENT_BYTES = 750_000; //750kb

  public RootPathTree(@NotNull final ExtendedReportStatistics extendedReportStatistics) {
    this.extendedReportStatistics = extendedReportStatistics;
  }

  public void addDisposedReferencedObjectWithPathToRoot(@NotNull final Stack<RootPathElement> rootPath,
                                                        @NotNull final ExceededClusterStatistics exceededClusterStatistics) {
    addObjectWithPathToRoot(rootPath, exceededClusterStatistics, DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE);
  }

  public void addClassLoaderPath(@NotNull final Stack<RootPathElement> rootPath,
                                 @NotNull final ExceededClusterStatistics exceededClusterStatistics) {
    addObjectWithPathToRoot(rootPath, exceededClusterStatistics, OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE);
    totalNominatedLoadersReferringObjectsStatistics.addObject(rootPath.lastElement().size);
  }

  public void addObjectWithPathToRoot(@NotNull final Stack<RootPathElement> rootPath,
                                      @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                      int nominatedNodeTypeId) {
    if (rootPath.size() > ROOT_PATH_TREE_MAX_OBJECT_DEPTH && !NOMINATED_NODE_TYPES_NO_PRINTING_OPTIMIZATION.contains(nominatedNodeTypeId)) {
      return;
    }

    ListIterator<RootPathElement> rootPathIterator = rootPath.listIterator(rootPath.size());
    int exceededClusterId = exceededClusterStatistics.exceededClusterIndex;

    RootPathTreeNode rootPathTreeNode = null;
    RootPathElement pathElement = null;

    // Iterate in reverse.
    while (rootPathIterator.hasPrevious()) {
      pathElement = rootPathIterator.previous();
      if (pathElement.getRootPathTreeNode(exceededClusterId, nominatedNodeTypeId) != null) {
        rootPathIterator.next();
        rootPathTreeNode = pathElement.getRootPathTreeNode(exceededClusterId, nominatedNodeTypeId);
        break;
      }
    }

    if (rootPathTreeNode == null) {
      pathElement = rootPathIterator.next();
      rootPathTreeNode = createRootPathTreeNode(pathElement, extendedReportStatistics);
      rootPathTreeNode.incrementNumberOfInstances(exceededClusterId, nominatedNodeTypeId, pathElement.subtreeSize);

      roots.add(rootPathTreeNode);
      pathElement.setRootPathTreeNode(rootPathTreeNode, exceededClusterId, nominatedNodeTypeId);
    }

    addPathElementIteration(rootPathTreeNode, pathElement, rootPathIterator, exceededClusterId, nominatedNodeTypeId);
  }

  private RootPathTreeNode createRootPathTreeNode(@NotNull final RootPathElement rootPathElement,
                                                  @NotNull final ExtendedReportStatistics extendedReportStatistics) {
    numberOfRootPathTreeNodes++;
    return new RootPathTreeNode(rootPathElement.getLabel(), rootPathElement.getClassName(), rootPathElement.isDisposedButReferenced(),
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
    assert currentRootPathElement.rootPathTreeNodes[exceededClusterId][nominatedNodeTypeId] == node;
    RootPathElement rootPathElement = iterator.next();

    if (node.label.equals(rootPathElement.getLabel()) && node.className.equals(rootPathElement.getClassName())) {
      node.isRepeated = true;
      rootPathElement.setRootPathTreeNode(node, exceededClusterId, nominatedNodeTypeId);
      addPathElementIteration(node, rootPathElement, iterator, exceededClusterId, nominatedNodeTypeId);
      return;
    }

    RootPathTreeNode childNode = node.children.get(rootPathElement.extendedStackNode);
    if (childNode == null) {
      childNode = createRootPathTreeNode(rootPathElement, extendedReportStatistics);
      node.children.put(rootPathElement.extendedStackNode, childNode);
    }

    childNode.incrementNumberOfInstances(exceededClusterId, nominatedNodeTypeId, rootPathElement.subtreeSize);

    rootPathElement.setRootPathTreeNode(childNode, exceededClusterId, nominatedNodeTypeId);
    addPathElementIteration(childNode, rootPathElement, iterator, exceededClusterId, nominatedNodeTypeId);
  }

  public void printPathTreeForComponentDisposedReferencedObjects(@NotNull final Consumer<String> writer,
                                                                 @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                                                 @NotNull final ObjectsStatistics totalNominatedTypeStatistics) {
    if (totalNominatedTypeStatistics.getObjectsCount() == 0) {
      return;
    }
    writer.accept("================= DISPOSED OBJECTS ================");
    printPathTreeForComponentAndNominatedType(writer, exceededClusterStatistics, DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE,
                                              totalNominatedTypeStatistics);
  }

  public void printPathTreeForComponentObjectsReferringNominatedLoaders(@NotNull final Consumer<String> writer,
                                                                        @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                                                        @NotNull final ComponentsSet.Component component) {
    if (component.customClassLoaders == null || totalNominatedLoadersReferringObjectsStatistics.getObjectsCount() == 0) {
      return;
    }
    writer.accept("================= OBJECTS RETAINING NOMINATED LOADERS ================");
    writer.accept("Nominated ClassLoaders:");
    for (String loader : component.customClassLoaders) {
      writer.accept(String.format(Locale.US, " --> %s", loader));
    }
    printPathTreeForComponentAndNominatedType(writer, exceededClusterStatistics, OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE,
                                              totalNominatedLoadersReferringObjectsStatistics);
  }

  public void printPathTreeForComponentAndNominatedType(@NotNull final Consumer<String> writer,
                                                        @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                                        int nominatedNodeTypeId,
                                                        @NotNull final ObjectsStatistics totalNominatedTypeStatistics) {
    Map<RootPathTreeNode, ObjectsStatistics> nominatedObjectsStatsInTheNodeSubtree = new HashMap<>();
    for (RootPathTreeNode root : roots) {
      calculateNominatedObjectsStatisticsInTheSubtree(root, exceededClusterStatistics.exceededClusterIndex, nominatedNodeTypeId,
                                                      nominatedObjectsStatsInTheNodeSubtree);
    }
    AtomicInteger rootIndex = new AtomicInteger(1);
    roots.stream().filter(r -> {
      ObjectsStatistics rootSubtreeStatistics = nominatedObjectsStatsInTheNodeSubtree.get(r);
      return (rootSubtreeStatistics != null) &&
             !shouldSkipPrintingNodeSubtree(totalNominatedTypeStatistics, rootSubtreeStatistics, nominatedNodeTypeId);
    }).sorted(Comparator.comparingLong(r -> nominatedObjectsStatsInTheNodeSubtree.get(r).getTotalSizeInBytes()).reversed()).forEach(r -> {
      writer.accept(String.format(Locale.US, "Root %d:", rootIndex.getAndIncrement()));
      printRootPathIteration(writer, r, " ", true, false, totalNominatedTypeStatistics, exceededClusterStatistics.exceededClusterIndex,
                             nominatedNodeTypeId, nominatedObjectsStatsInTheNodeSubtree);
    });
  }

  private static boolean shouldSkipPrintingNodeSubtree(@NotNull ObjectsStatistics totalNominatedTypeStatistics,
                                                       ObjectsStatistics rootSubtreeStatistics,
                                                       int nominatedNodeTypeId) {
    return (100 * rootSubtreeStatistics.getTotalSizeInBytes() <
            totalNominatedTypeStatistics.getTotalSizeInBytes() * NODE_SUBTREE_SIZE_PERCENTAGE_REQUIREMENT) &&
           (rootSubtreeStatistics.getTotalSizeInBytes() < NODE_SUBTREE_OBJECTS_SIZE_REQUIREMENT_BYTES) &&
           !NOMINATED_NODE_TYPES_NO_PRINTING_OPTIMIZATION.contains(nominatedNodeTypeId);
  }

  private ObjectsStatistics calculateNominatedObjectsStatisticsInTheSubtree(@NotNull final RootPathTreeNode node,
                                                                            int exceededClusterId,
                                                                            int nominatedNodeTypeId,
                                                                            @NotNull final Map<RootPathTreeNode, ObjectsStatistics> nominatedObjectsStatsInTheNodeSubtree) {
    ObjectsStatistics statistics = new ObjectsStatistics();
    if (node.instancesStatistics[exceededClusterId][nominatedNodeTypeId] == null ||
        node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount() == 0) {
      return statistics;
    }
    if (node.selfSizes[exceededClusterId][nominatedNodeTypeId] != null) {
      statistics.addStats(node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount(),
                          node.selfSizes[exceededClusterId][nominatedNodeTypeId]);
    }

    for (RootPathTreeNode childNode : node.children.values()) {
      if (childNode.instancesStatistics[exceededClusterId][nominatedNodeTypeId] == null ||
          childNode.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount() == 0) {
        continue;
      }
      statistics.addStats(calculateNominatedObjectsStatisticsInTheSubtree(childNode, exceededClusterId, nominatedNodeTypeId,
                                                                          nominatedObjectsStatsInTheNodeSubtree));
    }
    if (statistics.getObjectsCount() > 0) {
      nominatedObjectsStatsInTheNodeSubtree.put(node, statistics);
    }
    return statistics;
  }

  private String transformPrefix(@NotNull final String prefix,
                                 boolean isOnlyChild,
                                 boolean isLastChild) {
    if (isOnlyChild) {
      return prefix + ' ';
    }
    if (isLastChild) {
      return prefix.substring(0, prefix.length() - 1) + "\\-";
    }
    return prefix.substring(0, prefix.length() - 1) + "+-";
  }

  private void printRootPathIteration(@NotNull final Consumer<String> writer,
                                      @NotNull final RootPathTreeNode node,
                                      @NotNull final String prefix,
                                      boolean isOnlyChild,
                                      boolean isLastChild,
                                      @NotNull final ObjectsStatistics totalNominatedTypeStatistics,
                                      int exceededClusterId,
                                      int nominatedNodeTypeId,
                                      @NotNull final Map<RootPathTreeNode, ObjectsStatistics> nominatedObjectsStatsInTheNodeSubtree) {
    ObjectsStatistics nominatedObjectsInTheSubtree = nominatedObjectsStatsInTheNodeSubtree.get(node);

    if (shouldSkipPrintingNodeSubtree(totalNominatedTypeStatistics, nominatedObjectsInTheSubtree, nominatedNodeTypeId)) {
      return;
    }

    writer.accept(
      constructRootPathLine(node, prefix, isOnlyChild, isLastChild, totalNominatedTypeStatistics, exceededClusterId, nominatedNodeTypeId,
                            nominatedObjectsInTheSubtree));

    List<RootPathTreeNode> children = node.children.values().stream().filter(
        c -> (c.instancesStatistics[exceededClusterId][nominatedNodeTypeId] != null &&
              c.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount() > 0) &&
             nominatedObjectsStatsInTheNodeSubtree.containsKey(c))
      .sorted(Comparator.comparingLong(c -> nominatedObjectsStatsInTheNodeSubtree.get(c).getTotalSizeInBytes()).reversed()).toList();

    if (children.size() > 1) {
      int i = 0;

      for (RootPathTreeNode childNode : children) {
        String childPrefix = prefix;
        if (i == children.size() - 1) {
          childPrefix += "  ";
        }
        else {
          childPrefix += " |";
        }
        printRootPathIteration(writer, childNode, childPrefix, false,
                               i == children.size() - 1, totalNominatedTypeStatistics, exceededClusterId, nominatedNodeTypeId,
                               nominatedObjectsStatsInTheNodeSubtree);
        i++;
      }
      return;
    }
    for (RootPathTreeNode childNode : children) {
      printRootPathIteration(writer, childNode, prefix, true, false, totalNominatedTypeStatistics, exceededClusterId, nominatedNodeTypeId,
                             nominatedObjectsStatsInTheNodeSubtree);
    }
  }

  @NotNull
  private String constructRootPathLine(@NotNull final RootPathTreeNode node,
                                       @NotNull final String prefix,
                                       boolean isOnlyChild,
                                       boolean isLastChild,
                                       @NotNull final ObjectsStatistics totalNominatedTypeStatistics,
                                       int exceededClusterId,
                                       int nominatedNodeTypeId,
                                       @NotNull final ObjectsStatistics nominatedObjectsInTheSubtree) {
    String percentString =
      padStart((int)(100.0 * nominatedObjectsInTheSubtree.getTotalSizeInBytes() / totalNominatedTypeStatistics.getTotalSizeInBytes()) + "%",
               4,
               ' ');

    return '[' +
           padStart(HeapReportUtils.INSTANCE.toShortStringAsCount(nominatedObjectsInTheSubtree.getObjectsCount()), 5, ' ') +
           '/' +
           percentString +
           '/' +
           padStart(HeapReportUtils.INSTANCE.toShortStringAsSize(nominatedObjectsInTheSubtree.getTotalSizeInBytes()), 6, ' ') +
           ']' +
           padStart(HeapTraverseUtil.getObjectsStatsPresentation(node.instancesStatistics[exceededClusterId][nominatedNodeTypeId],
                                                                 MemoryReportCollector.HeapSnapshotPresentationConfig.PresentationStyle.OPTIMAL_UNITS),
                    20, ' ') +
           ' ' + (node.selfSizes[exceededClusterId][nominatedNodeTypeId] != null ? '*' : ' ') +
           ' ' + (node.isRepeated ? "(rep)" : "     ") +
           transformPrefix(prefix, isOnlyChild, isLastChild) +
           node.label +
           ": " +
           node.className +
           (node.isDisposedButReferenced ? "(disposed)" : "");
  }

  public int getNumberOfRootPathTreeNodes() {
    return numberOfRootPathTreeNodes;
  }

  public static class RootPathElement {
    @NotNull
    private final ExtendedStackNode extendedStackNode;
    private final long size;
    private long subtreeSize = 0;

    @NotNull
    private final RootPathTreeNode[][] rootPathTreeNodes;

    public RootPathElement(@NotNull ExtendedStackNode node, long size, @NotNull final ExtendedReportStatistics extendedReportStatistics) {
      extendedStackNode = node;
      rootPathTreeNodes =
        new RootPathTreeNode[extendedReportStatistics.componentToExceededClustersStatistics.size()][MAX_NUMBER_OF_NOMINATED_NODE_TYPES];
      this.size = size;
      this.subtreeSize = size;
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

    public void setRootPathTreeNode(@Nullable RootPathTreeNode rootPathTreeNode,
                                    int exceededClusterId,
                                    int nominatedNodeTypeId) {
      rootPathTreeNodes[exceededClusterId][nominatedNodeTypeId] = rootPathTreeNode;
    }

    @Nullable
    public RootPathTreeNode getRootPathTreeNode(int exceededClusterId, int nominatedNodeTypeId) {
      return rootPathTreeNodes[exceededClusterId][nominatedNodeTypeId];
    }

    public long getSize() {
      return size;
    }

    public long getSubtreeSize() {
      return subtreeSize;
    }

    public void addSubtreeSize(long size) {
      subtreeSize += size;

      // When we add subtree size to the node it may already participate in some object paths - we need to reflect the subtree size
      // change there as well to avoid out of sync.
      for (int i = 0; i < rootPathTreeNodes.length; i++) {
        RootPathTreeNode[] nodes = rootPathTreeNodes[i];
        for (int j = 0; j < nodes.length; j++) {
          if (nodes[j] == null) {
            continue;
          }
          assert nodes[j].instancesStatistics[i][j] != null;
          assert nodes[j].instancesStatistics[i][j].getObjectsCount() > 0;
          nodes[j].instancesStatistics[i][j].addStats(0, size);
        }
      }
    }
  }

  private static class RootPathTreeNode {
    @NotNull final String className;
    @NotNull final String label;
    boolean isDisposedButReferenced;

    @NotNull final Map<ExtendedStackNode, RootPathTreeNode> children = Maps.newHashMap();
    boolean isRepeated = false;

    // [i][j]: Contains number of objects/total subtree size in bytes that participate in a tree for exceeded cluster #i,
    // nomination type #j.
    final ObjectsStatistics[][] instancesStatistics;
    // [i][j]: Null if node is not nominated for exceeded cluster #i, nomination type #j. Otherwise total self size of objects.
    final Long[][] selfSizes;

    private RootPathTreeNode(@NotNull final String label,
                             @NotNull final String className,
                             boolean isDisposedButReferenced,
                             @NotNull final ExtendedReportStatistics extendedReportStatistics) {
      this.className = className;
      this.label = label;
      this.isDisposedButReferenced = isDisposedButReferenced;
      this.instancesStatistics =
        new ObjectsStatistics[extendedReportStatistics.componentToExceededClustersStatistics.size()][MAX_NUMBER_OF_NOMINATED_NODE_TYPES];
      this.selfSizes =
        new Long[extendedReportStatistics.componentToExceededClustersStatistics.size()][MAX_NUMBER_OF_NOMINATED_NODE_TYPES];
    }

    private void incrementNumberOfInstances(int exceededClusterId, int nominatedNodeTypeId, long subtreeSize) {
      if (instancesStatistics[exceededClusterId][nominatedNodeTypeId] == null) {
        instancesStatistics[exceededClusterId][nominatedNodeTypeId] = new ObjectsStatistics();
      }
      instancesStatistics[exceededClusterId][nominatedNodeTypeId].addObject(subtreeSize);
    }

    private void markNodeAsNominated(int exceededClusterId, int nominatedNodeTypeId, long size) {
      if (selfSizes[exceededClusterId][nominatedNodeTypeId] == null) {
        selfSizes[exceededClusterId][nominatedNodeTypeId] = 0L;
      }
      selfSizes[exceededClusterId][nominatedNodeTypeId] += size;
    }
  }
}
