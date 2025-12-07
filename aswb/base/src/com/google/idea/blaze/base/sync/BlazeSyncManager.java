/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.function.Predicate;
import org.jetbrains.annotations.VisibleForTesting;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  private final Project project;
  private static final Logger logger = Logger.getInstance(BlazeSyncManager.class);

  public BlazeSyncManager(Project project) {
    this.project = project;
  }

  public static BlazeSyncManager getInstance(Project project) {
    return project.getService(BlazeSyncManager.class);
  }

  public static void printAndLogError(String errorMessage, Context<?> context) {
    context.output(PrintOutput.error(errorMessage));
    logger.error(errorMessage);
  }

  @VisibleForTesting
  boolean shouldForceFullSync(
      BlazeProjectData oldProjectData,
      SyncProjectState projectState,
      SyncMode syncMode,
      BlazeContext context) {
    if (oldProjectData == null || projectState == null || syncMode == SyncMode.NO_BUILD) {
      return false;
    }
    SetView<LanguageClass> newLanguages =
        Sets.difference(
            projectState.getLanguageSettings().getActiveLanguages(),
            oldProjectData.getWorkspaceLanguageSettings().getActiveLanguages());
    // Don't care if languages are removed from project view because the corresponding targets will
    // be removed from the targetMap anyway at the end of the sync
    if (newLanguages.isEmpty()) {
      return false;
    }
    // Force a full sync if a new language is added to project view
    String message =
        String.format(
            "%s %s added to project view; forcing a full sync",
            StringUtil.pluralize("Language", newLanguages.size()),
            newLanguages.stream().map(LanguageClass::getName).collect(joining(",")));
    context.output(SummaryOutput.output(Prefix.INFO, message).log());
    logger.info(message);
    return true;
  }

  private Task getRootInvocationTask(BlazeSyncParams params) {
    String taskTitle;
    if (params.syncMode() == SyncMode.STARTUP) {
      taskTitle = "Startup Sync";
    } else if (params.syncOrigin().equals(BlazeSyncStartupActivity.SYNC_REASON)) {
      taskTitle = "Importing " + project.getName();
    } else if (params.syncMode() == SyncMode.PARTIAL) {
      taskTitle = "Partial Sync";
    } else if (params.syncMode() == SyncMode.FULL) {
      taskTitle = "Non-Incremental Sync";
    } else {
      taskTitle = "Incremental Sync";
    }
    return new Task(project, taskTitle, Task.Type.SYNC);
  }

  /**
   * Filters the project targets as part of a coherent sync process, updating derived project data
   * and sending notifications accordingly.
   *
   * @param reason a description of what triggered this action
   */
  public void filterProjectTargets(Predicate<TargetKey> filter, String reason) {
    throw new UnsupportedOperationException();
  }
}
