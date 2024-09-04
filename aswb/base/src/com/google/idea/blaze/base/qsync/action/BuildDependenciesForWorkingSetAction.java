/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;

/**
 * Action to build dependencies and enable analysis for the working set and it's reverse
 * dependencies
 */
public class BuildDependenciesForWorkingSetAction extends BlazeProjectAction {
  private static final Logger logger =
      Logger.getInstance(BuildDependenciesForWorkingSetAction.class);

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  private BuildDependenciesHelper createHelper(Project project) {
    return new BuildDependenciesHelper(project, DepsBuildType.REVERSE_DEPS);
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BuildDependenciesHelper helper = createHelper(project);
    if (!helper.canEnableAnalysisNow()) {
      return;
    }
    ImmutableSet<Path> workingSet;

    try {
      workingSet = helper.getWorkingSet();
    } catch (BuildException be) {
      logger.error("Error obtaining working set", be);
      notifyFailureWorkingSet(project, be);
      return;
    }

    ImmutableSet<Label> affectedTargets = helper.getAffectedTargetsForPaths(workingSet);
    if (affectedTargets.isEmpty()) {
      notifyEmptyWorkingSet(project);
      return;
    }

    QuerySyncActionStatsScope querySyncActionStats =
        QuerySyncActionStatsScope.createForPaths(getClass(), e, workingSet);
    helper.enableAnalysis(affectedTargets, querySyncActionStats);
  }

  private void notifyFailureWorkingSet(Project project, Throwable e) {
    QuerySyncManager.getInstance(project)
        .notifyError(
            "Could not obtain working set",
            String.format("Encountered error when trying to get working set: %s", e.getMessage()));
  }

  private void notifyEmptyWorkingSet(Project project) {
    QuerySyncManager.getInstance(project)
        .notifyWarning(
            "Nothing to build",
            "If you have edited project files recently, please re-sync and try again.");
  }
}
