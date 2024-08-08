/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.TargetExpressionList;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** Tracks and manages project targets for the purposes of automatic syncing. */
class ProjectTargetManagerImpl implements ProjectTargetManager {

  static ProjectTargetManagerImpl getImpl(Project project) {
    return project.getService(ProjectTargetManagerImpl.class);
  }

  private final Project project;
  private final ConcurrentHashMap<Integer, InProgressSync> inProgressBuilds =
      new ConcurrentHashMap<>();

  private volatile SyncStatus projectSyncStatus = SyncStatus.UNSYNCED;

  private ProjectTargetManagerImpl(Project project) {
    this.project = project;
  }

  @Override
  public SyncStatus getProjectSyncStatus() {
    return projectSyncStatus;
  }

  @Override
  public SyncStatus getSyncStatus(Label target) {
    // TODO(brendandouglas): implement logic to determine if a synced target is 'stale'
    // (time since last sync, any events affecting sync results, etc.)
    if (syncInProgress(target)) {
      return inTargetMap(target) ? SyncStatus.RESYNCING : SyncStatus.IN_PROGRESS;
    }
    return inTargetMap(target) ? SyncStatus.SYNCED : SyncStatus.UNSYNCED;
  }

  @Override
  @Nullable
  public SyncStatus getSyncStatus(File source) {
    // TODO(brendandouglas): implement 'stale' sync state
    ImmutableCollection<TargetKey> syncedTargets =
        SourceToTargetMap.getInstance(project).getRulesForSourceFile(source);
    if (!syncedTargets.isEmpty()) {
      return syncedTargets.stream().anyMatch(t -> syncInProgress(t.getLabel()))
          ? SyncStatus.RESYNCING
          : SyncStatus.SYNCED;
    }
    Label label = WorkspaceHelper.getBuildLabel(project, source);
    if (label == null) {
      // can't find a parent BUILD package
      return null;
    }

    // we don't know which target covers this source without a blaze query. Instead, just check if
    // any target in the parent package is currently being synced
    if (inProgressBuilds.values().stream()
        .map(s -> s.targets)
        .anyMatch(list -> list.includesAnyTargetInPackage(label.blazePackage()))) {
      return SyncStatus.IN_PROGRESS;
    }
    return SyncStatus.UNSYNCED;
  }

  private boolean inTargetMap(Label target) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null
        && projectData.getTargetMap().contains(TargetKey.forPlainTarget(target));
  }

  @Override
  public boolean syncInProgress(TargetExpression expr) {
    return inProgressBuilds.values().stream()
        .map(s -> s.targets)
        .anyMatch(list -> includesTarget(list, expr));
  }

  private static boolean includesTarget(TargetExpressionList list, TargetExpression expr) {
    if (expr instanceof Label) {
      return list.includesTarget((Label) expr);
    }
    WildcardTargetPattern pattern = WildcardTargetPattern.fromExpression(expr);
    if (pattern == null || pattern.isRecursive()) {
      // TODO(brendandouglas): support recursive target patterns
      return false;
    }
    return list.includesPackage(pattern.getBasePackage());
  }

  private void updateProjectSyncStatus() {
    boolean inProgress = inProgressBuilds.values().stream().anyMatch(s -> s.fullProjectSync);
    if (inProgress) {
      projectSyncStatus = SyncStatus.RESYNCING;
      return;
    }
    boolean synced = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() != null;
    projectSyncStatus = synced ? SyncStatus.SYNCED : SyncStatus.UNSYNCED;
  }

  static class TargetSyncListener implements SyncListener {
    @Override
    public void buildStarted(
        Project project,
        BlazeContext context,
        boolean fullProjectSync,
        int buildId,
        ImmutableList<TargetExpression> targets) {
      ProjectTargetManagerImpl manager = getImpl(project);
      manager.inProgressBuilds.put(
          buildId, new InProgressSync(fullProjectSync, TargetExpressionList.create(targets)));
      if (fullProjectSync) {
        manager.projectSyncStatus = SyncStatus.RESYNCING;
      }
      // refresh the sync status indicators
      ProjectView.getInstance(project).refresh();
    }

    @Override
    public void afterSync(
        Project project,
        BlazeContext context,
        SyncMode syncMode,
        SyncResult syncResult,
        ImmutableSet<Integer> buildIds) {
      ProjectTargetManagerImpl manager = getImpl(project);
      buildIds.forEach(manager.inProgressBuilds::remove);
      manager.updateProjectSyncStatus();
      if (!syncResult.successful()) {
        // project view won't otherwise update for failed/cancelled syncs
        ProjectView.getInstance(project).refresh();
      }
    }
  }

  private static class InProgressSync {
    final boolean fullProjectSync;
    final TargetExpressionList targets;

    InProgressSync(boolean fullProjectSync, TargetExpressionList targets) {
      this.fullProjectSync = fullProjectSync;
      this.targets = targets;
    }
  }
}
