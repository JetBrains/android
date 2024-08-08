/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.ReverseDependencyMap;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.annotation.Nullable;

/** Auto-sync support on changes to proto files in the project. */
class ProtoAutoSyncProvider implements AutoSyncProvider {

  @Override
  public boolean isSyncSensitiveFile(Project project, VirtualFile file) {
    return isProtoFile(file) && !getTargetsForSourceFile(project, file).isEmpty();
  }

  private static boolean isProtoFile(VirtualFile file) {
    return "proto".equals(file.getExtension());
  }

  private static ImmutableCollection<TargetKey> getTargetsForSourceFile(
      Project project, VirtualFile file) {
    return SourceToTargetMap.getInstance(project).getRulesForSourceFile(new File(file.getPath()));
  }

  @Nullable
  @Override
  public BlazeSyncParams getAutoSyncParamsForFile(Project project, VirtualFile modifiedFile) {
    if (!AutoSyncSettings.getInstance().autoSyncOnProtoChanges
        || !isSyncSensitiveFile(project, modifiedFile)) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    // TODO(brendandouglas): extract a source to genfile-producing-targets provider, and calculate
    // this as part of sync / make it a SyncCache
    Set<TargetExpression> plainTargets = new HashSet<>();
    getTargetsForSourceFile(project, modifiedFile)
        .forEach(t -> plainTargets.addAll(getPlainTargets(project, t)));

    return plainTargets.isEmpty()
        ? null
        : BlazeSyncParams.builder()
            .setTitle(AUTO_SYNC_TITLE)
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(AUTO_SYNC_REASON + ".ProtoAutoSyncProvider")
            .addTargetExpressions(plainTargets)
            .setBackgroundSync(true)
            .build();
  }

  /**
   * Finds all 'plain' targets in a target+aspect's rdeps, stopping at the first plain target it
   * finds along each path.
   */
  private static List<Label> getPlainTargets(Project project, TargetKey target) {
    if (target.isPlainTarget()) {
      return ImmutableList.of(target.getLabel());
    }
    ImmutableList.Builder<Label> output = new ImmutableList.Builder<>();
    Queue<TargetKey> todo = Queues.newArrayDeque();
    ImmutableMultimap<TargetKey, TargetKey> reverseDependencyMap =
        ReverseDependencyMap.get(project);
    todo.addAll(reverseDependencyMap.get(target));
    Set<TargetKey> seen = Sets.newHashSet();
    while (!todo.isEmpty()) {
      TargetKey targetKey = todo.remove();
      if (!seen.add(targetKey)) {
        continue;
      }
      if (targetKey.isPlainTarget()) {
        output.add(targetKey.getLabel());
      } else {
        todo.addAll(reverseDependencyMap.get(targetKey));
      }
    }
    return output.build();
  }
}
