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

import static com.android.tools.idea.diagnostics.heap.RootPathTree.DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE;
import static com.android.tools.idea.diagnostics.heap.RootPathTree.HEAP_SUMMARY_NODE_TYPE;
import static com.android.tools.idea.diagnostics.heap.RootPathTree.ESSENTIAL_NOMINATED_NODE_TYPES;
import static com.android.tools.idea.diagnostics.heap.RootPathTree.OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE;
import static com.google.common.base.Strings.padStart;

import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.Pair;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public abstract class RootPathTreePrinter {

  private static final int NODE_SUBTREE_SIZE_PERCENTAGE_REQUIREMENT = 2;
  private static final int NODE_SUBTREE_OBJECTS_SIZE_REQUIREMENT_BYTES = 750_000; //750kb

  protected final int nominatedNodeTypeId;
  protected final int exceededClusterId;

  @NotNull
  protected final ExtendedReportStatistics extendedReportStatistics;

  protected RootPathTreePrinter(@NotNull final ExtendedReportStatistics extendedReportStatistics,
                                int nominatedNodeTypeId,
                                int exceededClusterId) {
    this.extendedReportStatistics = extendedReportStatistics;
    this.nominatedNodeTypeId = nominatedNodeTypeId;
    this.exceededClusterId = exceededClusterId;
  }

  abstract boolean shouldPrintNodeSubtree(@NotNull final RootPathTreeNode node, int depth, short visitedEssentialNominatedNodeTypesMask);

  abstract void print(@NotNull final Consumer<String> writer);

  @NotNull
  abstract Comparator<RootPathTreeNode> getChildrenOrderingComparator();

  protected void printPathTreeForComponentAndNominatedType(@NotNull final Consumer<String> writer) {
    AtomicInteger rootIndex = new AtomicInteger(1);
    extendedReportStatistics.rootPathTree.roots.values().stream().filter(r -> shouldPrintNodeSubtree(r, 0, (short)0))
      .sorted(getChildrenOrderingComparator()).forEach(r -> {
        writer.accept(String.format(Locale.US, "Root %d:", rootIndex.getAndIncrement()));
        printRootPathIteration(writer, r, " ", true, false, 0, (short)0);
      });
  }

  protected void printRootPathIteration(@NotNull final Consumer<String> writer,
                                        @NotNull final RootPathTreeNode node,
                                        @NotNull final String prefix,
                                        boolean isOnlyChild,
                                        boolean isLastChild,
                                        int depth,
                                        short visitedEssentialNominatedNodeTypesMask) {
    writer.accept(constructRootPathLine(node, prefix, isOnlyChild, isLastChild, visitedEssentialNominatedNodeTypesMask));

    if (node.isDisposedButReferenced) {
      visitedEssentialNominatedNodeTypesMask |= 1 << DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE;
    }
    if (node.isLoadedWithNominatedLoader) {
      visitedEssentialNominatedNodeTypesMask |= 1 << OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE;
    }

    short finalVisitedEssentialNominatedNodeTypesMask = visitedEssentialNominatedNodeTypesMask;
    List<RootPathTreeNode> children = node.children.values().stream().filter(
        c -> (c.instancesStatistics[exceededClusterId][nominatedNodeTypeId] != null &&
              c.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount() > 0) &&
             shouldPrintNodeSubtree(c, depth + 1, finalVisitedEssentialNominatedNodeTypesMask))
      .sorted(getChildrenOrderingComparator()).toList();

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
        printRootPathIteration(writer, childNode, childPrefix, false, i == children.size() - 1, depth + 1,
                               visitedEssentialNominatedNodeTypesMask);
        i++;
      }
      return;
    }
    for (RootPathTreeNode childNode : children) {
      printRootPathIteration(writer, childNode, prefix, true, false, depth + 1, visitedEssentialNominatedNodeTypesMask);
    }
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

  @NotNull
  protected String constructRootPathLine(@NotNull final RootPathTreeNode node,
                                         @NotNull final String prefix,
                                         boolean isOnlyChild,
                                         boolean isLastChild,
                                         short visitedEssentialNominatedNodeTypesMask) {
    return padStart(
      HeapTraverseUtil.getObjectsSizePresentation(node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getTotalSizeInBytes(),
                                                  MemoryReportCollector.HeapSnapshotPresentationConfig.PresentationStyle.OPTIMAL_UNITS), 6,
      ' ') +
           padStart(
             HeapTraverseUtil.getObjectsCountPresentation(
               node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount(),
               MemoryReportCollector.HeapSnapshotPresentationConfig.PresentationStyle.OPTIMAL_UNITS), 11,
             ' ') +
           ' ' + (node.selfSizes[exceededClusterId][nominatedNodeTypeId] != null ? '*' : ' ') +
           ' ' + (node.isRepeated ? "(rep)" : "     ") +
           transformPrefix(prefix, isOnlyChild, isLastChild) +
           node.getPresentation(exceededClusterId, nominatedNodeTypeId, visitedEssentialNominatedNodeTypesMask);
  }

  static class RootPathTreeNominatedTypePrinter extends RootPathTreePrinter {
    @NotNull
    protected final ObjectsStatistics totalNominatedTypeStatistics;
    @NotNull
    protected final Map<RootPathTreeNode, ObjectsStatistics> nominatedObjectsStatsInTheNodeSubtree = new HashMap<>();
    @NotNull
    private final Comparator<RootPathTreeNode> childrenOrderingComparator;

    RootPathTreeNominatedTypePrinter(@NotNull final ObjectsStatistics totalNominatedTypeStatistics,
                                     @NotNull final ExtendedReportStatistics extendedReportStatistics,
                                     @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                     int nominatedNodeTypeId) {
      super(extendedReportStatistics, nominatedNodeTypeId, exceededClusterStatistics.exceededClusterIndex);
      this.totalNominatedTypeStatistics = totalNominatedTypeStatistics;
      for (RootPathTreeNode root : extendedReportStatistics.rootPathTree.roots.values()) {
        calculateNominatedObjectsStatisticsInTheSubtree(root, exceededClusterStatistics.exceededClusterIndex, nominatedNodeTypeId,
                                                        nominatedObjectsStatsInTheNodeSubtree);
      }

      childrenOrderingComparator =
        Comparator.comparingLong((RootPathTreeNode c) -> nominatedObjectsStatsInTheNodeSubtree.get(c).getTotalSizeInBytes()).reversed();
    }

    @NotNull
    @Override
    Comparator<RootPathTreeNode> getChildrenOrderingComparator() {
      return childrenOrderingComparator;
    }

    @Override
    void print(@NotNull final Consumer<String> writer) {
      printPathTreeForComponentAndNominatedType(writer);
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

    @Override
    boolean shouldPrintNodeSubtree(@NotNull RootPathTreeNode node, int depth, short visitedEssentialNominatedNodeTypesMask) {
      ObjectsStatistics rootSubtreeStatistics = nominatedObjectsStatsInTheNodeSubtree.get(node);

      return rootSubtreeStatistics != null &&
             ((100 * rootSubtreeStatistics.getTotalSizeInBytes() >=
               totalNominatedTypeStatistics.getTotalSizeInBytes() * NODE_SUBTREE_SIZE_PERCENTAGE_REQUIREMENT) ||
              (100 * rootSubtreeStatistics.getObjectsCount() >=
               totalNominatedTypeStatistics.getObjectsCount() * NODE_SUBTREE_SIZE_PERCENTAGE_REQUIREMENT) ||
              (rootSubtreeStatistics.getTotalSizeInBytes() >= NODE_SUBTREE_OBJECTS_SIZE_REQUIREMENT_BYTES) ||
              ESSENTIAL_NOMINATED_NODE_TYPES.contains(nominatedNodeTypeId));
    }

    @NotNull
    @Override
    protected String constructRootPathLine(@NotNull final RootPathTreeNode node,
                                           @NotNull final String prefix,
                                           boolean isOnlyChild,
                                           boolean isLastChild,
                                           short visitedEssentialNominatedNodeTypesMask) {
      ObjectsStatistics nominatedObjectsInTheSubtree = nominatedObjectsStatsInTheNodeSubtree.get(node);
      String percentString =
        padStart(
          (int)(100.0 * nominatedObjectsInTheSubtree.getTotalSizeInBytes() / totalNominatedTypeStatistics.getTotalSizeInBytes()) + "%",
          4,
          ' ');

      return '[' +
             padStart(HeapReportUtils.INSTANCE.toShortStringAsCount(nominatedObjectsInTheSubtree.getObjectsCount()), 5, ' ') +
             '/' +
             percentString +
             '/' +
             padStart(HeapReportUtils.INSTANCE.toShortStringAsSize(nominatedObjectsInTheSubtree.getTotalSizeInBytes()), 6, ' ') +
             ']' + super.constructRootPathLine(node, prefix, isOnlyChild, isLastChild, visitedEssentialNominatedNodeTypesMask);
    }
  }

  static class RootPathTreeDisposedObjectsPrinter extends RootPathTreeNominatedTypePrinter {

    @NotNull
    private final HeapSnapshotStatistics.DisposedObjectsInfo disposedObjectsInfo;

    RootPathTreeDisposedObjectsPrinter(@NotNull final ObjectsStatistics totalNominatedTypeStatistics,
                                       @NotNull final ExtendedReportStatistics extendedReportStatistics,
                                       @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                                       @NotNull final HeapSnapshotStatistics.DisposedObjectsInfo disposedObjectsInfo) {
      super(totalNominatedTypeStatistics, extendedReportStatistics, exceededClusterStatistics,
            DISPOSED_BUT_REFERENCED_NOMINATED_NODE_TYPE);
      this.disposedObjectsInfo = disposedObjectsInfo;
    }

    @Override
    public void print(@NotNull final Consumer<String> writer) {
      if (totalNominatedTypeStatistics.getObjectsCount() == 0) {
        return;
      }
      writer.accept("================= DISPOSED OBJECTS ================");
      printPathTreeForComponentAndNominatedType(writer);
    }

    @NotNull
    @Override
    protected String constructRootPathLine(@NotNull final RootPathTreeNode node,
                                           @NotNull final String prefix,
                                           boolean isOnlyChild,
                                           boolean isLastChild,
                                           short visitedEssentialNominatedNodeTypesMask) {
      if (node.isDisposedButReferenced) {
        ObjectsStatistics nominatedObjectsInTheSubtree = node.instancesStatistics[exceededClusterId][nominatedNodeTypeId];

        if (nominatedObjectsInTheSubtree.getTotalSizeInBytes() >= 1_000_000) {
          disposedObjectsInfo.numberOfDisposedObjectsWithAtLeast1mb++;
        }
        if (nominatedObjectsInTheSubtree.getTotalSizeInBytes() >= 10_000_000) {
          disposedObjectsInfo.numberOfDisposedObjectsWithAtLeast10mb++;
        }
        if (nominatedObjectsInTheSubtree.getTotalSizeInBytes() >= 100_000_000) {
          disposedObjectsInfo.numberOfDisposedObjectsWithAtLeast100mb++;
        }
      }

      return super.constructRootPathLine(node, prefix, isOnlyChild, isLastChild, visitedEssentialNominatedNodeTypesMask);
    }
  }

  static class RootPathTreeNominatedLoadersPrinter extends RootPathTreeNominatedTypePrinter {
    RootPathTreeNominatedLoadersPrinter(@NotNull final ObjectsStatistics totalNominatedTypeStatistics,
                                        @NotNull final ExtendedReportStatistics extendedReportStatistics,
                                        @NotNull final ExceededClusterStatistics exceededClusterStatistics) {
      super(totalNominatedTypeStatistics, extendedReportStatistics, exceededClusterStatistics,
            OBJECT_REFERRING_LOADER_NOMINATED_NODE_TYPE);
    }

    @Override
    public void print(@NotNull final Consumer<String> writer) {
      if (totalNominatedTypeStatistics.getObjectsCount() == 0) {
        return;
      }
      writer.accept("================= OBJECTS RETAINING NOMINATED LOADERS ================");
      writer.accept("Nominated ClassLoaders:");
      Map<String, Integer> classLoaderNameToCount = Maps.newHashMap();
      extendedReportStatistics.globalNominatedClassLoaders.forEach(cl -> {
        String loaderName = cl.getClass().getName();
        classLoaderNameToCount.putIfAbsent(loaderName, 0);
        classLoaderNameToCount.put(loaderName, classLoaderNameToCount.get(loaderName) + 1);
      });
      for (String classLoaderName : classLoaderNameToCount.keySet()) {
        writer.accept(" --> " + classLoaderName + ": " + classLoaderNameToCount.get(classLoaderName));
      }
      writer.accept("Duplicated classes:");
      for (Pair<String, Integer> classNameAndNumberOfInstances : extendedReportStatistics.duplicatedClassNamesAndNumberOfInstances) {
        writer.accept(" --> " + classNameAndNumberOfInstances.first + ": " + classNameAndNumberOfInstances.second);
      }
      printPathTreeForComponentAndNominatedType(writer);
    }
  }

  static class RootPathTreeSummaryPrinter extends RootPathTreePrinter {
    private final Comparator<RootPathTreeNode> childrenOrderingComparator;
    private static final long SUMMARY_SUBTREE_MAX_DEPTH = 50;
    private final long summaryRequiredSubtreeSize;

    RootPathTreeSummaryPrinter(@NotNull final ExtendedReportStatistics extendedReportStatistics,
                               @NotNull final ExceededClusterStatistics exceededClusterStatistics,
                               long summaryRequiredSubtreeSize) {
      super(extendedReportStatistics, HEAP_SUMMARY_NODE_TYPE, exceededClusterStatistics.exceededClusterIndex);
      this.summaryRequiredSubtreeSize = summaryRequiredSubtreeSize;

      childrenOrderingComparator =
        Comparator.comparingLong(
          (RootPathTreeNode c) -> c.instancesStatistics[exceededClusterId][HEAP_SUMMARY_NODE_TYPE].getTotalSizeInBytes()).reversed();
    }

    @Override
    boolean shouldPrintNodeSubtree(@NotNull RootPathTreeNode node, int depth, short visitedEssentialNominatedNodeTypesMask) {
      return node.instancesStatistics[exceededClusterId][nominatedNodeTypeId] != null &&
             node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getObjectsCount() > 0 &&
             node.instancesStatistics[exceededClusterId][nominatedNodeTypeId].getTotalSizeInBytes() >
             summaryRequiredSubtreeSize && depth < SUMMARY_SUBTREE_MAX_DEPTH;
    }

    @Override
    void print(@NotNull Consumer<String> writer) {
      printPathTreeForComponentAndNominatedType(writer);
    }

    @NotNull
    @Override
    Comparator<RootPathTreeNode> getChildrenOrderingComparator() {
      return childrenOrderingComparator;
    }
  }
}
