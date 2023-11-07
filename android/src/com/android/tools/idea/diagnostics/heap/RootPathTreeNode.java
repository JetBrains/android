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

import static com.android.tools.idea.diagnostics.heap.RootPathTree.MAX_NUMBER_OF_NOMINATED_NODE_TYPES;

import com.google.common.collect.Maps;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class RootPathTreeNode {
  @NotNull final String className;
  @NotNull final String label;
  boolean isDisposedButReferenced;

  @NotNull final Map<ExtendedStackNode, RootPathTreeNode> children = Maps.newHashMap();
  boolean isRepeated = false;

  // [i][j]: Contains number of objects/total subtree size in bytes that participate in a tree for exceeded cluster #i,
  // nomination type #j.
  final ObjectsStatistics[][] instancesStatistics;
  // [i][j]: Null if node is not nominated for exceeded cluster #i, nomination type #j. Otherwise, total self size of objects.
  final Long[][] selfSizes;

  RootPathTreeNode(@NotNull final String label,
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

  void incrementNumberOfInstances(int exceededClusterId, int nominatedNodeTypeId, long subtreeSize) {
    if (instancesStatistics[exceededClusterId][nominatedNodeTypeId] == null) {
      instancesStatistics[exceededClusterId][nominatedNodeTypeId] = new ObjectsStatistics();
    }
    instancesStatistics[exceededClusterId][nominatedNodeTypeId].addObject(subtreeSize);
  }

  void markNodeAsNominated(int exceededClusterId, int nominatedNodeTypeId, long size) {
    if (selfSizes[exceededClusterId][nominatedNodeTypeId] == null) {
      selfSizes[exceededClusterId][nominatedNodeTypeId] = 0L;
    }
    selfSizes[exceededClusterId][nominatedNodeTypeId] += size;
  }
}
