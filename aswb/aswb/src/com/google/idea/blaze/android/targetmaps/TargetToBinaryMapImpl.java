/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.targetmaps;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Implementation of {@link TargetToBinaryMap} */
public class TargetToBinaryMapImpl implements TargetToBinaryMap {

  private final Project project;
  private final Map<TargetKey, ImmutableSet<TargetKey>> targetToBinaries;
  @Nullable private ImmutableSet<TargetKey> sourceBinaries;

  public TargetToBinaryMapImpl(Project project) {
    this.project = project;
    this.targetToBinaries = new HashMap<>();
    this.sourceBinaries = null;
  }

  @Override
  public synchronized ImmutableSet<TargetKey> getBinariesDependingOn(
      Collection<TargetKey> targetKeys) {
    ImmutableSet<TargetKey> uncachedKeys =
        targetKeys.stream()
            .filter(t -> !targetToBinaries.containsKey(t))
            .collect(ImmutableSet.toImmutableSet());
    if (!uncachedKeys.isEmpty()) {
      cacheBinariesForTargetKeys(uncachedKeys);
    }
    return targetKeys.stream()
        .flatMap(k -> targetToBinaries.getOrDefault(k, ImmutableSet.of()).stream())
        .collect(ImmutableSet.toImmutableSet());
  }

  /**
   * Calculates the android binary targets that depend on each target in {@code targetKeys}, and
   * caches the calculation in {@link #targetToBinaries}. If there is already an entry in {@link
   * #targetToBinaries} for a target, it will be overwritten with the new calculation.
   *
   * <p>The calculation is performed by traversing the build tree of each binary once to look for
   * every target in {@code targetKeys}
   */
  private void cacheBinariesForTargetKeys(ImmutableSet<TargetKey> targetKeys) {
    // builder map to hold intermediate values before putting the entries in targetToBinaries
    Map<TargetKey, ImmutableSet.Builder<TargetKey>> builderMap =
        targetKeys.stream().collect(Collectors.toMap(t -> t, t -> ImmutableSet.builder()));

    Set<TargetKey> sourceBinaries = getSourceBinaryTargets();

    // Ask TransitiveDependencyMap if the binary is dependant on any of the targetKeys
    // This operation can be expensive.
    TransitiveDependencyMap transitiveDepsMap = TransitiveDependencyMap.getInstance(project);
    for (TargetKey sourceBinary : sourceBinaries) {
      // Add to map if a target is itself a binary
      if (targetKeys.contains(sourceBinary)) {
        builderMap.get(sourceBinary).add(sourceBinary);
      }
      transitiveDepsMap
          .filterPossibleTransitiveDeps(sourceBinary, targetKeys)
          .forEach(k -> builderMap.get(k).add(sourceBinary));
    }

    builderMap.forEach((target, builder) -> targetToBinaries.put(target, builder.build()));
  }

  @Override
  public synchronized ImmutableSet<TargetKey> getSourceBinaryTargets() {
    if (sourceBinaries != null) {
      return sourceBinaries;
    }

    // Get all android source targets and filter out non-binary targets
    // Note: BlazeImportUtil.getSourceTargetsStream already filters non-android targets
    Stream<TargetIdeInfo> sourceTargetsStream = BlazeImportUtil.getSourceTargetsStream(project);

    sourceBinaries =
        sourceTargetsStream
            .filter(t -> RuleType.BINARY.equals(t.getKind().getRuleType()))
            .map(TargetIdeInfo::getKey)
            .collect(ImmutableSet.toImmutableSet());
    return sourceBinaries;
  }

  /** Clears cache to trigger recalculation */
  private synchronized void invalidateCache() {
    sourceBinaries = null;
    targetToBinaries.clear();
  }

  /** Adapter to listen for project syncs and clear caches where required */
  public static class Adapter implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      TargetToBinaryMap instance = TargetToBinaryMap.getInstance(project);
      if (instance instanceof TargetToBinaryMapImpl) {
        ((TargetToBinaryMapImpl) instance).invalidateCache();
      }
    }
  }
}
